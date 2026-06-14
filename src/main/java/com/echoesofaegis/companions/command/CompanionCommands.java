package com.echoesofaegis.companions.command;

import com.echoesofaegis.companions.companion.CompanionManager;
import com.echoesofaegis.companions.config.CompanionsConfigManager;
import com.echoesofaegis.companions.data.CompanionMode;
import com.echoesofaegis.companions.data.CompanionRole;
import com.echoesofaegis.companions.gui.CompanionStorageGui;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.Mob;

public final class CompanionCommands {
    private final CompanionsConfigManager configManager;
    private final Supplier<CompanionManager> manager;
    private final Supplier<CompanionStorageGui> storageGui;
    private final Map<UUID, DuelRequest> duelRequests = new HashMap<>();

    public CompanionCommands(CompanionsConfigManager configManager, Supplier<CompanionManager> manager, Supplier<CompanionStorageGui> storageGui) {
        this.configManager = configManager;
        this.manager = manager;
        this.storageGui = storageGui;
    }

    public void register() {
        CommandRegistrationCallback.EVENT.register(this::register);
    }

    private void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment) {
        dispatcher.register(root("companions"));
        dispatcher.register(root("ecompanions"));
    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> root(String name) {
        return Commands.literal(name)
                .executes(this::help)
                .then(Commands.literal("summon")
                        .requires(this::canAdmin)
                        .executes(this::summon))
                .then(Commands.literal("recall").executes(this::recall))
                .then(Commands.literal("storage").executes(this::storage))
                .then(Commands.literal("whistle").executes(this::whistle))
                .then(Commands.literal("egg").executes(this::egg))
                .then(Commands.literal("bed")
                        .executes(this::bed)
                        .then(Commands.literal("clear").executes(this::clearBed))
                        .then(Commands.argument("color", StringArgumentType.word())
                                .suggests(this::suggestBedColors)
                                .executes(this::bedColor)))
                .then(Commands.literal("duel")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(this::duel)))
                .then(Commands.literal("follow").executes(context -> setMode(context, CompanionMode.FOLLOW)))
                .then(Commands.literal("stay").executes(context -> setMode(context, CompanionMode.STAY)))
                .then(Commands.literal("mode")
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests(this::suggestModes)
                                .executes(this::mode)))
                .then(Commands.literal("role")
                        .then(Commands.argument("role", StringArgumentType.word())
                                .suggests(this::suggestRoles)
                                .executes(this::role)))
                .then(Commands.literal("reload")
                        .requires(this::canAdmin)
                        .executes(this::reload));
    }

    private int help(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("Echoes Companions: /companions recall, /companions storage, /companions role <role>, /companions whistle, /companions bed, /companions egg")
                .withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private int summon(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        if (player == null) {
            context.getSource().sendFailure(Component.literal("Run this command as a player."));
            return 0;
        }
        Mob companion = manager.get().summonWild(player);
        if (companion == null) {
            context.getSource().sendFailure(Component.literal("Could not summon a tiny companion here."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Summoned a wild Little Wanderer. Drop food near it to tame it.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private int recall(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        if (player == null) {
            context.getSource().sendFailure(Component.literal("Run this command as a player."));
            return 0;
        }
        return manager.get().recallOwned(player).isPresent() ? 1 : 0;
    }

    private int storage(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        if (player == null) {
            context.getSource().sendFailure(Component.literal("Run this command as a player."));
            return 0;
        }
        return nearest(player, 12.0D).map(companion -> {
            storageGui.get().open(player, companion);
            return 1;
        }).orElseGet(() -> {
            player.sendSystemMessage(Component.literal("No owned companion close enough for storage.").withStyle(ChatFormatting.RED));
            return 0;
        });
    }

    private int egg(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        if (player == null) {
            context.getSource().sendFailure(Component.literal("Run this command as a player."));
            return 0;
        }
        return manager.get().reissueRespawnEgg(player);
    }

    private int whistle(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        if (player == null) {
            context.getSource().sendFailure(Component.literal("Run this command as a player."));
            return 0;
        }
        return manager.get().giveCompanionWhistle(player);
    }

    private int bed(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        if (player == null) {
            context.getSource().sendFailure(Component.literal("Run this command as a player."));
            return 0;
        }
        return manager.get().giveCompanionBed(player);
    }

    private int bedColor(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        if (player == null) {
            context.getSource().sendFailure(Component.literal("Run this command as a player."));
            return 0;
        }
        return manager.get().giveCompanionBed(player, StringArgumentType.getString(context, "color"));
    }

    private int clearBed(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        if (player == null) {
            context.getSource().sendFailure(Component.literal("Run this command as a player."));
            return 0;
        }
        return manager.get().clearOwnedBed(player);
    }

    private int mode(CommandContext<CommandSourceStack> context) {
        String mode = StringArgumentType.getString(context, "mode");
        try {
            return setMode(context, CompanionMode.valueOf(mode.toUpperCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.literal("Mode must be follow or stay."));
            return 0;
        }
    }

    private int role(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        if (player == null) {
            context.getSource().sendFailure(Component.literal("Run this command as a player."));
            return 0;
        }
        CompanionRole role = CompanionRole.parse(StringArgumentType.getString(context, "role"));
        return nearest(player, 32.0D).map(companion -> {
            manager.get().setRole(player, companion, role);
            return 1;
        }).orElseGet(() -> {
            player.sendSystemMessage(Component.literal("No owned companion found nearby.").withStyle(ChatFormatting.RED));
            return 0;
        });
    }

    private int duel(CommandContext<CommandSourceStack> context) {
        ServerPlayer challenger = player(context);
        if (challenger == null) {
            context.getSource().sendFailure(Component.literal("Run this command as a player."));
            return 0;
        }
        ServerPlayer target;
        try {
            target = EntityArgument.getPlayer(context, "player");
        } catch (Exception exception) {
            context.getSource().sendFailure(Component.literal("That player is not online."));
            return 0;
        }
        if (target.getUUID().equals(challenger.getUUID())) {
            challenger.sendSystemMessage(Component.literal("You cannot duel your own companion.").withStyle(ChatFormatting.RED));
            return 0;
        }
        long now = System.currentTimeMillis();
        DuelRequest incoming = duelRequests.get(challenger.getUUID());
        if (incoming != null && incoming.challenger.equals(target.getUUID()) && now - incoming.createdAt <= 60_000L) {
            duelRequests.remove(challenger.getUUID());
            duelRequests.remove(target.getUUID());
            return manager.get().startDuel(challenger, target) ? 1 : 0;
        }
        duelRequests.put(target.getUUID(), new DuelRequest(challenger.getUUID(), now));
        challenger.sendSystemMessage(Component.literal("Sent a companion duel request to " + target.getGameProfile().name() + ".").withStyle(ChatFormatting.GOLD));
        target.sendSystemMessage(Component.literal(challenger.getGameProfile().name() + " challenged your companion. Use /companions duel " + challenger.getGameProfile().name() + " to accept.").withStyle(ChatFormatting.GOLD));
        return 1;
    }

    private int setMode(CommandContext<CommandSourceStack> context, CompanionMode mode) {
        ServerPlayer player = player(context);
        if (player == null) {
            context.getSource().sendFailure(Component.literal("Run this command as a player."));
            return 0;
        }
        return nearest(player, 32.0D).map(companion -> {
            manager.get().setMode(player, companion, mode);
            return 1;
        }).orElseGet(() -> {
            player.sendSystemMessage(Component.literal("No owned companion found nearby.").withStyle(ChatFormatting.RED));
            return 0;
        });
    }

    private int reload(CommandContext<CommandSourceStack> context) {
        configManager.load();
        context.getSource().sendSuccess(() -> Component.literal("Reloaded Echoes Companions config.").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private java.util.Optional<Mob> nearest(ServerPlayer player, double radius) {
        return manager.get().findNearestOwned(player, radius);
    }

    private ServerPlayer player(CommandContext<CommandSourceStack> context) {
        try {
            return context.getSource().getPlayerOrException();
        } catch (Exception ignored) {
            return null;
        }
    }

    private CompletableFuture<Suggestions> suggestModes(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        builder.suggest("follow");
        builder.suggest("stay");
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestRoles(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        builder.suggest("defender");
        builder.suggest("scout");
        builder.suggest("passive");
        builder.suggest("aggressive");
        builder.suggest("stay");
        builder.suggest("guard_claim");
        return builder.buildFuture();
    }

    private CompletableFuture<Suggestions> suggestBedColors(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        for (String color : manager.get().bedColors()) {
            builder.suggest(color);
        }
        return builder.buildFuture();
    }

    private boolean canAdmin(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return true;
        }
        NameAndId identity = new NameAndId(player.getGameProfile());
        return source.getServer().isSingleplayerOwner(identity)
                || source.getServer().getPlayerList().isOp(identity);
    }

    private record DuelRequest(UUID challenger, long createdAt) {
    }
}

