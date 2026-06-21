package fr.nyuway.elytraeverywhere.mixin;

import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.process.PathingCommand;
import baritone.api.utils.BetterBlockPos;
import fr.nyuway.elytraeverywhere.debug.EELog;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
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
 * if (ctx.player() == null || ctx.player().getWorld().getRegistryKey() != World.NETHER) {
 *     return; // silently refuses to fly in any other dimension
 * }
 * }</pre>
 *
 * <p>This {@code pathTo0} is the single choke point for elytra: both the public
 * {@code pathTo} API and Baritone's own landing logic funnel through it, and if
 * it returns early no {@code ElytraBehavior} is ever created, so nothing flies.
 * We redirect only the {@code getRegistryKey()} call evaluated by that gate and
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
 * the {@code != World.NETHER} gate.
 */
@Mixin(targets = "baritone.process.ElytraProcess", remap = false)
public abstract class ElytraProcessMixin {

	@Redirect(
			method = "a(Lnet/minecraft/util/math/BlockPos;Z)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/World;getRegistryKey()Lnet/minecraft/registry/RegistryKey;",
					remap = true
			),
			remap = true
	)
	private RegistryKey<World> elytraeverywhere$reportNether(World world) {
		return World.NETHER;
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
	@Inject(method = "a(Lnet/minecraft/block/Block;)Z", at = @At("HEAD"), cancellable = true, remap = true)
	private static void elytraeverywhere$allowGroundLanding(Block block, CallbackInfoReturnable<Boolean> cir) {
		final MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.world.getRegistryKey() == World.NETHER) {
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
			int clampedY = MathHelper.clamp(block.y, 1, 127);
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
	@Inject(method = "a(Lnet/minecraft/util/math/BlockPos;Z)V", at = @At("HEAD"), cancellable = true, remap = true)
	private void elytraeverywhere$capLandingChurn(BlockPos destination, boolean appendDestination, CallbackInfo ci) {
		if (!appendDestination) {
			landingRetries = 0; // fresh flight
			return;
		}
		if (landingRetries > MAX_LANDING_RETRIES) {
			EELog.log("[landing] giving up auto-landing after {} retries - gliding in instead (avoids native context-churn crash)", landingRetries);
			ci.cancel(); // don't spin up yet another landing context
		}
	}
}
