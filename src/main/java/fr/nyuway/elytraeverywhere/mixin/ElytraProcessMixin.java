package fr.nyuway.elytraeverywhere.mixin;

import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.process.PathingCommand;
import baritone.api.utils.BetterBlockPos;
import fr.nyuway.elytraeverywhere.debug.EELog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Removes the "Nether only" gate inside Baritone's {@code ElytraProcess#pathTo0}.
 *
 * <p>The original method begins with:
 * <pre>{@code
 * if (ctx.player() == null || ctx.player().level().dimension() != Level.NETHER) {
 *     return; // silently refuses to fly in any other dimension
 * }
 * }</pre>
 *
 * <p>This {@code pathTo0} is the single choke point for elytra: both the public
 * {@code pathTo} API and Baritone's own landing logic funnel through it, and if
 * it returns early no {@code ElytraBehavior} is ever created, so nothing flies.
 * We redirect only the {@code dimension()} call evaluated by that gate and
 * report {@code NETHER}, which makes the check pass everywhere. Because the
 * redirect is scoped to this one call site, every other place Baritone reads the
 * real dimension keeps working normally.
 *
 * <p><b>Why the cryptic method name.</b> The Meteor Baritone fork ships minified:
 * its private methods are renamed, so {@code pathTo0} is compiled to
 * {@code a(BlockPos, boolean)}. The obfuscated name is baked into the published
 * artifact identically for every consumer, so we match it by its descriptor. If
 * a future Baritone snapshot reshuffles the minified names, re-run
 * {@code javap -p -c} on {@code baritone/process/ElytraProcess.class} and update
 * the selector to whichever private {@code (BlockPos, boolean)void} method holds
 * the {@code != Level.NETHER} gate.
 */
@Mixin(targets = "baritone.process.ElytraProcess", remap = false)
public abstract class ElytraProcessMixin {

