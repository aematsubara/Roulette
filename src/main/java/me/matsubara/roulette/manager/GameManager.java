package me.matsubara.roulette.manager;

import com.google.common.base.Preconditions;
import lombok.Getter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.GameRule;
import me.matsubara.roulette.game.GameType;
import me.matsubara.roulette.model.Model;
import me.matsubara.roulette.npc.NPC;
import me.matsubara.roulette.util.ParrotUtils;
import me.matsubara.roulette.util.PluginUtils;
import org.apache.commons.lang3.Validate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spigotmc.event.entity.EntityDismountEvent;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class GameManager implements Listener {

    private final RoulettePlugin plugin;
    private final @Getter List<Game> games;

    private File file;
    private FileConfiguration configuration;

    public GameManager(RoulettePlugin plugin) {
        this.plugin = plugin;
        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.games = new ArrayList<>();
        load();
    }

    // This is deprecated and removed in 1.20.6,
    // but as we are compiling with 1.20.1, the class will be changed on runtime.
    @EventHandler(ignoreCancelled = true)
    public void onPlayerDismount(@NotNull EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(event.getDismounted() instanceof ArmorStand)) return;

        Game playing = getGameByPlayer(player);
        if (playing == null || playing.getTransfers().remove(player.getUniqueId())) return;

        // Remove player from game.
        plugin.getMessageManager().send(player, MessageManager.Message.LEAVE_PLAYER);
        playing.removeCompletely(player);
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

    public void addFreshGame(String name, int minPlayers, int maxPlayers, GameType type, UUID modelId, Location location, UUID owner, int startTime) {
        add(name, null, null, null, minPlayers, maxPlayers, type, modelId, location, owner, startTime, true, null, null, false, false, null, null, null, null, null, null);
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
            boolean betAll,
            @Nullable UUID accountTo,
            @Nullable EnumMap<GameRule, Boolean> rules,
            boolean parrotEnabled,
            boolean parrotSounds,
            @Nullable Parrot.Variant parrotVariant,
            @Nullable ParrotUtils.ParrotShoulder parrotShoulder,
            @Nullable Material carpet,
            @Nullable Material customization,
            @Nullable Integer patternIndex,
            @Nullable List<String> chipsDisabled) {
        Game game = new Game(
                plugin,
                name,
                npcName,
                npcTexture,
                npcSignature,
                new Model(plugin, type.getModelName(), modelId, location, carpet, customization, patternIndex),
                minPlayers,
                maxPlayers,
                type,
                owner,
                startTime,
                betAll,
                accountTo,
                rules,
                parrotEnabled,
                parrotSounds,
                parrotVariant,
                parrotShoulder,
                chipsDisabled);

        games.add(game);

        // Save game to config.
        save(game);
    }

    public void save(@NotNull Game game) {
        String name = game.getName();

        // Save model related data.
        configuration.set("games." + name + ".model.id", game.getModelId().toString());
        configuration.set("games." + name + ".model.type", game.getType().name());

        Model model = game.getModel();

        // Save wool material for chairs.
        configuration.set("games." + name + ".model.carpet", null); // Previous mapping.
        configuration.set("games." + name + ".model.wool-type", model.getCarpetsType().name());

        // Save customization material for the table and chairs.
        configuration.set("games." + name + ".model.wood-type", null); // Previous mapping.
        configuration.set("games." + name + ".model.customization-group", model.getTexture().block().name());

        // Save decoration pattern.
        configuration.set("games." + name + ".model.deco-pattern", null); // Previous mapping.
        configuration.set("games." + name + ".model.pattern-index", model.getPatternIndex());

        // Save location.
        saveLocation(name, game.getLocation());

        // Save rules.
        for (GameRule rule : GameRule.values()) {
            String ruleName = rule.name().replace("_", "-").toLowerCase(Locale.ROOT);
            configuration.set("games." + name + ".rules." + ruleName, game.isRuleEnabled(rule));
        }

        // Save settings.
        configuration.set("games." + name + ".settings.bet-all", game.isBetAllEnabled());
        configuration.set("games." + name + ".settings.start-time", game.getStartTime());
        configuration.set("games." + name + ".settings.min-players", game.getMinPlayers());
        configuration.set("games." + name + ".settings.max-players", game.getMaxPlayers());
        configuration.set("games." + name + ".settings.chips-disabled", game.getChipsDisabled());

        // Save NPC related data.
        configuration.set("games." + name + ".npc.name", game.getNPCName());
        configuration.set("games." + name + ".npc.parrot.enabled", game.isParrotEnabled());
        configuration.set("games." + name + ".npc.parrot.sounds", game.isParrotSounds());
        configuration.set("games." + name + ".npc.parrot.variant", game.getParrotVariant().name());
        configuration.set("games." + name + ".npc.parrot.shoulder", game.getParrotShoulder().name());
        if (game.hasNPCTexture()) {
            // Save skin data.
            configuration.set("games." + name + ".npc.skin.texture", game.getNPCTexture());
            configuration.set("games." + name + ".npc.skin.signature", game.getNPCSignature());
        } else {
            // Remove skin section.
            configuration.set("games." + name + ".npc.skin", null);
        }

        // Save other data.
        configuration.set("games." + name + ".other.owner-id", game.getOwner().toString());
        if (game.getAccountGiveTo() != null) {
            configuration.set("games." + name + ".other.account-to-id", game.getAccountGiveTo().toString());
        }

        saveConfig();
    }

    private void update() {
        games.forEach(Game::remove);
        games.clear();

        ConfigurationSection section = configuration.getConfigurationSection("games");
        if (section == null) return;

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

            Material carpet = PluginUtils.getOrNull(Material.class, configuration.getString("games." + path + ".model.wool-type"));
            Material customization = PluginUtils.getOrNull(Material.class, configuration.getString("games." + path + ".model.customization-group"));

            int patternIndex = configuration.getInt("games." + path + ".model.pattern-index", -1);

            // Load location.
            Location location = loadLocation(path);

            // Load rules.
            EnumMap<GameRule, Boolean> rules = new EnumMap<>(GameRule.class);
            rules.put(GameRule.LA_PARTAGE, configuration.getBoolean("games." + path + ".rules.la-partage"));
            rules.put(GameRule.EN_PRISON, configuration.getBoolean("games." + path + ".rules.en-prison"));
            rules.put(GameRule.SURRENDER, configuration.getBoolean("games." + path + ".rules.surrender"));

            // Load settings.
            boolean betAll = configuration.getBoolean("games." + path + ".settings.bet-all");
            int startTime = configuration.getInt("games." + path + ".settings.start-time");
            int minPlayers = configuration.getInt("games." + path + ".settings.min-players");
            int maxPlayers = configuration.getInt("games." + path + ".settings.max-players");
            List<String> chipsDisabled = configuration.getStringList("games." + path + ".settings.chips-disabled");

            // Load NPC related data.
            String npcName = configuration.getString("games." + path + ".npc.name");
            String texture = configuration.getString("games." + path + ".npc.skin.texture");
            String signature = configuration.getString("games." + path + ".npc.skin.signature");

            // Load other data.
            UUID owner = UUID.fromString(configuration.getString("games." + path + ".other.owner-id", UUID.randomUUID().toString()));
            String accountToString = configuration.getString("games." + path + ".other.account-to-id");
            UUID accountTo = accountToString != null ? UUID.fromString(accountToString) : null;

            boolean parrotEnabled = configuration.getBoolean("games." + path + ".npc.parrot.enabled", false);
            boolean parrotSounds = configuration.getBoolean("games." + path + ".npc.parrot.sounds", false);
            Parrot.Variant parrotVariant = PluginUtils.getOrNull(Parrot.Variant.class, configuration.getString("games." + path + ".npc.parrot.variant", ""));
            ParrotUtils.ParrotShoulder parrotShoulder = PluginUtils.getOrNull(ParrotUtils.ParrotShoulder.class, configuration.getString("games." + path + ".npc.parrot.shoulder", ""));

            add(path,
                    npcName,
                    texture,
                    signature,
                    minPlayers,
                    maxPlayers,
                    type,
                    modelId,
                    location,
                    owner,
                    startTime,
                    betAll,
                    accountTo,
                    rules,
                    parrotEnabled,
                    parrotSounds,
                    parrotVariant,
                    parrotShoulder,
                    carpet,
                    customization,
                    patternIndex,
                    chipsDisabled);
        }

        if (!games.isEmpty()) {
            plugin.getLogger().info("All games have been loaded from games.yml!");
            return;
        }

        plugin.getLogger().info("No games have been loaded from games.yml, why don't you create one?");
    }

    @Contract("_ -> new")
    private @NotNull Location loadLocation(String path) {
        String worldName = configuration.getString("games." + path + ".location.world");
        Preconditions.checkNotNull(worldName);

        double x = configuration.getDouble("games." + path + ".location.x");
        double y = configuration.getDouble("games." + path + ".location.y");
        double z = configuration.getDouble("games." + path + ".location.z");
        float yaw = (float) configuration.getDouble("games." + path + ".location.yaw");
        float pitch = (float) configuration.getDouble("games." + path + ".location.pitch");

        return new Location(Bukkit.getWorld(worldName), x, y, z, yaw, pitch);
    }

    private void saveLocation(String path, @NotNull Location location) {
        Validate.notNull(location.getWorld(), "World can't be null.");
        configuration.set("games." + path + ".location.world", location.getWorld().getName());
        configuration.set("games." + path + ".location.x", location.getX());
        configuration.set("games." + path + ".location.y", location.getY());
        configuration.set("games." + path + ".location.z", location.getZ());
        configuration.set("games." + path + ".location.yaw", location.getYaw());
        configuration.set("games." + path + ".location.pitch", 0.0f);
    }

    public boolean isPlaying(Player player) {
        for (Game game : games) {
            if (game.isPlaying(player)) return true;
        }
        return false;
    }

    public @Nullable Game getGame(String name) {
        for (Game game : games) {
            if (game.getName().equalsIgnoreCase(name)) return game;
        }
        return null;
    }

    public void deleteGame(@NotNull Game game) {
        game.remove();
        games.remove(game);

        // Remove from config.
        configuration.set("games." + game.getName(), null);
        saveConfig();
    }

    public @Nullable Game getGameByNPC(NPC npc) {
        for (Game game : games) {
            if (game.getNpc().equals(npc)) return game;
        }
        return null;
    }

    @Contract(pure = true)
    public @Nullable Game getGameByPlayer(Player player) {
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