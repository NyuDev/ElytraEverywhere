package fr.nyuway.elytraeverywhere.command;

import com.mojang.brigadier.context.CommandContext;
import fr.nyuway.elytraeverywhere.debug.ChatConsoleMirror;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

/**
 * Registers {@code /eedebug}, which toggles {@link ChatConsoleMirror} (mirroring
 * in-game chat to the console). Parsing only; the behaviour lives in the mirror.
 */
public final class DebugCommand {

	private final ChatConsoleMirror mirror;

	public DebugCommand(ChatConsoleMirror mirror) {
		this.mirror = mirror;
	}

	public void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
				dispatcher.register(ClientCommandManager.literal("eedebug").executes(this::toggle)));
	}

	private int toggle(CommandContext<FabricClientCommandSource> ctx) {
		boolean on = mirror.toggle();
		ctx.getSource().sendFeedback(Text.literal("[ElytraEverywhere] Debug console logging " + (on ? "ON" : "OFF")));
		return 1;
	}
}
