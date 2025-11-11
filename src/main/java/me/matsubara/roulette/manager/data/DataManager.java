package me.matsubara.roulette.manager.data;

import com.google.common.base.Predicates;
import lombok.Getter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.file.Config;
import me.matsubara.roulette.game.GameType;
import me.matsubara.roulette.game.data.Bet;
import me.matsubara.roulette.game.data.Slot;
import me.matsubara.roulette.game.data.WinData;
import me.matsubara.roulette.util.PluginUtils;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DataManager {

    private final RoulettePlugin plugin;
    private final File databaseFile;
    private final @Getter List<RouletteSession> sessions = new ArrayList<>();
    private final @Getter List<MapRecord> maps = new ArrayList<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public DataManager(@NotNull RoulettePlugin plugin) {
        this.plugin = plugin;
        this.databaseFile = new File(plugin.getDataFolder(), "data.db");
        // Create tables and fill sessions.
        CompletableFuture.runAsync(() -> {
            initTables();
            initSessions();
            initMaps();
        }, executor);
    }

    private Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getPath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON;");
        }
        return connection;
    }

    private void initTables() {
        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            // Create a table of roulette sessions.
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS roulette_sessions (" +
                    "roulette_session_uuid BLOB PRIMARY KEY," +
                    "roulette_table_name TEXT NOT NULL," +
                    "winning_slot TEXT NOT NULL," +
                    "game_type TEXT NOT NULL," +
                    "session_date BIGINT NOT NULL" +
                    ");");

            // Add new columns since the creation of the database.
            ensureColumn(connection, "roulette_sessions", "game_type", "AMERICAN");

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

    @SuppressWarnings("SameParameterValue")
    private void ensureColumn(Connection connection, String table, String column, String defaultValue) throws SQLException {
        try (ResultSet result = connection.getMetaData().getColumns(null, null, table, column)) {
            if (result.next()) return;

            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " TEXT NOT NULL DEFAULT '" + defaultValue + "'");
                plugin.getLogger().info("Column '" + column + "' not found on table {" + table + "}, creating it!");
            }
        }
    }

    public CompletableFuture<RouletteSession> saveSession(@NotNull UUID sessionUUID, String name, Collection<Map.Entry<Player, Bet>> bets, @NotNull Slot slot, GameType type, long timestamp) {
        RouletteSession session = new RouletteSession(sessionUUID, name, slot, type, timestamp, bets);
        sessions.add(session);
        sort();

        List<RouletteSession> last = handleLimit();

        // Save session and results async.
        return CompletableFuture.supplyAsync(() -> {
            saveSession(session);
            if (last != null) last.forEach(this::removeSession);
            return session;
        }, executor);
    }

    private int getLimit() {
        int limit = Config.SESSIONS_LIMIT.asInt();
        return limit == -1 ? -1 : Math.max(limit, 3);
    }

    private @Nullable List<RouletteSession> handleLimit() {
        int limit = getLimit(), size = sessions.size(), excess = size - limit;
        if (limit == -1 || sessions.isEmpty() || size <= limit) return null;

        List<RouletteSession> last = new ArrayList<>(sessions.subList(Math.max(0, size - excess), size));
        if (!Config.SESSIONS_KEEP_VICTORIES.asBool()) {
            sessions.removeAll(last);
            return last;
        }

        // Ignore all-winning sessions.
        last.removeIf(session -> session.results().stream()
                .noneMatch(Predicates.not(PlayerResult::won)));

        // Remove losing results.
        last.forEach(session -> List.copyOf(session.results()).stream()
                .filter(Predicates.not(PlayerResult::won))
                .forEach(this::remove));

        return null;
    }

    private void saveSession(@NotNull RouletteSession session) {
        String sql = "INSERT INTO roulette_sessions (roulette_session_uuid, roulette_table_name, winning_slot, game_type, session_date) VALUES (?, ?, ?, ?, ?);";
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, PluginUtils.toBytes(session.sessionUUID()));
            statement.setString(2, session.name());
            statement.setString(3, session.slot().name());
            statement.setString(4, session.type().name());
            statement.setLong(5, session.timestamp());
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
        if (!results.isEmpty()) {
            if (results.stream()
                    .filter(temp -> temp.playerUUID().equals(result.playerUUID()))
                    .anyMatch(PlayerResult::won)) return;
            removeMap(new MapRecord(0, result.playerUUID(), result.sessionUUID()));
            return;
        }

        // If the session doesn't have any result, then we want to remove the whole session.
        sessions.remove(session);

        // Remove sesion async.
        future.thenRunAsync(() -> removeSession(session), executor);
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
                        PluginUtils.getOrDefault(Slot.class, set.getString("winning_slot"), Slot.SLOT_0),
                        PluginUtils.getOrDefault(GameType.class, set.getString("game_type"), GameType.AMERICAN),
                        set.getLong("session_date"));
                session.results().addAll(getPlayerResultsBySession(session, connection));
                sessions.add(session);
            }
        } catch (SQLException exception) {
            exception.printStackTrace();
        }

        sort();
    }

    private void sort() {
        // Sort from new to old.
        sessions.sort((first, second) -> Long.compare(second.timestamp(), first.timestamp()));
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
        CompletableFuture.runAsync(() -> maps.forEach(this::saveMap), executor);
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

    private void removeMap(@NotNull MapRecord map) {
        String sql = "DELETE FROM roulette_maps WHERE player_uuid = ? AND roulette_session_uuid = ?;";
        try (Connection connection = getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBytes(1, PluginUtils.toBytes(map.playerUUID()));
            statement.setBytes(2, PluginUtils.toBytes(map.sessionUUID()));
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