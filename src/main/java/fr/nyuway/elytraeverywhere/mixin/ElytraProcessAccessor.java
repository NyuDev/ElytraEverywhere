package fr.nyuway.elytraeverywhere.mixin;

import baritone.api.utils.BetterBlockPos;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Reaches into {@code baritone.process.ElytraProcess}'s private landing internals so
 * {@link ElytraProcessMixin} can take over landing in the End (see that class for why).
 *
 * <p><b>All of these targets are minified to {@code a}.</b> The Meteor Baritone fork renames
 * methods and fields, so {@code pathTo0}, {@code shouldLandForSafety}, {@code landingSpot} and
 * {@code goingToLandingSpot} are all compiled to the name {@code a}. Mixin still resolves each
 * one because the accessor/invoker <i>signature</i> is unique:
 * <ul>
 *   <li>{@code a(BlockPos, boolean)} is the only such method = {@code pathTo0};</li>
 *   <li>{@code a()boolean} is the only no-arg boolean method = {@code shouldLandForSafety};</li>
 *   <li>the {@code BetterBlockPos}-typed field {@code a} = {@code landingSpot};</li>
 *   <li>the first {@code boolean} field {@code a} = {@code goingToLandingSpot}.</li>
 * </ul>
 * If a future Baritone snapshot reshuffles these, re-run {@code javap -p} on
 * {@code ElytraProcess.class} and re-check the descriptors.
 */
@Mixin(targets = "baritone.process.ElytraProcess", remap = false)
public interface ElytraProcessAccessor {

    /** {@code pathTo0(BlockPos, boolean)} - rebuilds the flight; append=true is "land here". */
    @Invoker(value = "a", remap = true)
    void elytraeverywhere$pathTo0(BlockPos destination, boolean appendDestination);

    /** {@code shouldLandForSafety()} - true when almost out of elytra durability or fireworks. */
    @Invoker(value = "a", remap = false)
    boolean elytraeverywhere$shouldLandForSafety();

    /** The {@code landingSpot} field (endPos the LANDING state descends onto). */
    @Accessor(value = "a", remap = false)
    void elytraeverywhere$setLandingSpot(BetterBlockPos landingSpot);

    /** The {@code goingToLandingSpot} flag; setting it true makes onTick skip its own finder. */
    @Accessor(value = "a", remap = false)
    void elytraeverywhere$setGoingToLandingSpot(boolean value);
}
