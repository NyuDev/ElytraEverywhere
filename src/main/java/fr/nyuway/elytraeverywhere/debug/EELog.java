package fr.nyuway.elytraeverywhere.debug;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central, always-on verbose logger for the addon's own decisions (landing
 * search, retry capping, water landing, native-input guards). Kept in a plain
 * runtime class - not a mixin - so it can hold a normal static logger, and so
 * mixins can log by calling into it. These events are infrequent (per landing /
 * per guard trip), so logging them is cheap and makes the addon debuggable from
 * the console / latest.log without attaching a debugger.
 */
public final class EELog {

	private static final Logger LOGGER = LoggerFactory.getLogger("ElytraEverywhere/Debug");

	private EELog() {
	}

	public static void log(String message) {
		LOGGER.info(message);
	}

	public static void log(String format, Object... args) {
		LOGGER.info(format, args);
	}
}
