package me.matsubara.roulette.manager;

import com.github.juliarn.npc.NPC;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.GameRule;
import me.matsubara.roulette.game.GameType;
import me.matsubara.roulette.model.Model;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class GameManager {

    private final RoulettePlugin plugin;
    private final List<Game> games;

    private File file;
    private FileConfiguration configuration;

    public GameManager(RoulettePlugin plugin) {
        this.plugin = plugin;
        this.games = new ArrayList<>();
        load();
    }

    private void load() {
        file = new File(plugin.getDataFolder(), "games.yml");
        if (!file.exists()) {
            plugin.saveResource("games.yml", false);
        }
        configuration = new YamlConfiguration();
        try {
            configuration.load(file);
            update();
        } catch (IOException | InvalidConfigurationException exception) {
            exception.printStackTrace();
        }
    }

    public void add(
            String name,
            @Nullable String npcName,
            @Nullable String npcTexture,
            @Nullable String npcSignature,
            int minPlayers,
            int maxPlayers,
            GameType type,
            UUID modelId,
            Location location,
            UUID owner,
            int startTime,
            @Nullable UUID accountTo,
            @Nullable EnumMap<GameRule, Boolean> rules) {
        Game game = new Game(
                plugin,
                name,
                npcName,
                npcTexture,
                npcSignature,
                new Model(plugin, type.getModelName(), modelId, location),
                minPlayers,
                maxPlayers,
                type,
                owner,
                startTime,
                accountTo,
                rules);

        games.add(game);

        // Save game to config.
        save(game);
    }

    public void save(Game game) {
        // Save model related data.
        configuration.set("games." + game.getName() + ".model.id", game.getModelId().toString());
        configuration.set("games." + game.getName() + ".model.type", game.getType().name());

        // Save location.
        configuration.set("games." + game.getName() + ".location", game.getLocation());

        // Save rules.
        for (GameRule rule : GameRule.values()) {
            String name = rule.name().replace("_", "-").toLowerCase();
            configuration.set("games." + game.getName() + ".rules." + name, game.isRuleEnabled(rule));
        }

        // Save settings.
        configuration.set("games." + game.getName() + ".settings.start-time", game.getStartTime());
        configuration.set("games." + game.getName() + ".settings.min-players", game.getMinPlayers());
        configuration.set("games." + game.getName() + ".settings.max-players", game.getMaxPlayers());

        // Save NPC related data.
        configuration.set("games." + game.getName() + ".npc.name", game.getNPCName());
        configuration.set("games." + game.getName() + ".npc.skin.texture", game.getNPCTexture());
        configuration.set("games." + game.getName() + ".npc.skin.signature", game.getNPCSignature());

        // Save other data.
        configuration.set("games." + game.getName() + ".other.owner-id", game.getOwner().toString());
        if (game.getAccountGiveTo() != null) {
            configuration.set("games." + game.getName() + ".other.account-to-id", game.getAccountGiveTo().toString());
        }

        saveConfig();
    }

    private void update() {
        games.forEach(Game::remove);
        games.clear();

        ConfigurationSection section = configuration.getConfigurationSection("games");
        if (section == null) return;

        int loaded = 0;

        for (String path : section.getKeys(false)) {

            // Load model related data.
            UUID modelId = UUID.fromString(configuration.getString("games." + path + ".model.id", UUID.randomUUID().toString()));
            GameType type;
            try {
                type = GameType.valueOf(configuration.getString("games." + path + ".model.type", "AMERICAN"));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("The game " + path + " has an invalid type of game.");
                continue;
            }

            // Load location.
            Location location = configuration.getSerializable("games." + path + ".location", Location.class);

            // Load rules.
            EnumMap<GameRule, Boolean> rules = new EnumMap<>(GameRule.class);
            rules.put(GameRule.LA_PARTAGE, configuration.getBoolean("games." + path + ".rules.la-partage"));
            rules.put(GameRule.EN_PRISON, configuration.getBoolean("games." + path + ".rules.en-prison"));
            rules.put(GameRule.SURRENDER, configuration.getBoolean("games." + path + ".rules.surrender"));

            // Load settings.
            int startTime = configuration.getInt("games." + path + ".settings.start-time");
            int minPlayers = configuration.getInt("games." + path + ".settings.min-players");
            int maxPlayers = configuration.getInt("games." + path + ".settings.max-players");

            // Load NPC related data.
            String npcName = configuration.getString("games." + path + ".npc.name");
            String texture = configuration.getString("games." + path + ".npc.skin.texture");
            String signature = configuration.getString("games." + path + ".npc.skin.signature");

            // Load other data.
            UUID owner = UUID.fromString(configuration.getString("games." + path + ".other.owner-id", UUID.randomUUID().toString()));
            String accountToString = configuration.getString("games." + path + ".other.account-to-id");
            UUID accountTo = accountToString != null ? UUID.fromString(accountToString) : null;

            add(path, npcName, texture, signature, minPlayers, maxPlayers, type, modelId, location, owner, startTime, accountTo, rules);
            loaded++;
        }

        if (loaded > 0) {
            plugin.getLogger().info("All games have been loaded from games.yml!");
            return;
        }
        plugin.getLogger().info("No games have been loaded from games.yml, why don't you create one?");
    }

    public boolean isPlaying(Player player) {
        for (Game game : games) {
            if (game.getPlayers().containsKey(player)) return true;
        }
        return false;
    }

    public Game getGame(String name) {
        for (Game game : games) {
            if (game.getName().equalsIgnoreCase(name)) return game;
        }
        return null;
    }

    public void deleteGame(Game game) {
        game.remove();
        games.remove(game);

        // Remove from config.
        configuration.set("games." + game.getName(), null);
        saveConfig();
    }

    public Game getGameByNPC(NPC npc) {
        for (Game game : games) {
            if (game.getNPC().equals(npc)) return game;
        }
        return null;
    }

    public Game getGameByPlayer(Player player) {
        for (Game game : games) {
            if (!game.isPlaying(player)) continue;
            return game;
        }
        return null;
    }

    public boolean exist(String name) {
        for (Game game : games) {
            if (game.getName().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    public List<Game> getGames() {
        return games;
    }

    private void saveConfig() {
        try {
            configuration.save(file);
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    public void reloadConfig() {
        try {
            configuration = new YamlConfiguration();
            configuration.load(file);
            update();
        } catch (IOException | InvalidConfigurationException exception) {
            exception.printStackTrace();
        }
    }
}