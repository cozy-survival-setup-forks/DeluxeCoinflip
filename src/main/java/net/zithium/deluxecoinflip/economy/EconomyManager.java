/*
 * DeluxeCoinflip Plugin
 * Copyright (c) 2021 - 2026 Zithium Studios. All rights reserved.
 */

package net.zithium.deluxecoinflip.economy;

import net.zithium.deluxecoinflip.DeluxeCoinflipPlugin;
import net.zithium.deluxecoinflip.config.ConfigType;
import net.zithium.deluxecoinflip.economy.provider.EconomyProvider;
import net.zithium.deluxecoinflip.economy.provider.impl.*;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

public class EconomyManager {

    private final DeluxeCoinflipPlugin plugin;
    private final Map<String, EconomyProvider> economyProviders;

    public EconomyManager(DeluxeCoinflipPlugin plugin) {
        this.plugin = plugin;
        this.economyProviders = new LinkedHashMap<>();
    }

    /**
     * Load economies
     */
    public void onEnable() {
        economyProviders.clear();

        ConfigurationSection section = plugin.getConfigHandler(ConfigType.CONFIG)
                .getConfig()
                .getConfigurationSection("settings.providers");
        Logger logger = plugin.getLogger();

        if (section == null) {
            logger.severe("There are no enabled providers set in the config. Plugin will now disable.");
            Bukkit.getPluginManager().disablePlugin(plugin);
            return;
        }

        // List of possible providers.
        Map<String, EconomyProvider> possibleProviders = Map.of(
                "Vault", new VaultProvider(),
                "TokenEnchant", new TokenEnchantProvider(),
                "TokenManager", new TokenManagerProvider(),
                "ZithiumMobcoins", new ZithiumMobcoinsProvider(),
                "PlayerPoints", new PlayerPointsProvider(),
                "BeastTokens", new BeastTokensProvider(),
                "CUSTOM_CURRENCY", new CustomCurrencyProvider("CUSTOM_CURRENCY", plugin)
        );

        for (Map.Entry<String, EconomyProvider> entry : possibleProviders.entrySet()) {
            String key = entry.getKey().toUpperCase();
            EconomyProvider provider = entry.getValue();
            ConfigurationSection providerSection = section.getConfigurationSection(key);

            // If no section or disabled, skip
            if (providerSection == null || !providerSection.getBoolean("enabled", false)) continue;

            // Check plugin dependency if needed (except CustomCurrency)
            if (!key.equals("CUSTOM_CURRENCY")) {
                if (plugin.getServer().getPluginManager().getPlugin(key) == null) {
                    logger.warning("Skipping economy provider '" + key + "' (plugin not found).");
                    continue;
                }
            }

            // Optional display name
            if (providerSection.contains("display_currency_name")) {
                provider.setCurrencyDisplayName(providerSection.getString("display_currency_name"));
            }

            economyProviders.put(key, provider);
            provider.onEnable();
            logger.info("Enabled economy provider '" + key + "'.");
        }

        if (economyProviders.isEmpty()) {
            logger.severe("No valid economy providers were enabled. Plugin will now disable.");
            Bukkit.getPluginManager().disablePlugin(plugin);
            return;
        }

        logger.info("Found and using " + String.join(", ", economyProviders.keySet()) + " economy provider(s).");
    }


    /**
     * Register an Economy
     *
     * @param provider       The {@link EconomyProvider}
     * @param requiredPlugin The required plugin in order to work
     */
    public void registerEconomyProvider(EconomyProvider provider, String requiredPlugin) {
        if (requiredPlugin != null) {
            if (plugin.getServer().getPluginManager().getPlugin(requiredPlugin) != null) {
                economyProviders.put(provider.getIdentifier().toUpperCase(), provider);
                plugin.getLogger().info("Registered economy provider '" + provider.getIdentifier() + "' using " + requiredPlugin + " plugin.");
            }
        } else {
            economyProviders.put(provider.getIdentifier().toUpperCase(), provider);
            plugin.getLogger().info("Registered economy provider '" + provider.getIdentifier() + "'");
        }
    }

    /**
     * Fetch an EconomyProvider (if registered and loaded)
     *
     * @param identifier The identifier
     * @return The EconomyProvider if found, otherwise null
     */
    public EconomyProvider getEconomyProvider(String identifier) {
        return economyProviders.get(identifier.toUpperCase());
    }

    /**
     * Get all loaded economies
     *
     * @return Map of String for economy identifier and {@link EconomyProvider} object
     */
    public Map<String, EconomyProvider> getEconomyProviders() {
        return Collections.unmodifiableMap(economyProviders);
    }
}
