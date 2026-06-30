package fr.nyuway.elytraeverywhere.command;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import fr.nyuway.elytraeverywhere.runtime.ElytraFollowTracker;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Registers {@code /eefollow <player>} and {@code /eefollow stop}.
 *
 * <p>{@code /eefollow <player>} sets up Baritone's {@code FollowProcess} with a player-name
 * filter, just like {@code #follow player <name>} would, and also primes the
 * {@link ElytraFollowTracker} so it takes over as soon as the player starts gliding.
 * While on the ground the existing walking-follow behaviour is unchanged.
 *
 * <p>{@code /eefollow stop} cancels both the follow filter and any active elytra path.
 */
public final class ElytraFollowCommand {

    private final ElytraFollowTracker tracker;

    public ElytraFollowCommand(ElytraFollowTracker tracker) {
        this.tracker = tracker;
    }

    public void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
                dispatcher.register(ClientCommandManager.literal("eefollow")
                        .then(ClientCommandManager.literal("stop")
                                .executes(this::stop))
                        .then(ClientCommandManager.argument("player", StringArgumentType.word())
                                .executes(this::followPlayer))));
    }

    private int followPlayer(CommandContext<FabricClientCommandSource> ctx) {
        final String name = StringArgumentType.getString(ctx, "player");
        final IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        tracker.reset();
        // Register the filter with Baritone's FollowProcess.
        // While on the ground it handles walking; while gliding ElytraFollowTracker drives elytra.
        baritone.getFollowProcess().follow(e -> e instanceof Player && e.getName().getString().equalsIgnoreCase(name));
        ctx.getSource().sendFeedback(
                Component.literal("[ElytraEverywhere] ").withStyle(ChatFormatting.LIGHT_PURPLE)
                        .append(Component.literal("Following ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(name).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" (elytra + walk)").withStyle(ChatFormatting.GRAY)));
        return 1;
    }

    private int stop(CommandContext<FabricClientCommandSource> ctx) {
        final IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        baritone.getFollowProcess().cancel();
        baritone.getPathingBehavior().cancelEverything();
        tracker.reset();
        ctx.getSource().sendFeedback(
                Component.literal("[ElytraEverywhere] ").withStyle(ChatFormatting.LIGHT_PURPLE)
                        .append(Component.literal("Follow stopped").withStyle(ChatFormatting.GRAY)));
        return 1;
    }
}
