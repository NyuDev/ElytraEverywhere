package fr.nyuway.elytraeverywhere.runtime;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.process.IElytraProcess;
import baritone.api.process.IFollowProcess;
import fr.nyuway.elytraeverywhere.debug.EELog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;

import java.util.Comparator;

/**
 * Bridges Baritone's {@link IFollowProcess} with {@link IElytraProcess}: when the player
 * is gliding with an elytra AND a {@code #follow} (or {@code /eefollow}) target is set,
 * this tracker continuously steers the elytra autopilot toward the closest matching entity.
 *
 * <p>How it plugs in:
 * <ol>
 *   <li>The user runs {@code /eefollow <player>} (or {@code #follow player <name>}), which
 *       calls {@link IFollowProcess#follow} to register a filter.</li>
 *   <li>While the player is on the ground, {@code IFollowProcess} already handles walking
 *       toward the target — nothing changes there.</li>
 *   <li>As soon as the player is gliding ({@link LocalPlayer#isFallFlying()}), this tracker
 *       takes over: on each client tick it finds the closest matching entity and asks
 *       {@link IElytraProcess#pathTo} to (re-)route toward that position. The elytra autopilot
 *       handles the actual flight; we only update the destination when the target moves
 *       significantly, so path recomputation is infrequent.</li>
 *   <li>When the player lands, {@code isFallFlying()} becomes false, this class does nothing,
 *       and {@code IFollowProcess} resumes walking on its own.</li>
 * </ol>
 *
 * <p>Safety: we skip the repath if the target is already very close (within
 * {@value #MIN_DIST_BLOCKS} blocks), avoiding ramming into them at elytra speed.
 */
public final class ElytraFollowTracker {

    /** Minimum distance (blocks) to keep from the target. No repath below this. */
    private static final int MIN_DIST_BLOCKS = 6;
    private static final int MIN_DIST_SQ = MIN_DIST_BLOCKS * MIN_DIST_BLOCKS;

    /**
     * Repath when the target has moved at least this many blocks from the last issued goal.
     * Large enough to avoid constant recomputation, small enough to stay on target.
     */
    private static final int REPATH_THRESHOLD_SQ = 16 * 16;

    /**
     * Minimum client ticks between two consecutive repaths (~2 seconds at 20 tps).
     * Prevents hammering {@link IElytraProcess#pathTo}, which rebuilds the native path context.
     */
    private static final int MIN_REPATH_TICKS = 40;

    private BlockPos lastGoal;
    private int ticksSinceRepath = MIN_REPATH_TICKS; // allow immediate first repath

    /** Resets internal state; call when follow is started or stopped externally. */
    public void reset() {
        lastGoal = null;
        ticksSinceRepath = MIN_REPATH_TICKS;
    }

    public void onClientTick(Minecraft client) {
        final LocalPlayer player = client.player;
        if (player == null || !player.isFallFlying()) {
            return;
        }

        final IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        final IFollowProcess followProcess = baritone.getFollowProcess();
        if (followProcess.currentFilter() == null) {
            return;
        }

        ticksSinceRepath++;

        // Use Baritone's own entity stream (same source as FollowProcess uses internally)
        // but without the followTargetMaxDistance cap, so long-range elytra follow works.
        final Entity target = baritone.getPlayerContext().entitiesStream()
                .filter(e -> !e.equals(player) && e.isAlive() && followProcess.currentFilter().test(e))
                .min(Comparator.comparingDouble(player::distanceToSqr))
                .orElse(null);

        if (target == null) {
            return;
        }

        // Don't ram into the target at elytra speed.
        if (player.distanceToSqr(target) < MIN_DIST_SQ) {
            return;
        }

        final BlockPos targetPos = target.blockPosition();
        final IElytraProcess elytra = baritone.getElytraProcess();

        final boolean noActiveGoal = lastGoal == null || !elytra.isActive();
        final boolean targetMoved = lastGoal != null && targetPos.distSqr(lastGoal) > REPATH_THRESHOLD_SQ;

        if ((noActiveGoal || targetMoved) && ticksSinceRepath >= MIN_REPATH_TICKS) {
            elytra.pathTo(targetPos);
            lastGoal = targetPos;
            ticksSinceRepath = 0;
            EELog.log("[elytrafollow] repath -> {} ({})", targetPos, target.getName().getString());
        }
    }
}
