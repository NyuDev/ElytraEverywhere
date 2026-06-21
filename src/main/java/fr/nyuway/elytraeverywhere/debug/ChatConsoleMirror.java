package fr.nyuway.elytraeverywhere.debug;

import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Optional debug aid: when enabled, mirrors in-game chat (notably Baritone's
 * status messages) to the console/log, so flights can be followed and diagnosed
 * with far more readability than scrolling chat. Toggled by {@code /eedebug}.
 *
 * <p>Single responsibility: hold the on/off state and forward messages. It is the
 * listener registered on Fabric's game-message event; nothing else touches chat.
 */
public final class ChatConsoleMirror {

	private static final Logger LOGGER = LoggerFactory.getLogger("ElytraEverywhere/Chat");

	private boolean enabled;

	public boolean toggle() {
		enabled = !enabled;
		return enabled;
	}

	public boolean isEnabled() {
		return enabled;
	}

	/** Registered on {@code ClientReceiveMessageEvents.GAME}. */
	public void onGameMessage(Text message, boolean overlay) {
		if (enabled && !overlay) {
			LOGGER.info("{}", message.getString());
		}
	}
}
