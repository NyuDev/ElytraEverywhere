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
 * <p>Usage: {@code #follow player <name>} (or any {@code #follow} variant), then take off.
 * No new command is needed. While the player is gliding ({@link LocalPlayer#isFallFlying()})
 * and Baritone's {@link IFollowProcess} has an active filter, this tracker:
 * <ol>
 *   <li>Continuously steers the elytra autopilot toward the closest matching entity.</li>
 *   <li>Exposes {@link #followActive} so {@link fr.nyuway.elytraeverywhere.mixin.ElytraProcessMixin}
 *       can suppress the normal landing sequence while following (landing is only allowed
 *       for genuine safety situations — low durability or fireworks).</li>
 * </ol>
 *
 * <p>On the ground, {@link IFollowProcess} handles walking as before; this class does
 * nothing when {@code isFallFlying()} is false.
 *
 * <h3>Why landing suppression is needed</h3>
 * {@link IElytraProcess#pathTo} sets a <em>fixed</em> destination. Once the player comes
 * within ~48 blocks of that point, {@code ElytraProcess} triggers its landing sequence
 * (via {@code pathTo0(spot, appendDestination=true)}). If the target keeps moving — or if
 * we are simply catching up — the landing sequence fires well before we actually want to stop.
 * {@link fr.nyuway.elytraeverywhere.mixin.ElytraProcessMixin#elytraeverywhere$capLandingChurn}
 * already intercepts every {@code pathTo0(*, true)} call; adding an
 * {@link #followActive} check there suppresses the landing while following is active,
 * while still allowing emergency landings ({@code shouldLandForSafety()}).
 */
public final class ElytraFollowTracker {

    /**
     * True while the player is gliding with an active follow target.
     * Read by {@link fr.nyuway.elytraeverywhere.mixin.ElytraProcessMixin} to suppress
     * the normal landing sequence.
     */
    public static boolean followActive = false;

    /** Repath when the target has moved this many blocks from the last issued goal. */
    private static final int REPATH_DIST_BLOCKS = 8;
    private static final int REPATH_DIST_SQ = REPATH_DIST_BLOCKS * REPATH_DIST_BLOCKS;

    /**
     * Repath when the player is within this many blocks of the current goal.
     * Chosen to be larger than ElytraProcess's own 48-block landing trigger so we
     * issue a fresh {@code pathTo} before that trigger has a chance to fire.
     */
    private static final int APPROACH_DIST_BLOCKS = 55;
    private static final int APPROACH_DIST_SQ = APPROACH_DIST_BLOCKS * APPROACH_DIST_BLOCKS;

    /** Minimum client ticks between two consecutive repaths (~1 s at 20 tps). */
    private static final int MIN_REPATH_TICKS = 20;

    /** Don't issue a new goal when already this close to the target (avoid ramming). */
    private static final int MIN_DIST_BLOCKS = 6;
    private static final int MIN_DIST_SQ = MIN_DIST_BLOCKS * MIN_DIST_BLOCKS;

    private BlockPos lastGoal;
    private int ticksSinceRepath = MIN_REPATH_TICKS;

    public void onClientTick(Minecraft client) {
        final LocalPlayer player = client.player;
        if (player == null || !player.isFallFlying()) {
            followActive = false;
            return;
        }

        final IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        final IFollowProcess followProcess = baritone.getFollowProcess();
        if (followProcess.currentFilter() == null) {
            followActive = false;
            return;
        }

        // Signal ElytraProcessMixin to suppress normal landing while we're following.
        followActive = true;
        ticksSinceRepath++;
        if (ticksSinceRepath < MIN_REPATH_TICKS) {
            return;
        }

        // Use Baritone's own entity stream — same source as FollowProcess uses internally,
        // but without the followTargetMaxDistance cap so long-range elytra tracking works.
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

        // ...or the target has drifted from the last goal...
        final boolean targetMoved = lastGoal != null && targetPos.distSqr(lastGoal) > REPATH_DIST_SQ;

        // ...or we're getting close to the last goal. A repath here issues a fresh path
        // (resetting ElytraProcess's "complete" state) before the 48-block landing trigger
        // can fire. Combined with the landing suppression above, this keeps the elytra
        // perpetually chasing the target rather than trying to land.
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
}
