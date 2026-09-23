/*
 * DeluxeCoinflip Plugin
 * Copyright (c) 2021 - 2025 Zithium Studios. All rights reserved.
 */

package net.zithium.deluxecoinflip.config;

import net.zithium.deluxecoinflip.utility.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public enum Messages {

    PREFIX("general.prefix"),
    RELOAD("general.reload"),
    NO_PERMISSION("general.no-permission"),
    HELP_DEFAULT("general.help_default"),
    HELP_ADMIN("general.help_admin"),

    BROADCASTS_TOGGLED_ON("coinflip.toggle_broadcasts_on"),
    BROADCASTS_TOGGLED_OFF("coinflip.toggle_broadcasts_off"),
    GAME_NOT_FOUND("coinflip.game_not_found"),
    CREATED_GAME("coinflip.created_coinflip"),
    DELETED_GAME("coinflip.deleted_coinflip"),
    INSUFFICIENT_FUNDS("coinflip.insufficient-funds"),
    CREATE_MINIMUM_AMOUNT("coinflip.minimum-amount"),
    CREATE_MAXIMUM_AMOUNT("coinflip.maximum-amount"),
    GAME_ACTIVE("coinflip.coinflip-active"),
    PLAYER_CHALLENGE("coinflip.player-challenged-you"),
    COINFLIP_BROADCAST("coinflip.broadcast-coinflip"),
    COINFLIP_CREATED_BROADCAST("coinflip.broadcast-created-coinflip"),

    ERROR_GAME_UNAVAILABLE("coinflip.game-unavailable"),
    ERROR_COINFLIP_SELF("coinflip.cant-coinflip-self"),
    ENTER_VALUE_FOR_GAME("coinflip.enter-value"),
    CHAT_CANCELLED("coinflip.chat-cancelled"),
    INVALID_CURRENCY("coinflip.invalid-currency"),
    INVALID_AMOUNT("coinflip.invalid-amount"),

    GAME_FORFEIT("coinflip.summary-forfeit"),
    GAME_REFUNDED("coinflip.refunded"),
    GAME_SUMMARY_LOSS("coinflip.summary-loss"),
    GAME_SUMMARY_WIN("coinflip.summary-win"),

    BOT_GAME_LOSS("coinflip.bot-summary-loss"),
    BOT_GAME_WIN("coinflip.bot-summary-win");

    private static FileConfiguration config;

    private final String path;

    Messages(String path) {
        this.path = path;
    }

    public static void setConfiguration(FileConfiguration c) {
        config = c;
    }

    public void broadcast(Object... replacements) {
        if (config == null) {
            return;
        }

        Bukkit.getOnlinePlayers().forEach(player -> send(player, replacements));
    }

    public void send(CommandSender receiver, Object... replacements) {
        if (config == null || receiver == null) {
            return;
        }

        Object value = config.get(this.path);

        String message;
        if (value == null) {
            message = "DeluxeCoinflip: message not found (" + this.path + ")";
        } else if (value instanceof List) {
            List<String> lines = config.getStringList(this.path);
            message = TextUtil.fromList(lines);
        } else {
            message = String.valueOf(value);
        }

        if (message == null || message.isEmpty()) {
            return;
        }

        String colored = TextUtil.color(replace(message, replacements));
        if (colored == null || colored.isEmpty()) {
            return;
        }

        receiver.sendMessage(colored);
    }

    private String replace(String message, Object... replacements) {
        if (message == null) {
            return "";
        }

        if (replacements != null) {
            for (int i = 0; i + 1 < replacements.length; i += 2) {
                String key = String.valueOf(replacements[i]);
                String val = String.valueOf(replacements[i + 1]);
                if (key != null && !key.isEmpty()) {
                    message = message.replace(key, val != null ? val : "");
                }
            }
        }

        if (config != null) {
            String prefix = config.getString(PREFIX.getPath());
            message = message.replace("{PREFIX}", (prefix != null && !prefix.isEmpty()) ? prefix : "");
        }

        return message;
    }

    public String getPath() {
        return this.path;
    }
}
