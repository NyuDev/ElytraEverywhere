package fr.nyuway.elytraeverywhere.runtime;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import fr.nyuway.elytraeverywhere.debug.EELog;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

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

	public void onClientTick(MinecraftClient client) {
		final ClientPlayerEntity player = client.player;
		if (player == null || !player.isTouchingWater()) {
			return;
		}
		final IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
		if (baritone.getElytraProcess().isActive()) {
			// Console only - no chat spam. The user wanted all addon output in the log.
			EELog.log("[landing] touched water -> finishing landing at the surface ({})", player.getBlockPos());
			baritone.getPathingBehavior().cancelEverything();
		}
	}
}
