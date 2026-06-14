package com.echoesofaegis.companions;

import com.echoesofaegis.companions.command.CompanionCommands;
import com.echoesofaegis.companions.companion.CompanionManager;
import com.echoesofaegis.companions.config.CompanionsConfigManager;
import com.echoesofaegis.companions.data.CompanionDataStore;
import com.echoesofaegis.companions.gui.CompanionStorageGui;
import java.nio.file.Path;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EchoesCompanionsMod implements ModInitializer {
    public static final String MOD_ID = "echoescompanions";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static EchoesCompanionsMod instance;

    private final CompanionsConfigManager configManager = new CompanionsConfigManager();
    private CompanionDataStore dataStore;
    private CompanionManager companionManager;
    private CompanionStorageGui storageGui;

    @Override
    public void onInitialize() {
        instance = this;
        configManager.load();

        companionManager = new CompanionManager(configManager, () -> dataStore);
        storageGui = new CompanionStorageGui(() -> companionManager, () -> dataStore);
        companionManager.setStorageOpener(storageGui::open);

        new CompanionCommands(configManager, () -> companionManager, () -> storageGui).register();
        companionManager.register();

        ServerLifecycleEvents.SERVER_STARTED.register(this::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (dataStore != null) {
                dataStore.save();
            }
        });

        LOGGER.info("Echoes Companions initialized.");
    }

    public static int duelWins(net.minecraft.server.level.ServerPlayer player) {
        return instance == null || instance.dataStore == null ? 0 : instance.dataStore.duelWins(player.getUUID());
    }

    public static int duelLosses(net.minecraft.server.level.ServerPlayer player) {
        return instance == null || instance.dataStore == null ? 0 : instance.dataStore.duelLosses(player.getUUID());
    }

    public static String duelRecord(net.minecraft.server.level.ServerPlayer player) {
        return duelWins(player) + "-" + duelLosses(player);
    }

    private void start(MinecraftServer server) {
        Path root = server.getWorldPath(LevelResource.ROOT).resolve(MOD_ID);
        dataStore = new CompanionDataStore(root.resolve("companions.json"));
        dataStore.load();
        LOGGER.info("Echoes Companions data ready.");
    }
}
