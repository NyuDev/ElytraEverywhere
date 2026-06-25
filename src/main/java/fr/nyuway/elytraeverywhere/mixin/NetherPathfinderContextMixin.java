package fr.nyuway.elytraeverywhere.mixin;

import baritone.api.utils.Pair;
import dev.babbaj.pathfinder.NetherPathfinder;
import dev.babbaj.pathfinder.PathSegment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes Baritone's elytra pathfinder actually usable outside the Nether.
 *
 * <p>The whole elytra system assumes {@code octreeY == worldY}, and the native
 * octree is only 128 tall (y 0..127). Baritone fills it in
 * {@code NetherPathfinderContext#writeChunkData} with:
 * <pre>{@code
 * ChunkSection[] sections = chunk.getSectionArray();
 * for (int y0 = 0; y0 < 8; y0++) {            // sections 0..7
 *     ... write section[y0] at octreeY = y0*16 ...
 * }
 * }</pre>
 * In the Nether section 0 starts at world y 0, so this is correct. In the
 * <b>Overworld</b> section 0 starts at world y <b>-64</b>, so it loads the
 * underground (y -64..63) into octree y 0..127 while the player flies at y 64+ -
 * the pathfinder then thinks the player is buried in rock, finds no path, and
 * spams "Failed to compute next segment" until the client locks up.
 *
 * <p>The fix is one redirect: when the packer asks the chunk for its sections,
 * hand back the 8 sections that cover world y {@code [0, 128)} for the current
 * dimension, so octree y keeps matching world y everywhere. The Nether/End are
 * already aligned at y 0 and are returned untouched.
 *
 * <p>(In the minified Baritone build {@code writeChunkData} is inlined into the
 * packing lambda {@code a(SoftReference)}, which is where the redirect lands.)
 */
@Mixin(targets = "baritone.process.elytra.NetherPathfinderContext", remap = false)
public abstract class NetherPathfinderContextMixin {

	@Redirect(
			method = "a(Ljava/lang/ref/SoftReference;)V",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/chunk/LevelChunk;getSections()[Lnet/minecraft/world/level/chunk/LevelChunkSection;",
					remap = true
			),
			remap = true
	)
	private LevelChunkSection[] elytraeverywhere$sectionsForFlightBand(LevelChunk chunk) {
		LevelChunkSection[] all = chunk.getSections();
		// Number of sections below world y 0 (4 in the Overworld, 0 in Nether/End).
		// Mojang renamed getMinBuildHeight() -> getMinY() after 1.21.1.
		//? if <1.21.3 {
		/*int offset = (-chunk.getMinBuildHeight()) >> 4;*/
		//?} else {
		int offset = (-chunk.getMinY()) >> 4;
		//?}
		if (offset <= 0) {
			return all;
		}
		LevelChunkSection[] band = new LevelChunkSection[8];
		for (int i = 0; i < 8 && (i + offset) < all.length; i++) {
			band[i] = all[i + offset];
		}
		return band;
	}

	/**
	 * Drops live block updates that fall outside the octree's {@code [0,128)} band.
	 *
	 * <p>{@code queueBlockUpdate} writes each changed block straight into the native
	 * octree with {@code Octree.setBlock(ptr, x, pos.getY(), z, solid)}, guarded only
	 * against {@code y >= 128}. The Nether never has blocks below y=0, so Baritone
	 * never needed a lower guard - but the Overworld does (deepslate down to y=-64).
	 * Feeding the native lib a negative y writes out of bounds and segfaults the JVM
	 * (EXCEPTION_ACCESS_VIOLATION). We cancel those updates; they're below the
	 * flyable band anyway, so nothing useful is lost.
	 *
	 * <p>Target: the minified per-block lambda body {@code a(long, Pair)} of
	 * {@code queueBlockUpdate}.
	 */
	@Inject(method = "a(JLbaritone/api/utils/Pair;)V", at = @At("HEAD"), cancellable = true, remap = false)
	private static void elytraeverywhere$skipOutOfBoundsBlockUpdate(long ptr, Pair<BlockPos, ?> block, CallbackInfo ci) {
		int y = block.first().getY();
		if (y < 0 || y >= 128) {
			ci.cancel();
		}
	}

	/**
	 * Stops the native raytracer from fast-failing the whole JVM.
	 *
	 * <p>The C++ raytracer is fragile - Baritone already special-cases
	 * {@code start == dest} ("if start == dest then the cpp raytracer dies"). When
	 * elytra flies under an End island into a block, or the player position spikes
	 * on a fatal collision, a raytrace gets degenerate coordinates, prints
	 * "raytrace whiffed" and crashes the process (EXCEPTION 0xC0000409, a native
	 * stack-buffer fast-fail that Java cannot catch).
	 *
	 * <p>So we never hand the native calls unsafe coordinates: anything non-finite,
	 * outside the octree's {@code [0,128)} y band / the world border, or an absurd
	 * distance is reported as "not visible" (blocked) instead of being raytraced.
	 * Blocked is the safe, conservative answer - Baritone just treats that view as
	 * obstructed. Targets the minified {@code raytrace(Vec3,Vec3)} and
	 * {@code raytrace(double[],double[])}.
	 */
	@Inject(method = "a(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;)Z", at = @At("HEAD"), cancellable = true, remap = true)
	private void elytraeverywhere$guardSingleRaytrace(Vec3 start, Vec3 end, CallbackInfoReturnable<Boolean> cir) {
		if (elytraeverywhere$elytraGone()
				|| !elytraeverywhere$pointSafe(start.x, start.y, start.z)
				|| !elytraeverywhere$pointSafe(end.x, end.y, end.z)
				|| Math.abs(end.x - start.x) > 4096.0
				|| Math.abs(end.y - start.y) > 4096.0
				|| Math.abs(end.z - start.z) > 4096.0) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "a([D[D)Z", at = @At("HEAD"), cancellable = true, remap = false)
	private void elytraeverywhere$guardMultiRaytrace(double[] src, double[] dst, CallbackInfoReturnable<Boolean> cir) {
		if (elytraeverywhere$elytraGone() || !elytraeverywhere$coordsSafe(src) || !elytraeverywhere$coordsSafe(dst)) {
			cir.setReturnValue(false);
		}
	}

	/**
	 * True once the elytra process has been cancelled. Its {@code ElytraBehavior} -
	 * and soon the native context this raytrace points at - is being torn down, so a
	 * late raytrace (e.g. from a pending async path callback) would read freed native
	 * memory and crash the JVM. Treat it as "not visible" and don't touch the lib.
	 */
	private static boolean elytraeverywhere$elytraGone() {
		try {
			return !baritone.api.BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().isActive();
		} catch (Throwable ignored) {
			return false;
		}
	}

	private static boolean elytraeverywhere$pointSafe(double x, double y, double z) {
		return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
				&& y >= 0.0 && y < 128.0
				&& Math.abs(x) < 3.0e7 && Math.abs(z) < 3.0e7;
	}

	private static boolean elytraeverywhere$coordsSafe(double[] coords) {
		// Flat [x,y,z, x,y,z, ...] triples; every third value is a Y component.
		for (int i = 0; i < coords.length; i++) {
			double v = coords[i];
			if (!Double.isFinite(v)) {
				return false;
			}
			if (i % 3 == 1 && (v < 0.0 || v >= 128.0)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Keeps the y coordinates handed to the native pathfinder inside its valid
	 * {@code [0,128)} range.
	 *
	 * <p>Over open ocean the landing search can pick a high air block and offset it
	 * upward, producing a destination above y=128; flying high in the Overworld can
	 * likewise put the start above 128. {@code NetherPathfinder.pathFind} then
	 * throws {@code IllegalArgumentException: Invalid y1 or y2}, which Baritone does
	 * not catch ("An unhandled exception occurred"). Clamping both y values keeps
	 * the path valid so the descent continues to the water, where the water-landing
	 * helper finishes it.
	 *
	 * <p>In the Nether everything is already 0..127, so this is a no-op there.
	 */
	@Redirect(
			method = "a(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Ldev/babbaj/pathfinder/PathSegment;",
			at = @At(
					value = "INVOKE",
					target = "Ldev/babbaj/pathfinder/NetherPathfinder;pathFind(JIIIIIIZZIZ)Ldev/babbaj/pathfinder/PathSegment;",
					remap = false
			),
			remap = true
	)
	private PathSegment elytraeverywhere$clampPathFindY(long context, int x1, int y1, int z1, int x2, int y2, int z2,
	                                                    boolean refine, boolean raytrace, int timeoutMs, boolean predictTerrain) {
		int clampedY1 = Math.max(0, Math.min(127, y1));
		int clampedY2 = Math.max(0, Math.min(127, y2));
		if (clampedY1 != y1 || clampedY2 != y2) {
			fr.nyuway.elytraeverywhere.debug.EELog.log("[pathfind] clamped y into [0,127]: ({},{},{})->({},{},{}) / ({},{},{})->({},{},{})",
					x1, y1, z1, x1, clampedY1, z1, x2, y2, z2, x2, clampedY2, z2);
		}
		return NetherPathfinder.pathFind(context, x1, clampedY1, z1, x2, clampedY2, z2, refine, raytrace, timeoutMs, predictTerrain);
	}
}
