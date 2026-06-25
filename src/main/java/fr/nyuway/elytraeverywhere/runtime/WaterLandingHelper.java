package fr.nyuway.elytraeverywhere.runtime;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import fr.nyuway.elytraeverywhere.debug.EELog;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Finishes a water landing the instant the player splashes in.
 *
 * <p>Baritone's elytra landing is built for solid ground, so we step in the moment
 * the player <b>touches the water</b> while the autopilot is flying: cancel, land
 * at the surface. We intentionally do NOT wait for {@code !isGliding} - with elytra
 * you keep gliding underwater all the way to the seabed first, which is exactly the
 * "I sank to the bottom and crashed" behaviour we're fixing. The autopilot only ever
 * reaches the water while landing (cruise stays well above terrain), so touching
 * water unambiguously means "land here".
 */
public final class WaterLandingHelper {

	public void onClientTick(Minecraft client) {
		final LocalPlayer player = client.player;
		if (player == null || !player.isInWater()) {
			return;
		}
		final IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
		if (baritone.getElytraProcess().isActive()) {
			EELog.log("[landing] touched water -> finishing landing at the surface ({})", player.blockPosition());
			baritone.getPathingBehavior().cancelEverything();
			// On land Baritone prints its own "Done :)" when the path completes, but we
			// cancel the water landing before it gets there, so that line never shows.
			// Re-send it ourselves, in Baritone's exact format, so a water touchdown reads
			// the same as a ground one. (Only here, i.e. only for water - never on land.)
			final Component done = Component.literal("[").withStyle(ChatFormatting.DARK_PURPLE)
					.append(Component.literal("Baritone").withStyle(ChatFormatting.LIGHT_PURPLE))
					.append(Component.literal("]").withStyle(ChatFormatting.DARK_PURPLE))
					.append(Component.literal(" Done :)").withStyle(ChatFormatting.GRAY));
			//? if >=26.1 {
			/*player.sendSystemMessage(done);*/
			//?} else {
			player.displayClientMessage(done, false);
			//?}
		}
	}
}
