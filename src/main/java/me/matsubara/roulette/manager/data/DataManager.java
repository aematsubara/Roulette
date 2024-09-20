package me.matsubara.roulette.manager.data;

import lombok.Getter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.data.Bet;
import me.matsubara.roulette.game.data.Slot;
import me.matsubara.roulette.game.data.WinData;
import me.matsubara.roulette.util.PluginUtils;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DataManager {

    private final File databaseFile;
    private final @Getter List<RouletteSession> sessions = new ArrayList<>();
    private final @Getter List<MapRecord> maps = new ArrayList<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public DataManager(@NotNull RoulettePlugin plugin) {
        this.databaseFile = new File(plugin.getDataFolder(), "data.db");
        // Create tables and fill sessions.
        CompletableFuture.runAsync(() -> {
            initTables();
            initSessions();
            initMaps();
        }, executor);
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getPath());
    }

    private void initTables() {
        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            // Create a table of roulette sessions.
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS roulette_sessions (" +
                    "roulette_session_uuid BLOB PRIMARY KEY," +
                    "roulette_table_name TEXT NOT NULL," +
                    "winning_slot TEXT NOT NULL," +
                    "session_date BIGINT NOT NULL" +
                    ");");

            // Create a table of player results.
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS player_results (" +
                    "player_uuid BLOB NOT NULL," +
                    "roulette_session_uuid BLOB NOT NULL," +
                    "win TEXT NOT NULL," +
                    "money_involved INTEGER NOT NULL," +
                    "player_slot TEXT NOT NULL," +
                    "FOREIGN KEY(roulette_session_uuid) REFERENCES roulette_sessions(roulette_session_uuid) ON DELETE CASCADE" +
                    ");");

            // Create a table of map ids.
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS roulette_maps (" +
                    "map_id INTEGER NOT NULL," +
                    "player_uuid BLOB NOT NULL," +
                    "roulette_session_uuid BLOB NOT NULL," +
                    "FOREIGN KEY(roulette_session_uuid) REFERENCES roulette_sessions(roulette_session_uuid) ON DELETE CASCADE" +
                    ");");
        } catch (SQLException exception) {
            exception.printStackTrace();
        }
    }

    public RouletteSession saveSession(@NotNull UUID sessionUUID, String name, Collection<Map.Entry<Player, Bet>> bets, @NotNull Slot slot, long timestamp) {
        RouletteSession session = new RouletteSession(sessionUUID, name, slot, timestamp, bets);
        sessions.add(session);

        // Save session and results async.
        CompletableFuture.runAsync(() -> saveSession(session), executor);

        return session;
    }

    private void saveSession(@NotNull RouletteSession session) {
        String sql = "INSERT INTO roulette_sessions (roulette_session_uuid, roulette_table_name, winning_slot, session_date) VALUES (?, ?, ?, ?);";
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, PluginUtils.toBytes(session.sessionUUID()));
            statement.setString(2, session.name());
            statement.setString(3, session.slot().name());
            statement.setLong(4, session.timestamp());
            statement.executeUpdate();

            // After saving session, save results.
            session.results().forEach(this::savePlayerResults);
        } catch (SQLException exception) {
            exception.printStackTrace();
        }
    }

    private void savePlayerResults(@NotNull PlayerResult result) {
        String sql = "INSERT INTO player_results (player_uuid, roulette_session_uuid, win, money_involved, player_slot) VALUES (?, ?, ?, ?, ?)";
        WinData.WinType win = result.win();
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, PluginUtils.toBytes(result.playerUUID()));
            statement.setBytes(2, PluginUtils.toBytes(result.sessionUUID()));
            statement.setString(3, win != null ? win.name() : "DEFEAT");
            statement.setInt(4, (int) (result.money() * 100));
            statement.setString(5, result.slot().name());
            statement.executeUpdate();
        } catch (SQLException exception) {
            exception.printStackTrace();
        }
    }

    public void remove(@NotNull PlayerResult result) {
        RouletteSession session = result.session();

        List<PlayerResult> results = session.results();
        if (!results.contains(result)) return;

        results.remove(result);

        // Remove result async.
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> removePlayerResult(result), executor);
        if (!results.isEmpty()) return;

        // If the session doesn't have any result, then we want to remove the whole session.
        sessions.remove(session);

        // Remove sesion async.
        future.thenRunAsync(() -> {
            removeSession(session);
            removeMap(session);
        }, executor);
    }

    private void removeMap(@NotNull RouletteSession session) {
        String sql = "DELETE FROM roulette_maps WHERE roulette_session_uuid = ?;";
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, PluginUtils.toBytes(session.sessionUUID()));
            statement.executeUpdate();
        } catch (SQLException exception) {
            exception.printStackTrace();
        }
    }

    private void removeSession(@NotNull RouletteSession session) {
        // Since the player can only make 1 bet on a slot, we don't need an ID.
        String sql = "DELETE FROM roulette_sessions WHERE roulette_session_uuid = ?;";
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, PluginUtils.toBytes(session.sessionUUID()));
            statement.executeUpdate();
        } catch (SQLException exception) {
            exception.printStackTrace();
        }
    }

    private void removePlayerResult(@NotNull PlayerResult result) {
        // Since the player can only make 1 bet on a slot, we don't need an ID.
        String sql = "DELETE FROM player_results WHERE player_uuid = ? AND roulette_session_uuid = ? AND player_slot = ?;";
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, PluginUtils.toBytes(result.playerUUID()));
            statement.setBytes(2, PluginUtils.toBytes(result.sessionUUID()));
            statement.setString(3, result.slot().name());
            statement.executeUpdate();
        } catch (SQLException exception) {
            exception.printStackTrace();
        }
    }

    public RouletteSession getSessionByUUID(UUID uuid) {
        for (RouletteSession session : sessions) {
            if (session.sessionUUID().equals(uuid)) return session;
        }
        return null;
    }

    private void initSessions() {
        String sql = "SELECT * FROM roulette_sessions;";
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            ResultSet set = statement.executeQuery();
            while (set.next()) {
                RouletteSession session = new RouletteSession(
                        PluginUtils.toUUID(set.getBytes("roulette_session_uuid")),
                        set.getString("roulette_table_name"),
                        PluginUtils.getOrNull(Slot.class, set.getString("winning_slot")),
                        set.getLong("session_date"));
                session.results().addAll(getPlayerResultsBySession(session, connection));
                sessions.add(session);
            }
        } catch (SQLException exception) {
            exception.printStackTrace();
        }
    }

    private @NotNull List<PlayerResult> getPlayerResultsBySession(@NotNull RouletteSession session, Connection connection) {
        List<PlayerResult> results = new ArrayList<>();

        String sql = "SELECT * FROM player_results WHERE roulette_session_uuid = ?;";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            UUID sessionUUID = session.sessionUUID();
            statement.setBytes(1, PluginUtils.toBytes(sessionUUID));

            ResultSet set = statement.executeQuery();
            while (set.next()) {
                results.add(new PlayerResult(
                        session,
                        PluginUtils.toUUID(set.getBytes("player_uuid")),
                        sessionUUID,
                        PluginUtils.getOrNull(WinData.WinType.class, set.getString("win")),
                        set.getInt("money_involved") / 100.0d,
                        PluginUtils.getOrNull(Slot.class, set.getString("player_slot"))));
            }
        } catch (SQLException exception) {
            exception.printStackTrace();
        }

        return results;
    }

    public void saveMaps(@NotNull List<MapRecord> maps) {
        this.maps.addAll(maps);

        // Save maps async.
        CompletableFuture.runAsync(() -> this.maps.forEach(this::saveMap), executor);
    }

    private void saveMap(@NotNull MapRecord map) {
        String sql = "INSERT INTO roulette_maps (map_id, player_uuid, roulette_session_uuid) VALUES (?, ?, ?)";
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, map.mapId());
            statement.setBytes(2, PluginUtils.toBytes(map.playerUUID()));
            statement.setBytes(3, PluginUtils.toBytes(map.sessionUUID()));
            statement.executeUpdate();
        } catch (SQLException exception) {
            exception.printStackTrace();
        }
    }

    private void initMaps() {
        String sql = "SELECT * FROM roulette_maps;";
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            ResultSet set = statement.executeQuery();
            while (set.next()) {
                maps.add(new MapRecord(
                        set.getInt("map_id"),
                        PluginUtils.toUUID(set.getBytes("player_uuid")),
                        PluginUtils.toUUID(set.getBytes("roulette_session_uuid"))));
            }
        } catch (SQLException exception) {
            exception.printStackTrace();
        }
    }
}