/*
 * DeluxeCoinflip Plugin
 * Copyright (c) 2021 - 2026 Zithium Studios. All rights reserved.
 */

package net.zithium.deluxecoinflip.menu.inventories;

import com.tcoded.folialib.impl.PlatformScheduler;
import com.tcoded.folialib.wrapper.task.WrappedTask;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import net.kyori.adventure.text.Component;
import net.zithium.deluxecoinflip.DeluxeCoinflipPlugin;
import net.zithium.deluxecoinflip.config.ConfigType;
import net.zithium.deluxecoinflip.config.Messages;
import net.zithium.deluxecoinflip.economy.EconomyManager;
import net.zithium.deluxecoinflip.economy.provider.EconomyProvider;
import net.zithium.deluxecoinflip.game.CoinflipGame;
import net.zithium.deluxecoinflip.storage.PlayerData;
import net.zithium.deluxecoinflip.storage.StorageManager;
import net.zithium.deluxecoinflip.utility.ItemStackBuilder;
import net.zithium.deluxecoinflip.utility.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Play with Bot: a solo game against the house instead of another player. No listing, resolves instantly, and
 * keeps its own win/loss count separate from PvP stats so one doesn't inflate the other.
 */
public class BotGameGUI {

    private static final int ANIMATION_COUNT_THRESHOLD = 12;

    private final DeluxeCoinflipPlugin plugin;
    private final PlatformScheduler scheduler;
    private final EconomyManager economyManager;
    private final FileConfiguration config;
    private final String title;

    public BotGameGUI(@NotNull DeluxeCoinflipPlugin plugin) {
        this.plugin = plugin;
        this.scheduler = DeluxeCoinflipPlugin.scheduler();
        this.economyManager = plugin.getEconomyManager();
        this.config = plugin.getConfigHandler(ConfigType.CONFIG).getConfig();
        this.title = TextUtil.color(config.getString("coinflip-gui.title", "&lFLIPPING COIN..."));
    }

    /** The bet has already been withdrawn from the player, and the game registered in the active-games
     * cache, by the caller before this runs. */
    public void startGame(@NotNull Player player, @NotNull CoinflipGame game) {
        double winChance = config.getDouble("settings.bot.win-chance", 50.0);
        double payoutMultiplier = config.getDouble("settings.bot.payout-multiplier", 2.0);
        SecureRandom random = new SecureRandom();
        boolean win = random.nextDouble() * 100.0 < winChance;
        long payout = Math.round(game.getAmount() * payoutMultiplier);

        Gui gui = Gui.gui().rows(3).title(Component.text(title)).create();
        gui.disableAllInteractions();

        GuiItem playerHead = new GuiItem(new ItemStackBuilder(game.getCachedHead())
                .withName(TextUtil.color("&e" + player.getName())).build());
        GuiItem botHead = new GuiItem(new ItemStackBuilder(Material.ZOMBIE_HEAD)
                .withName(TextUtil.color("&7Bot")).build());
        GuiItem winnerHead = win ? playerHead : botHead;

        UUID playerId = player.getUniqueId();

        scheduler.runAtEntity(player, task -> {
            Player current = Bukkit.getPlayer(playerId);
            if (current != null) {
                gui.open(current);
            }

            new AnimationLoop(gui, playerHead, botHead, winnerHead, playerId, game, win, payout).accept(null);
        });
    }

    private class AnimationLoop implements Consumer<WrappedTask> {

        private final Gui gui;
        private final GuiItem playerHead;
        private final GuiItem botHead;
        private final GuiItem winnerHead;
        private final UUID playerId;
        private final CoinflipGame game;
        private final boolean win;
        private final long payout;
        private final AtomicBoolean finished = new AtomicBoolean(false);

        private boolean alternate = false;
        private int count = 0;

        AnimationLoop(Gui gui, GuiItem playerHead, GuiItem botHead, GuiItem winnerHead, UUID playerId,
                     CoinflipGame game, boolean win, long payout) {
            this.gui = gui;
            this.playerHead = playerHead;
            this.botHead = botHead;
            this.winnerHead = winnerHead;
            this.playerId = playerId;
            this.game = game;
            this.win = win;
            this.payout = payout;
        }

