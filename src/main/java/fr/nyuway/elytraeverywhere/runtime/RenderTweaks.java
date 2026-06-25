package fr.nyuway.elytraeverywhere.runtime;

import baritone.api.BaritoneAPI;
import baritone.api.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tidies up Baritone's path rendering, which on this version draws the path as
 * chunky 5px lines. We thin it down <b>once per world join</b>, and only where
 * the value is still Baritone's default - so anyone who set their own width keeps
 * it. Applied a couple of seconds after joining, after Baritone has loaded its
 * own settings for the world (otherwise it would overwrite us).
 *
 * <p>Note: we deliberately leave {@code elytraRenderSimulation} (the cyan line
 * showing where the autopilot is heading) ON - it's useful in flight.
 *
 * <p>Single responsibility: dimension-independent render-setting hygiene.
 */
public final class RenderTweaks {

	private static final Logger LOGGER = LoggerFactory.getLogger("ElytraEverywhere/Render");

	private static final int SETTLE_TICKS = 40; // ~2s, let Baritone load its config first

	private Level lastWorld;
	private int ticksInWorld;
	private boolean appliedForThisWorld;

	public void onClientTick(Minecraft client) {
		if (client.level == null) {
			lastWorld = null;
			return;
		}
		if (client.level != lastWorld) {
			lastWorld = client.level;
			ticksInWorld = 0;
			appliedForThisWorld = false;
			return;
		}
		if (appliedForThisWorld) {
			return;
		}
		if (++ticksInWorld < SETTLE_TICKS) {
			return;
		}
		applyCrisperDefaults();
		appliedForThisWorld = true;
	}

	private void applyCrisperDefaults() {
		final Settings settings = BaritoneAPI.getSettings();
		boolean changed = false;

		if (settings.pathRenderLineWidthPixels.value == 5.0f) { // Baritone default
			settings.pathRenderLineWidthPixels.value = 4.0f;
			changed = true;
		}

		if (changed) {
			LOGGER.info("Set the Baritone path line to 4px. Change with '#set pathRenderLineWidthPixels <n>' to taste.");
		}
	}
}
