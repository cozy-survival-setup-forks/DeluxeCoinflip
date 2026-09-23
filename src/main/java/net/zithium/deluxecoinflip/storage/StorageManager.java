/*
 * DeluxeCoinflip Plugin
 * Copyright (c) 2021 - 2025 Zithium Studios. All rights reserved.
 */

package net.zithium.deluxecoinflip.storage;

import com.tcoded.folialib.impl.PlatformScheduler;
import net.zithium.deluxecoinflip.DeluxeCoinflipPlugin;
import net.zithium.deluxecoinflip.exception.InvalidStorageHandlerException;
import net.zithium.deluxecoinflip.storage.handler.StorageHandler;
import net.zithium.deluxecoinflip.storage.handler.impl.SQLiteHandler;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StorageManager implements Listener {

    private final DeluxeCoinflipPlugin plugin;
    private final PlatformScheduler scheduler;
    private final Map<UUID, PlayerData> playerDataMap;
    private StorageHandler storageHandler;

    public StorageManager(DeluxeCoinflipPlugin plugin) {
        this.plugin = plugin;
        this.scheduler = DeluxeCoinflipPlugin.scheduler();
        this.playerDataMap = new ConcurrentHashMap<>();
    }

    public void onEnable() {
        final String configuredType = plugin.getConfig().getString("storage.type");
        if ("SQLITE".equalsIgnoreCase(configuredType)) {
            storageHandler = new SQLiteHandler();
        } else {
            throw new InvalidStorageHandlerException("Invalid storage handler specified: " + configuredType);
        }

        if (!storageHandler.onEnable(plugin)) {
            plugin.getServer().getPluginManager().disablePlugin(plugin);
            return;
        }

        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        Bukkit.getOnlinePlayers().forEach(player -> loadPlayerData(player.getUniqueId()));
    }

    public void onDisable(boolean shutdown) {
        if (shutdown && storageHandler != null) {
            storageHandler.onDisable();
        }
    }

    public Optional<PlayerData> getPlayer(UUID uuid) {
        return Optional.ofNullable(playerDataMap.get(uuid));
    }

    public void updateOfflinePlayerWin(UUID uuid, long profit, long beforeTax) {
        scheduler.runAsync(task -> {
            PlayerData playerData = storageHandler.getPlayer(uuid);
            playerData.updateWins();
            playerData.updateProfit(profit);
            playerData.updateGambled(beforeTax);
            storageHandler.savePlayer(playerData);
        });
    }

    public void updateOfflinePlayerLoss(UUID uuid, long beforeTax) {
        scheduler.runAsync(task -> {
            PlayerData playerData = storageHandler.getPlayer(uuid);
            playerData.updateLosses();
            playerData.updateLosses(beforeTax);
            playerData.updateGambled(beforeTax);
            storageHandler.savePlayer(playerData);
        });
    }

    public void updateOfflinePlayerBotWin(UUID uuid) {
        scheduler.runAsync(task -> {
            PlayerData playerData = storageHandler.getPlayer(uuid);
            playerData.updateBotWins();
            storageHandler.savePlayer(playerData);
        });
    }

    public void updateOfflinePlayerBotLoss(UUID uuid) {
        scheduler.runAsync(task -> {
            PlayerData playerData = storageHandler.getPlayer(uuid);
            playerData.updateBotLosses();
            storageHandler.savePlayer(playerData);
        });
    }

    public void loadPlayerData(UUID uuid) {
        scheduler.runAsync(task -> {
            PlayerData data = storageHandler.getPlayer(uuid);
            playerDataMap.put(uuid, data);
        });
    }

    public void savePlayerData(PlayerData player, boolean removeCache) {
        UUID uuid = player.getUUID();
        scheduler.runAsync(task -> {
            storageHandler.savePlayer(player);
            if (removeCache) {
                playerDataMap.remove(uuid);
            }
        });
    }

    public Map<UUID, PlayerData> getPlayerDataMap() {
        return playerDataMap;
    }

    public StorageHandler getStorageHandler() {
        return storageHandler;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        loadPlayerData(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        getPlayer(event.getPlayer().getUniqueId()).ifPresent(data -> savePlayerData(data, true));
    }
}
