package fr.nyuway.elytraeverywhere.runtime;

import java.util.HashMap;
import java.util.Map;

/**
 * Rate-limits a couple of Baritone status lines that, during a perfectly normal
 * elytra flight, are re-sent every tick and flood the chat.
 *
 * <p>Both are purely cosmetic - the flight and landing work fine, Baritone just
 * narrates each retry:
 * <ul>
 *   <li>"Failed to compute next segment" - the path occasionally can't be extended
 *       one tick and is retried the next; harmless, but printed every time;</li>
 *   <li>"Landed, but still moving, waiting for velocity to die down..." - printed
 *       on every tick of the post-landing settle, before the player has fully
 *       stopped.</li>
 * </ul>
 *
 * <p>We let each through at most once every {@link #THROTTLE_MS}, so the player
 * still sees it happen without the wall of duplicates. Anything else Baritone
 * logs is untouched. Single responsibility: the throttle decision + its state,
 * kept out of the mixin so it can hold normal static state.
 */
public final class MessageThrottle {

	/** Substrings of the Baritone chat lines we rate-limit (matched with contains). */
	private static final String[] THROTTLED = {
			"Failed to compute next segment",
			"Landed, but still moving",
	};

	/** Minimum gap between two showings of the same throttled line. */
	private static final long THROTTLE_MS = 3_000L;

	private static final Map<String, Long> lastShown = new HashMap<>();

	private MessageThrottle() {
	}

	/**
	 * @return {@code true} if this Baritone chat line is a known spammy one that was
	 *         already shown within the last {@link #THROTTLE_MS} and should be dropped.
	 */
	public static boolean shouldSuppress(String message) {
		if (message == null) {
			return false;
		}
		for (String key : THROTTLED) {
			if (message.contains(key)) {
				final long now = System.currentTimeMillis();
				final Long last = lastShown.get(key);
				if (last != null && now - last < THROTTLE_MS) {
					return true; // shown too recently - drop this duplicate
				}
				lastShown.put(key, now);
				return false; // first one (or past the cooldown) - let it through
			}
		}
		return false; // not a throttled line
	}
}
