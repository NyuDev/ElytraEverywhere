package fr.nyuway.elytraeverywhere;

import fr.nyuway.elytraeverywhere.command.DebugCommand;
import fr.nyuway.elytraeverywhere.compat.BaritoneCompat;
import fr.nyuway.elytraeverywhere.debug.ChatConsoleMirror;
import fr.nyuway.elytraeverywhere.runtime.PredictTerrainPolicy;
import fr.nyuway.elytraeverywhere.runtime.RenderTweaks;
import fr.nyuway.elytraeverywhere.runtime.WaterLandingHelper;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mod entry point (composition root).
 *
 * <p>A Baritone addon: the Mixins in {@code ..mixin} make Baritone's own
 * {@code #elytra} work in every dimension (remove the Nether gate, fix the chunk
 * packing, guard the native octree writes, and stop the Overworld auto-landing
 * search from freezing). This class only wires the runtime helpers:
 * <ul>
 *   <li>{@link PredictTerrainPolicy} keeps {@code elytraPredictTerrain} off
 *       outside the Nether;</li>
 *   <li>{@link ChatConsoleMirror} + {@code /eedebug} mirror chat to the console
 *       on demand for readable diagnostics.</li>
 * </ul>
 */
public final class ElytraEverywhere implements ClientModInitializer {

	public static final String MOD_ID = "elytraeverywhere";

	private static final Logger LOGGER = LoggerFactory.getLogger("ElytraEverywhere");

	@Override
	public void onInitializeClient() {
		// Wrong (or no) Baritone: our mixins target the Meteor fork's class names, which
		// don't exist in the obfuscated official build. Stay inert and tell the player
		// where to get the right files, without touching any Baritone class. The mixin
		// configs are non-required, so the absent targets are skipped, not fatal.
		if (!BaritoneCompat.isMeteorBaritonePresent()) {
			BaritoneCompat.warnIncompatibleBaritone();
			LOGGER.warn("'baritone-meteor' not found - ElytraEverywhere is idle (it hooks the Meteor "
					+ "fork, not the official Baritone). See https://github.com/NyuDev/ElytraEverywhere");
			return;
		}

		final PredictTerrainPolicy predictTerrainPolicy = new PredictTerrainPolicy();
		final RenderTweaks renderTweaks = new RenderTweaks();
		final WaterLandingHelper waterLandingHelper = new WaterLandingHelper();
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			predictTerrainPolicy.onClientTick(client);
			renderTweaks.onClientTick(client);
			waterLandingHelper.onClientTick(client);
		});

		final ChatConsoleMirror chatMirror = new ChatConsoleMirror();
		new DebugCommand(chatMirror).register();
		ClientReceiveMessageEvents.GAME.register(chatMirror::onGameMessage);

		LOGGER.info("Baritone elytra fixed for every dimension. '/eedebug' toggles chat-to-console logging.");
	}
}