	@Redirect(
			method = "a(Lnet/minecraft/core/BlockPos;Z)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/Level;dimension()Lnet/minecraft/resources/ResourceKey;",
					remap = true
			),
			remap = true
	)
	private ResourceKey<Level> elytraeverywhere$reportNether(Level world) {
		return Level.NETHER;
	}

	/**
	 * Stops the auto-landing search from freezing the game outside the Nether.
	 *
	 * <p>When a path completes, {@code ElytraProcess} hunts for a landing spot with
	 * {@code isSafeBlock}, which only accepts {@code NETHERRACK}/{@code GRAVEL}.
	 * Those don't exist in the Overworld, so the inlined breadth-first search never
	 * finds a spot and scans every loaded air block between y0 and y128 - millions
	 * of them - hanging the client thread the instant you arrive.
	 *
	 * <p>Outside the Nether we treat any ordinary solid/standable block as a valid
	 * landing block (excluding the obvious hazards), so the search terminates at the
	 * ground right below the player and actually lands. The Nether keeps Baritone's
	 * original behaviour untouched.
	 *
	 * <p>Target: the minified {@code isSafeBlock(Block)} = {@code a(Block)Z}.
	 */
	@Inject(method = "a(Lnet/minecraft/world/level/block/Block;)Z", at = @At("HEAD"), cancellable = true, remap = true)
	private static void elytraeverywhere$allowGroundLanding(Block block, CallbackInfoReturnable<Boolean> cir) {
		final Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.level.dimension() == Level.NETHER) {
			return; // Nether: leave the original netherrack/gravel logic alone.
		}
		final boolean solidLand = block != Blocks.AIR
				&& block != Blocks.WATER // prefer dry land; water is only a last resort (oceans)
				&& block != Blocks.LAVA
				&& block != Blocks.FIRE
				&& block != Blocks.SOUL_FIRE
				&& block != Blocks.MAGMA_BLOCK
				&& block != Blocks.CACTUS;
		if (solidLand) {
			cir.setReturnValue(true); // prefer real, dry, standable land - keep looking until we find some
			return;
		}
		// Not dry land (air/water/hazard). Keep looking for real land within a small
		// radius; past that budget, fall back to the nearest *water surface*. Crucially
		// we never accept an AIR block as the fallback: that would be a mid-air "landing
		// spot" the elytra can only orbit, never reach (the bug that looped then dropped
		// the player to their death). Air keeps the downward column walk going until it
		// hits the water surface, which IS a valid touchdown.
		if (++landingScanBudget > 5_000) {
			if (landingScanBudget == 5_001) {
				EELog.log("[landing] no land within budget -> falling back to the nearest water surface");
			}
			if (block == Blocks.WATER || landingScanBudget > 100_000) {
				cir.setReturnValue(true); // water surface (or, past a hard cap over true void, anything - anti-freeze)
			}
		}
	}

	/** Blocks scanned by the landing search this tick; bounds it so it can't freeze. */
	@Unique
	private static int landingScanBudget;

	@Inject(method = "onTick(ZZ)Lbaritone/api/process/PathingCommand;", at = @At("HEAD"), remap = false)
	private void elytraeverywhere$resetLandingBudget(boolean calcFailed, boolean isSafeToCancel, CallbackInfoReturnable<PathingCommand> cir) {
		landingScanBudget = 0;
	}

	@Shadow
	public abstract void pathTo(Goal goal);

	/** Baritone's public {@code currentDestination()} (= behavior.destination, or null). */
	@Shadow(remap = true)
	public abstract BlockPos currentDestination();

	/**
	 * Clamps an out-of-range elytra goal Y into the octree's reachable {@code [1,127]}
	 * band instead of throwing "The y of the goal is not between 0 and 128".
	 *
	 * <p>The octree only spans y 0..127 (see {@link NetherPathfinderContextMixin}), so a
	 * goal above 128 (or at/below 0) can't be represented. Rather than refusing the
	 * command, we retarget it to the nearest reachable altitude and re-dispatch - the
	 * autopilot flies to the goal's X/Z at the capped height. The re-dispatched goal is
	 * already in range, so this recurses exactly once.
	 */
	@Inject(method = "pathTo(Lbaritone/api/pathing/goals/Goal;)V", at = @At("HEAD"), cancellable = true, remap = false)
	private void elytraeverywhere$clampGoalY(Goal goal, CallbackInfo ci) {
		if (goal instanceof GoalBlock block && (block.y <= 0 || block.y >= 128)) {
			int clampedY = Mth.clamp(block.y, 1, 127);
			EELog.log("[goal] elytra goal y {} out of octree range -> retargeting at y {}", block.y, clampedY);
			this.pathTo(new GoalBlock(block.x, clampedY, block.z));
			ci.cancel();
		}
	}

	/** Consecutive "bad landing spot" retries; capped to stop the crash-prone context churn. */
	@Unique
	private static int landingRetries;

	private static final int MAX_LANDING_RETRIES = 0;

	@Inject(method = "a(Lbaritone/api/utils/BetterBlockPos;)V", at = @At("HEAD"), remap = false)
	private void elytraeverywhere$countLandingRetry(BetterBlockPos endPos, CallbackInfo ci) {
		landingRetries++;
		EELog.log("[landing] 'bad landing spot' retry #{} (endPos {})", landingRetries, endPos);
	}

	/**
	 * Caps the landing-spot churn that crashes the game.
	 *
	 * <p>Each retry re-runs {@code pathTo0(spot, true)}, which builds a fresh
	 * {@code ElytraBehavior}/{@code NetherPathfinderContext} and destroys the old
	 * one asynchronously. Over water the landing never settles, so this churns
	 * rapidly while async raytraces are still pointing at the freed native context
	 * -> use-after-free -> {@code EXCEPTION_ACCESS_VIOLATION}. After a couple of
	 * retries we simply stop building new landing paths; the player keeps gliding
	 * down and the water-landing helper finishes the job, with no more churn.
	 *
	 * <p>A normal {@code pathTo} (appendDestination=false, e.g. a new {@code #elytra})
	 * resets the retry counter - that's a fresh flight.
	 */
	@Inject(method = "a(Lnet/minecraft/core/BlockPos;Z)V", at = @At("HEAD"), cancellable = true, remap = true)
	private void elytraeverywhere$capLandingChurn(BlockPos destination, boolean appendDestination, CallbackInfo ci) {
		if (!appendDestination) {
			landingRetries = 0; // fresh flight
			elytraeverywhere$landingHandled = false; // a new user flight -> allow the End takeover to run again
			return;
		}
		if (landingRetries > MAX_LANDING_RETRIES) {
			EELog.log("[landing] giving up auto-landing after {} retries - gliding in instead (avoids native context-churn crash)", landingRetries);
			ci.cancel(); // don't spin up yet another landing context
		}
	}

	// ------------------------------------------------------------------------------------------
	// End landing takeover
	// ------------------------------------------------------------------------------------------
	// Baritone's landing search (findSafeLandingSpot, inlined into onTick by the minifier) only
	// accepts NETHERRACK/GRAVEL as ground, so in the End it never finds a real spot. The old
	// isSafeBlock hack made it accept *any* block once a scan budget was exceeded - over the End
	// void that means it eventually accepts an AIR block, returns a "landing spot" floating in the
	// void, dives at it, sees there's nothing there ("bad landing spot, trying again") and loops.
	//
	// Here we own the decision in the End: when it's time to land, find the nearest *solid* ground
	// to the goal ourselves (heightmap scan, rejecting void edges) and route the autopilot onto it,
	// while setting goingToLandingSpot so Baritone's own finder is skipped entirely (no freeze, no
	// void dive). Overworld and Nether are untouched - their landing already works.

	/** Horizontal radius (blocks) searched around the goal for a solid landing surface. */
	@Unique
	private static final int ELYTRAEVERYWHERE_SEARCH_RADIUS = 112;

	/** Air blocks required above the ground so the glide-down has clearance (matches Baritone). */
	@Unique
	private static final int ELYTRAEVERYWHERE_LANDING_CLEARANCE = 15;

	/** Highest ground we'll target: ground + clearance must stay inside the octree's y &lt; 128. */
	@Unique
	private static final int ELYTRAEVERYWHERE_MAX_GROUND_Y = 127 - ELYTRAEVERYWHERE_LANDING_CLEARANCE;

	/** Start landing once within this horizontal distance of the goal (Baritone itself uses 48). */
	@Unique
	private static final double ELYTRAEVERYWHERE_LAND_TRIGGER = 64.0;

	/** True once we've routed this flight to a landing spot, so we don't re-route every tick. */
	@Unique
	private boolean elytraeverywhere$landingHandled;

	@Unique
	private int elytraeverywhere$handledDestX;

	@Unique
	private int elytraeverywhere$handledDestZ;

	@Inject(method = "onTick(ZZ)Lbaritone/api/process/PathingCommand;", at = @At("HEAD"), remap = false)
	private void elytraeverywhere$takeoverEndLanding(boolean calcFailed, boolean isSafeToCancel, CallbackInfoReturnable<PathingCommand> cir) {
		final Minecraft mc = Minecraft.getInstance();
		final LocalPlayer player = mc.player;
		final ClientLevel world = mc.level;
		// Only the End suffers the void-landing bug; leave the (verified) Overworld and the
		// untouched Nether landing alone.
		if (player == null || world == null || world.dimension() != Level.END) {
			return;
		}
		if (player.onGround()) {
			// On the ground (still walking to a takeoff spot, or already landed) - nothing to
			// take over. Using onGround() rather than isGliding()/isFallFlying() keeps this one
			// source compiling across the whole 1.21.x line: that elytra method was renamed
			// isFallFlying -> isGliding in 1.21.2, but onGround() has been stable throughout.
			return;
		}
		final BlockPos dest = this.currentDestination();
		if (dest == null) {
			return;
		}

		// Already routed this destination once; don't churn it every tick.
		if (elytraeverywhere$landingHandled
				&& dest.getX() == elytraeverywhere$handledDestX
				&& dest.getZ() == elytraeverywhere$handledDestZ) {
			return;
		}

		final ElytraProcessAccessor self = (ElytraProcessAccessor) (Object) this;
		final boolean safety = self.elytraeverywhere$shouldLandForSafety();
		final double dx = player.getX() - (dest.getX() + 0.5);
		final double dz = player.getZ() - (dest.getZ() + 0.5);
		final boolean nearGoal = (dx * dx + dz * dz) <= (ELYTRAEVERYWHERE_LAND_TRIGGER * ELYTRAEVERYWHERE_LAND_TRIGGER);
		if (!nearGoal && !safety) {
			return; // still cruising
		}

		// Emergency lands where we are; a normal completion lands at the goal.
		final int originX = safety ? player.getBlockX() : dest.getX();
		final int originZ = safety ? player.getBlockZ() : dest.getZ();
		final BetterBlockPos ground = elytraeverywhere$findNearestEndGround(world, originX, originZ);

		if (ground != null) {
			final BetterBlockPos target = new BetterBlockPos(ground.x, ground.y + ELYTRAEVERYWHERE_LANDING_CLEARANCE, ground.z);
			EELog.log("[landing] End: routing to nearest solid ground {} (descend target {})", ground, target);
			self.elytraeverywhere$pathTo0(target, true);       // fly to & descend onto real ground
			self.elytraeverywhere$setLandingSpot(target);      // endPos used by the LANDING state
			self.elytraeverywhere$setGoingToLandingSpot(true); // make Baritone skip its own (void-diving) finder
			elytraeverywhere$landingHandled = true;
			elytraeverywhere$handledDestX = target.x;           // our reroute is now the destination; don't re-handle it
			elytraeverywhere$handledDestZ = target.z;
			return;
		}

		// No solid ground within range. Whatever happens, suppress Baritone's finder so it can
		// neither freeze scanning the void nor pick an air block and dive.
		self.elytraeverywhere$setGoingToLandingSpot(true);
		if (safety) {
			// Emergency with nowhere to land: descend in place instead of orbiting forever.
			self.elytraeverywhere$setLandingSpot(new BetterBlockPos(player.getBlockX(), player.getBlockY(), player.getBlockZ()));
			elytraeverywhere$landingHandled = true;
			elytraeverywhere$handledDestX = dest.getX();
			elytraeverywhere$handledDestZ = dest.getZ();
			EELog.log("[landing] End emergency: no ground within {} blocks - descending in place", ELYTRAEVERYWHERE_SEARCH_RADIUS);
		} else {
			// Keep scanning on later ticks as chunks stream in / we drift toward an island.
			EELog.log("[landing] End: no solid ground within {} blocks of {},{} yet - circling, not diving", ELYTRAEVERYWHERE_SEARCH_RADIUS, originX, originZ);
		}
	}

	/**
	 * Nearest solid, standable, non-edge ground column to {@code (originX, originZ)}, searched
	 * outward ring by ring (so the closest wins) within {@link #ELYTRAEVERYWHERE_SEARCH_RADIUS}.
	 * Returns the ground block itself, or {@code null} if nothing suitable is loaded nearby.
	 */
	@Unique
	private BetterBlockPos elytraeverywhere$findNearestEndGround(ClientLevel world, int originX, int originZ) {
		BetterBlockPos best = null;
		long bestDistSq = Long.MAX_VALUE;
		for (int r = 0; r <= ELYTRAEVERYWHERE_SEARCH_RADIUS; r++) {
			// Every cell in ring r is at least r away; if we already have something closer, stop.
			if (best != null && (long) r * r > bestDistSq) {
				break;
			}
			for (int gx = -r; gx <= r; gx++) {
				for (int gz = -r; gz <= r; gz++) {
					if (Math.max(Math.abs(gx), Math.abs(gz)) != r) {
						continue; // only the shell of this ring
					}
					final BetterBlockPos g = elytraeverywhere$groundAt(world, originX + gx, originZ + gz);
					if (g == null) {
						continue;
					}
					final long d = (long) gx * gx + (long) gz * gz;
					if (d < bestDistSq) {
						bestDistSq = d;
						best = g;
					}
				}
			}
		}
		return best;
	}

	/**
	 * The top solid block at {@code (x, z)} if it makes a safe touchdown: loaded, within the
	 * octree's reachable height, standable, with {@link #ELYTRAEVERYWHERE_LANDING_CLEARANCE} air
	 * above, and not on the 1-block rim of an island (which would slide the player into the void).
	 */
	@Unique
	private BetterBlockPos elytraeverywhere$groundAt(ClientLevel world, int x, int z) {
		if (!world.hasChunk(x >> 4, z >> 4)) {
			return null;
		}
		final int groundY = world.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
		if (groundY < 0 || groundY > ELYTRAEVERYWHERE_MAX_GROUND_Y) {
			return null; // void column, or too high for the octree
		}
		if (!elytraeverywhere$isStandable(world.getBlockState(new BlockPos(x, groundY, z)))) {
			return null;
		}
		for (int dy = 1; dy <= ELYTRAEVERYWHERE_LANDING_CLEARANCE; dy++) {
			if (!world.getBlockState(new BlockPos(x, groundY + dy, z)).isAir()) {
				return null; // something overhead -> no room to glide down
			}
		}
		for (int nx = -1; nx <= 1; nx++) {
			for (int nz = -1; nz <= 1; nz++) {
				if ((nx != 0 || nz != 0) && world.getBlockState(new BlockPos(x + nx, groundY, z + nz)).isAir()) {
					return null; // an edge over the void
				}
			}
		}
		return new BetterBlockPos(x, groundY, z);
	}

	/** A block you can actually stand on: solid, not a fluid, not a hazard. */
	@Unique
	private boolean elytraeverywhere$isStandable(BlockState state) {
		if (state.isAir() || !state.getFluidState().isEmpty()) {
			return false;
		}
		final Block block = state.getBlock();
		return block != Blocks.LAVA
				&& block != Blocks.MAGMA_BLOCK
				&& block != Blocks.FIRE
				&& block != Blocks.SOUL_FIRE
				&& block != Blocks.CACTUS;
	}
}
