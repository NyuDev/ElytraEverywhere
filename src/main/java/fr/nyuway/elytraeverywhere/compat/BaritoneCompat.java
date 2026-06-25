package fr.nyuway.elytraeverywhere.compat;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Baritone-flavour compatibility gate.
 *
 * <p>Every patch in this addon targets the <b>Meteor fork</b> of Baritone by class
 * name. The official cabaletta build is ProGuard-obfuscated (its classes are renamed
 * every release), so there is nothing stable to hook; on the wrong - or no - Baritone
 * the mixins simply have no target. Rather than the cryptic Fabric "missing dependency"
 * crash, the mod loads inert and, once in a world, tells the player where to get the
 * right files. It never blocks the game.
 *
 * <p>Deliberately references no Baritone class, so it is safe to use exactly when
 * Baritone is absent.
 */
public final class BaritoneCompat {

	private static final String METEOR_BARITONE_ID = "baritone-meteor";
	private static final String PROJECT_URL = "https://github.com/NyuDev/ElytraEverywhere";

	private BaritoneCompat() {
	}

	/** True when the Meteor fork of Baritone is installed, i.e. the patches can apply. */
	public static boolean isMeteorBaritonePresent() {
		return FabricLoader.getInstance().isModLoaded(METEOR_BARITONE_ID);
	}

	/**
	 * Shows a one-line, non-blocking chat notice the first time the player enters a
	 * world (and again on each later join), pointing at the download page.
	 */
	public static void warnIncompatibleBaritone() {
		final boolean[] wasInWorld = {false};
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			boolean inWorld = client.player != null;
			if (inWorld && !wasInWorld[0]) {
				//? if >=26.1 {
				/*client.player.sendSystemMessage(notice());*/
				//?} else {
				client.player.displayClientMessage(notice(), false);
				//?}
			}
			wasInWorld[0] = inWorld;
		});
	}

	private static Component notice() {
		return Component.literal("[ElytraEverywhere] ").withStyle(ChatFormatting.LIGHT_PURPLE)
				.append(Component.literal("Incompatible Baritone — this addon needs ").withStyle(ChatFormatting.GRAY))
				.append(Component.literal("Meteor's Baritone").withStyle(ChatFormatting.WHITE))
				.append(Component.literal(" (not the official one), so it stays idle. Get the right Baritone + addon for your version:")
						.withStyle(ChatFormatting.GRAY))
				.append(Component.literal("\n  " + PROJECT_URL).withStyle(ChatFormatting.AQUA));
	}
}
