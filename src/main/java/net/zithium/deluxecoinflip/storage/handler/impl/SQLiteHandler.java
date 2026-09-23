/*
 * DeluxeCoinflip Plugin
 * Copyright (c) 2021 - 2025 Zithium Studios. All rights reserved.
 */

package net.zithium.deluxecoinflip.storage.handler.impl;

import net.zithium.deluxecoinflip.DeluxeCoinflipPlugin;
import net.zithium.deluxecoinflip.game.CoinflipGame;
import net.zithium.deluxecoinflip.storage.PlayerData;
import net.zithium.deluxecoinflip.storage.handler.StorageHandler;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class SQLiteHandler implements StorageHandler {

    private DeluxeCoinflipPlugin plugin;
    private File file;

    @Override
    public boolean onEnable(final DeluxeCoinflipPlugin plugin) {
        this.plugin = plugin;

        if (!plugin.getDataFolder().exists()) {
            boolean made = plugin.getDataFolder().mkdirs();
            if (!made && !plugin.getDataFolder().exists()) {
                plugin.getLogger().severe("Could not create plugin data folder: " + plugin.getDataFolder().getAbsolutePath());
                return false;
            }
        }

        file = new File(plugin.getDataFolder(), "database.db");
        if (!file.exists()) {
            try {
                boolean created = file.createNewFile();
                if (!created && !file.exists()) {
                    plugin.getLogger().severe("Could not create database file: " + file.getAbsolutePath());
                    return false;
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Error occurred while creating the database file.", e);
                return false;
            }
        }

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "SQLite JDBC driver not found.", e);
            return false;
        }

        createTable();
        addBotColumnsIfMissing();
        return true;
    }

    @Override
    public void onDisable() {
        plugin.getLogger().info("Saving player data to database...");

        Map<UUID, PlayerData> playerDataMap = DeluxeCoinflipPlugin.getInstance().getStorageManager().getPlayerDataMap();

        try (Connection c = getConnection()) {
            c.setAutoCommit(false);
            String sql = "REPLACE INTO players (uuid, wins, losses, profit, total_loss, total_gambled, broadcasts, bot_wins, bot_losses) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);";
            try (PreparedStatement preparedStatement = c.prepareStatement(sql)) {
                for (PlayerData player : new ArrayList<>(playerDataMap.values())) {
                    preparedStatement.setString(1, player.getUUID().toString());
                    preparedStatement.setInt(2, player.getWins());
                    preparedStatement.setInt(3, player.getLosses());
                    preparedStatement.setLong(4, player.getProfit());
                    preparedStatement.setLong(5, player.getTotalLosses());
                    preparedStatement.setLong(6, player.getTotalGambled());
                    preparedStatement.setBoolean(7, player.isDisplayBroadcastMessages());
                    preparedStatement.setInt(8, player.getBotWins());
                    preparedStatement.setInt(9, player.getBotLosses());
                    preparedStatement.addBatch();
                }

                preparedStatement.executeBatch();
            }
            c.commit();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error occurred while saving player data on shutdown.", e);
        } finally {
            playerDataMap.clear();
        }
    }

    public Connection getConnection() {
        try {
            return DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error occurred while setting up the database connection.", ex);
            throw new IllegalStateException("Unable to open SQLite connection", ex);
        }
    }

    private void createTable() {
        try (Connection tableConnection = getConnection();
             Statement statement = tableConnection.createStatement()) {
            String TABLE_NAME = "players";
            String createPlayersTable = "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    "uuid VARCHAR(255) NOT NULL PRIMARY KEY, " +
                    "wins INTEGER, " +
                    "losses INTEGER, " +
                    "profit BIGINT," +
                    "total_loss BIGINT," +
                    "total_gambled BIGINT," +
                    "broadcasts BOOLEAN," +
                    "bot_wins INTEGER DEFAULT 0," +
                    "bot_losses INTEGER DEFAULT 0);";
            statement.execute(createPlayersTable);

            String createGamesTable = "CREATE TABLE IF NOT EXISTS games (" +
                    "uuid VARCHAR(255) NOT NULL PRIMARY KEY, " +
                    "provider VARCHAR(255)," +
                    "amount BIGINT);";
            statement.execute(createGamesTable);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error occurred while creating database tables.", e);
        }
    }

    /** Adds bot_wins/bot_losses for installs upgrading from before Play with Bot existed. Runs after
     * createTable(), so the table is guaranteed to already exist by the time this checks for the columns. */
    private void addBotColumnsIfMissing() {
        try (Connection connection = getConnection()) {
            if (connection.getMetaData().getColumns(null, null, "players", "bot_wins").next()) {
                return;
            }

            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE players ADD bot_wins INTEGER DEFAULT 0");
                statement.execute("ALTER TABLE players ADD bot_losses INTEGER DEFAULT 0");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error occurred while adding bot game columns.", e);
        }
    }

    @Override
    public PlayerData getPlayer(final UUID uuid) {
        String sql = "SELECT wins, losses, profit, total_loss, total_gambled, broadcasts, bot_wins, bot_losses FROM players WHERE uuid = ?;";
        try (Connection playerConnection = getConnection();
             PreparedStatement preparedStatement = playerConnection.prepareStatement(sql)) {
            preparedStatement.setString(1, uuid.toString());
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    PlayerData playerData = new PlayerData(uuid);
                    playerData.setWins(resultSet.getInt("wins"));
                    playerData.setLosses(resultSet.getInt("losses"));
                    playerData.setProfit(resultSet.getLong("profit"));
                    playerData.setTotalLosses(resultSet.getLong("total_loss"));
                    playerData.setTotalGambled(resultSet.getLong("total_gambled"));
                    playerData.setDisplayBroadcastMessages(resultSet.getBoolean("broadcasts"));
                    playerData.setBotWins(resultSet.getInt("bot_wins"));
                    playerData.setBotLosses(resultSet.getInt("bot_losses"));

                    return playerData;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error occurred while attempting to get a player's data.", e);
        }

        return new PlayerData(uuid);
    }

    @Override
    public void savePlayer(final PlayerData player) {
        String sql = "REPLACE INTO players (uuid, wins, losses, profit, total_loss, total_gambled, broadcasts, bot_wins, bot_losses) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);";
        try (Connection playerConnection = getConnection();
             PreparedStatement preparedStatement = playerConnection.prepareStatement(sql)) {
            preparedStatement.setString(1, player.getUUID().toString());
            preparedStatement.setInt(2, player.getWins());
            preparedStatement.setInt(3, player.getLosses());
            preparedStatement.setLong(4, player.getProfit());
            preparedStatement.setLong(5, player.getTotalLosses());
            preparedStatement.setLong(6, player.getTotalGambled());
            preparedStatement.setBoolean(7, player.isDisplayBroadcastMessages());
            preparedStatement.setInt(8, player.getBotWins());
            preparedStatement.setInt(9, player.getBotLosses());
            preparedStatement.execute();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error occurred while attempting to save a player's data.", e);
        }
    }

    @Override
    public void saveCoinflip(CoinflipGame game) {
        String sql = "REPLACE INTO games (uuid, provider, amount) VALUES (?, ?, ?);";
        try (Connection coinflipConnection = getConnection();
             PreparedStatement preparedStatement = coinflipConnection.prepareStatement(sql)) {
            preparedStatement.setString(1, game.getPlayerUUID().toString());
            preparedStatement.setString(2, game.getProvider());
            preparedStatement.setLong(3, game.getAmount());
            preparedStatement.execute();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error occurred while attempting to save a coinflip game.", e);
        }
    }

    @Override
    public void deleteCoinflip(UUID uuid) {
        String sql = "DELETE FROM games WHERE uuid = ?;";
        try (Connection coinflipConnection = getConnection();
             PreparedStatement preparedStatement = coinflipConnection.prepareStatement(sql)) {
            preparedStatement.setString(1, uuid.toString());
            preparedStatement.execute();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error occurred while attempting to delete a coinflip game.", e);
        }
    }

    @Override
    public Map<UUID, CoinflipGame> getGames() {
        Map<UUID, CoinflipGame> games = new HashMap<>();
        String sql = "SELECT uuid, provider, amount FROM games;";
        try (Connection gamesConnection = getConnection();
             PreparedStatement preparedStatement = gamesConnection.prepareStatement(sql);
             ResultSet resultSet = preparedStatement.executeQuery()) {
            while (resultSet.next()) {
                UUID uuid = UUID.fromString(resultSet.getString("uuid"));
                String provider = resultSet.getString("provider");
                long amount = resultSet.getLong("amount");
                games.put(uuid, new CoinflipGame(uuid, provider, amount));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error occurred while attempting to get all coinflip games.", e);
        }

        return games;
    }

    @Override
    public CoinflipGame getCoinflipGame(@NotNull UUID uuid) {
        final String sql = "SELECT provider, amount FROM games WHERE uuid = ?;";
        try (Connection gameConnection = getConnection();
             PreparedStatement preparedStatement = gameConnection.prepareStatement(sql)) {
            preparedStatement.setString(1, uuid.toString());
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }

                final String provider = resultSet.getString("provider");
                final long amount = resultSet.getLong("amount");
                return new CoinflipGame(uuid, provider, amount);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error occurred while attempting to get a coinflip game.", e);
            return null;
        }
    }
}
