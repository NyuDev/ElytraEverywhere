package fr.nyuway.elytraeverywhere.runtime;

import baritone.api.BaritoneAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps Baritone's {@code elytraPredictTerrain} setting consistent with the
 * dimension the player is currently in.
 *
 * <p>Terrain prediction only makes sense in the Nether: it rebuilds the
 * {@code 0..128} Nether terrain from {@code elytraNetherSeed} so Baritone can
 * plan a path beyond render distance. In the Overworld or End that prediction
 * is wrong and would steer the autopilot into terrain that isn't really there,
 * so we force it off. The previous value is remembered and restored when the
 * player comes back to the Nether, so a user who deliberately enabled prediction
 * for Nether flights never has it silently clobbered.
 *
 * <p>Single responsibility: dimension-aware management of exactly one Baritone
 * setting. It knows nothing about the dimension gate (that is the Mixins' job).
 */
public final class PredictTerrainPolicy {

	private static final Logger LOGGER = LoggerFactory.getLogger("ElytraEverywhere/PredictTerrain");

	/** True when we, and not the user, turned prediction off - so we may restore it. */
	private boolean disabledByUs;

	public void onClientTick(Minecraft client) {
		if (client.level == null) {
			return;
		}

		final boolean inNether = client.level.dimension() == Level.NETHER;
		final var predictTerrain = BaritoneAPI.getSettings().elytraPredictTerrain;

		if (!inNether) {
			if (predictTerrain.value) {
				predictTerrain.value = false;
				disabledByUs = true;
				LOGGER.info("Outside the Nether - disabled elytraPredictTerrain so the autopilot follows real loaded chunks.");
			}
		} else if (disabledByUs) {
			predictTerrain.value = true;
			disabledByUs = false;
			LOGGER.info("Back in the Nether - restored elytraPredictTerrain.");
		}
	}
}
