/*
 * DeluxeCoinflip Plugin
 * Copyright (c) 2021 - 2026 Zithium Studios. All rights reserved.
 */

package net.zithium.deluxecoinflip.game;

import com.tcoded.folialib.impl.PlatformScheduler;
import net.zithium.deluxecoinflip.DeluxeCoinflipPlugin;
import net.zithium.deluxecoinflip.utility.ItemStackBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class CoinflipGame implements Cloneable {

    private final PlatformScheduler scheduler;
    private final UUID uuid;
    private OfflinePlayer player;
    private String provider;
    private long amount;
    private ItemStack cachedHead;

    private transient volatile boolean activeGame = false;
    private transient volatile UUID opponent;

    public CoinflipGame(UUID uuid, String provider, long amount) {
        this.scheduler = DeluxeCoinflipPlugin.scheduler();
        this.uuid = uuid;
        this.provider = provider;
        this.amount = amount;
        this.cachedHead = new ItemStack(Material.PLAYER_HEAD);

        scheduler.runAsync(task -> {
            this.player = Bukkit.getOfflinePlayer(uuid);
            this.cachedHead = new ItemStackBuilder(Material.PLAYER_HEAD).setSkullOwner(player).build();
        });
    }

    public CoinflipGame(UUID uuid, String provider, long amount, OfflinePlayer player, ItemStack cachedHead) {
        this.scheduler = DeluxeCoinflipPlugin.scheduler();
        this.uuid = uuid;
        this.provider = provider;
        this.amount = amount;
        this.cachedHead = cachedHead;
        this.player = player;
    }

    public UUID getPlayerUUID() {
        return uuid;
    }

    public @Nullable UUID getOpponentUUID() {
        return opponent;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = Math.max(amount, 0);
    }

    public OfflinePlayer getOfflinePlayer() {
        return player;
    }

    public ItemStack getCachedHead() {
        return cachedHead != null ? cachedHead.clone() : new ItemStack(Material.PLAYER_HEAD);
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    @Override
    public CoinflipGame clone() {
        try {
            CoinflipGame copy = (CoinflipGame) super.clone();
            copy.cachedHead = (this.cachedHead != null) ? this.cachedHead.clone() : null;
            return copy;
        } catch (CloneNotSupportedException ex) {
            throw new AssertionError(ex);
        }
    }

    public boolean isActiveGame() {
        return activeGame;
    }

    public void setActiveGame(boolean activeGame) {
        this.activeGame = activeGame;
    }

    public void attachOpponent(@NotNull UUID opponent) {
        this.opponent = opponent;
    }
}
