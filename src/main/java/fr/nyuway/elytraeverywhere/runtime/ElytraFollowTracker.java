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
 * Keeps Baritone's elytra autopilot aimed at a moving {@code #follow} target.
 *
 * <p>When the player is gliding ({@link LocalPlayer#isFallFlying()}) and Baritone's
 * {@link IFollowProcess} has an active filter (set by {@code #follow player <name>} or
 * {@code #follow entities}), this tracker continuously updates the elytra destination
 * toward the closest matching entity so the autopilot never tries to land just because
 * the target has moved.
 *
 * <h3>Why a live update is necessary</h3>
 * {@link IElytraProcess#pathTo} sets a <em>fixed</em> destination. Once the player
 * comes within ~48 blocks of that point, {@code ElytraProcess} triggers its landing
 * sequence. If the target has moved in the meantime the player would land at an empty
 * spot instead of chasing them. This tracker pre-empts that by issuing a fresh
 * {@code pathTo} whenever:
 * <ul>
 *   <li>the target has moved more than {@value #REPATH_DIST_BLOCKS} blocks from the last
 *       issued goal, or</li>
 *   <li>the player is within {@value #APPROACH_DIST_BLOCKS} blocks of the current goal
 *       (before ElytraProcess's own 48-block landing trigger fires).</li>
 * </ul>
 * A minimum interval of {@value #MIN_REPATH_TICKS} ticks (~1 s) between two repaths
 * avoids thrashing the native pathfinder.
 *
 * <p>On the ground the {@code IFollowProcess} continues handling walking normally;
 * this class does nothing when {@code isFallFlying()} is false.
 */
public final class ElytraFollowTracker {

    /** Repath when the target moves this many blocks from the last issued goal. */
    private static final int REPATH_DIST_BLOCKS = 8;
    private static final int REPATH_DIST_SQ = REPATH_DIST_BLOCKS * REPATH_DIST_BLOCKS;

    /**
     * Repath when the player is within this many blocks of the current goal.
     * Must be greater than ElytraProcess's own 48-block landing trigger so we always
     * beat it to the punch.
     */
    private static final int APPROACH_DIST_BLOCKS = 55;
    private static final int APPROACH_DIST_SQ = APPROACH_DIST_BLOCKS * APPROACH_DIST_BLOCKS;

    /** Minimum client ticks between two consecutive repaths (~1 second at 20 tps). */
    private static final int MIN_REPATH_TICKS = 20;

    /** Don't issue a new goal when already this close to the target (avoid ramming). */
    private static final int MIN_DIST_BLOCKS = 6;
    private static final int MIN_DIST_SQ = MIN_DIST_BLOCKS * MIN_DIST_BLOCKS;

    private BlockPos lastGoal;
    private int ticksSinceRepath = MIN_REPATH_TICKS; // start ready to repath immediately

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
        if (ticksSinceRepath < MIN_REPATH_TICKS) {
            return;
        }

        // Use Baritone's own entity stream — identical source to what FollowProcess
        // uses internally, but without the followTargetMaxDistance cap so long-range
        // elytra tracking works regardless of that setting.
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

        // Repath if there is no active goal yet...
        final boolean noActiveGoal = lastGoal == null || !elytra.isActive();

        // ...or the target has drifted far enough from the last goal...
        final boolean targetMoved = lastGoal != null && targetPos.distSqr(lastGoal) > REPATH_DIST_SQ;

        // ...or we're getting close to the last goal and risk triggering ElytraProcess's
        // landing sequence (which fires at <48 blocks from the path's final node).
        final double playerToGoalSq = lastGoal != null
                ? player.distanceToSqr(lastGoal.getX() + 0.5, lastGoal.getY() + 0.5, lastGoal.getZ() + 0.5)
                : Double.MAX_VALUE;
        final boolean approachingGoal = playerToGoalSq < APPROACH_DIST_SQ;

        if (noActiveGoal || targetMoved || approachingGoal) {
            elytra.pathTo(targetPos);
            lastGoal = targetPos;
            ticksSinceRepath = 0;
            EELog.log("[elytrafollow] repath -> {} ({})", targetPos, target.getName().getString());
        }
    }

    /** Resets internal state. Call when a new follow is started or follow is cancelled. */
    public void reset() {
        lastGoal = null;
        ticksSinceRepath = MIN_REPATH_TICKS;
    }
}
