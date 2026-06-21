package fr.nyuway.elytraeverywhere.mixin;

import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Removes the second "Nether only" gate, the one guarding the {@code #elytra}
 * chat command in Baritone's {@code ElytraCommand#execute}:
 * <pre>{@code
 * if (ctx.world().getRegistryKey() != World.NETHER) {
 *     throw new CommandInvalidStateException("Only works in the nether");
 * }
 * }</pre>
 *
 * <p>Without this, typing {@code #elytra} outside the Nether throws before it
 * ever reaches {@link ElytraProcessMixin}'s unlocked {@code pathTo0}. Same
 * technique: report {@code NETHER} for the single dimension lookup the gate
 * performs, leaving the rest of the command untouched.
 */
@Mixin(targets = "baritone.command.defaults.ElytraCommand", remap = false)
public abstract class ElytraCommandMixin {

	@Redirect(
			method = "execute",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/World;getRegistryKey()Lnet/minecraft/registry/RegistryKey;",
					remap = true
			)
	)
	private RegistryKey<World> elytraeverywhere$reportNether(World world) {
		return World.NETHER;
	}
}