        @Override
        public void accept(WrappedTask task) {
            if (finished.get()) {
                return;
            }

            Player player = Bukkit.getPlayer(playerId);
            if (player == null) {
                // Disconnected mid-animation: resolve immediately instead of continuing to schedule
                // against an entity that's no longer here (the class of bug that used to leave a
                // paid-for game stuck unresolved when a player quit or was banned mid-flip).
                resolve(finished, playerId, game, win, payout);
                return;
            }

            if (count++ >= ANIMATION_COUNT_THRESHOLD) {
                gui.setItem(13, winnerHead);
                gui.getFiller().fill(new GuiItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE));
                gui.disableAllInteractions();
                gui.update();

                playConfiguredSound(player, "coinflip-gui.sounds.animation_complete", Sound.ENTITY_PLAYER_LEVELUP);
                scheduler.runAtEntityLater(player, innerTask -> {
                    Player stillOnline = Bukkit.getPlayer(playerId);
                    if (stillOnline != null) {
                        stillOnline.closeInventory();
                    }
                }, 20L);

                resolve(finished, playerId, game, win, payout);
                return;
            }

            gui.setItem(13, alternate ? playerHead : botHead);
            GuiItem filler = new GuiItem(new org.bukkit.inventory.ItemStack(
                    alternate ? Material.YELLOW_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE));
            for (int i = 0; i < gui.getInventory().getSize(); i++) {
                if (i != 13) gui.setItem(i, filler);
            }
            alternate = !alternate;

            if (player.getOpenInventory().getTopInventory().equals(gui.getInventory())) {
                playConfiguredSound(player, "coinflip-gui.sounds.animation_tick", Sound.BLOCK_WOODEN_BUTTON_CLICK_ON);
                gui.update();
            }

            scheduler.runAtEntityLater(player, this, 10L);
        }
    }

    /** Runs on the calling entity's own thread; only the economy deposit needs the global-region
     * {@code runNextTick} hop, everything else (stats, messages) stays here. */
    private void resolve(AtomicBoolean finished, UUID playerId, CoinflipGame game, boolean win, long payout) {
        if (!finished.compareAndSet(false, true)) {
            return;
        }

        plugin.getActiveGamesCache().unregister(game);

        EconomyProvider provider = economyManager.getEconomyProvider(game.getProvider());
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
        String stakeFormatted = TextUtil.numberFormat(game.getAmount());

        if (win && provider != null) {
            scheduler.runNextTick(task -> provider.deposit(offlinePlayer, payout));
        }

        updateStats(playerId, win);

        Player online = Bukkit.getPlayer(playerId);
        if (online != null) {
            if (win) {
                Messages.BOT_GAME_WIN.send(online, "{STAKE}", stakeFormatted, "{WINNINGS}", TextUtil.numberFormat(payout));
            } else {
                Messages.BOT_GAME_LOSS.send(online, "{STAKE}", stakeFormatted);
            }
        }
    }

    private void updateStats(UUID playerId, boolean win) {
        StorageManager storageManager = plugin.getStorageManager();
        Optional<PlayerData> optionalPlayerData = storageManager.getPlayer(playerId);
        if (optionalPlayerData.isPresent()) {
            PlayerData playerData = optionalPlayerData.get();
            if (win) playerData.updateBotWins();
            else playerData.updateBotLosses();
        } else if (win) {
            storageManager.updateOfflinePlayerBotWin(playerId);
        } else {
            storageManager.updateOfflinePlayerBotLoss(playerId);
        }
    }

    private void playConfiguredSound(Player player, String path, Sound def) {
        var section = config.getConfigurationSection(path);
        if (section == null || !section.getBoolean("enabled", true)) {
            return;
        }

        String name = section.getString("name", def.name());
        Sound chosen;
        try {
            chosen = Sound.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException ex) {
            chosen = def;
        }

        float vol = (float) section.getDouble("volume", 1.0);
        float pitch = (float) section.getDouble("pitch", 1.0);
        player.playSound(player.getLocation(), chosen, vol, pitch);
    }
}
