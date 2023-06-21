package me.matsubara.roulette;

import com.comphenix.protocol.ProtocolLibrary;
import com.cryptomorin.xseries.ReflectionUtils;
import com.tchristofferson.configupdater.ConfigUpdater;
import lombok.Getter;
import me.matsubara.roulette.command.Main;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.GameType;
import me.matsubara.roulette.hook.EssXExtension;
import me.matsubara.roulette.hook.PAPIExtension;
import me.matsubara.roulette.listener.EntityDamageByEntity;
import me.matsubara.roulette.listener.InventoryClick;
import me.matsubara.roulette.listener.InventoryClose;
import me.matsubara.roulette.listener.PlayerQuit;
import me.matsubara.roulette.listener.npc.PlayerNPCInteract;
import me.matsubara.roulette.listener.protocol.SteerVehicle;
import me.matsubara.roulette.listener.protocol.UseEntity;
import me.matsubara.roulette.manager.*;
import me.matsubara.roulette.npc.NPCPool;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

@Getter
public final class RoulettePlugin extends JavaPlugin {

    // Managers.
    private ChipManager chipManager;
    private ConfigManager configManager;
    private GameManager gameManager;
    private InputManager inputManager;
    private MessageManager messageManager;
    private StandManager standManager;
    private WinnerManager winnerManager;

    // NPC Pool.
    private NPCPool npcPool;

    // Economy manager.
    private Economy economy;

    // EssentialsX extension.
    private EssXExtension essXExtension;

    private final String[] DEPENDENCIES = {"ProtocolLib", "Vault"};
    private final NavigableMap<Long, String> abbreviations = new TreeMap<>();

    private Team hideTeam;
    private Team collisionTeam;

    private static final List<String> SPECIAL_SECTIONS = Collections.singletonList("custom-win-multiplier.slots");

    @Override
    public void onEnable() {
        PluginManager pluginManager = getServer().getPluginManager();

        // Disable plugin if server version is older than 1.13.
        if (ReflectionUtils.MINOR_NUMBER < 13 || ReflectionUtils.MINOR_NUMBER == 16) {
            getLogger().info("This plugin only works from 1.13 and up (except 1.16), disabling...");
            pluginManager.disablePlugin(this);
            return;
        }

        // Disable plugin if dependencies aren't installed.
        if (!hasDependencies(DEPENDENCIES)) {
            getLogger().severe("You need to install all the dependencies to be able to use this plugin, disabling...");
            pluginManager.disablePlugin(this);
            return;
        }


        // Disable plugin if we can't set up economy manager.
        if (setupEconomy() == null) {
            getLogger().severe("You need to install an economy provider (like EssentialsX, CMI, etc...) to be able to use this plugin, disabling...");
            pluginManager.disablePlugin(this);
            return;
        }

        // Register protocol events.
        ProtocolLibrary.getProtocolManager().addPacketListener(new SteerVehicle(this));
        ProtocolLibrary.getProtocolManager().addPacketListener(new UseEntity(this));

        // Register bukkit events.
        pluginManager.registerEvents(new PlayerNPCInteract(this), this);
        pluginManager.registerEvents(new EntityDamageByEntity(this), this);
        pluginManager.registerEvents(new InventoryClick(this), this);
        pluginManager.registerEvents(new InventoryClose(this), this);
        pluginManager.registerEvents(new PlayerQuit(this), this);

        // Register main command.
        PluginCommand mainCommand = getCommand("roulette");
        if (mainCommand != null) {
            Main main = new Main(this);
            mainCommand.setExecutor(main);
            mainCommand.setTabCompleter(main);
        }

        // Register placeholder for PAPI.
        if (hasDependency("PlaceholderAPI")) new PAPIExtension(this);
        if (hasDependency("Essentials")) essXExtension = new EssXExtension(this);

        // Team used to disable the nametag of NPCs.
        getHideTeam();

        // Team used to disable collisions for players inside the game (sitting on chair).
        getCollisionTeam();

        // Initialize managers.
        chipManager = new ChipManager(this);
        configManager = new ConfigManager(this);

        int distance = Math.max(1, configManager.getRenderDistance());

        // Initialize npc pool (before games get loaded).
        npcPool = NPCPool.builder(this)
                .spawnDistance(distance)
                .actionDistance(distance)
                .tabListRemoveTicks(40)
                .build();

        gameManager = new GameManager(this);
        inputManager = new InputManager(this);
        messageManager = new MessageManager(this);
        standManager = new StandManager(this);
        winnerManager = new WinnerManager(this);

        // Save models to /models.
        saveModels(GameType.AMERICAN.getModelName(), GameType.EUROPEAN.getModelName());
        loadConfigAndUpdateIt();

        reloadAbbreviations();
    }

    public void reloadAbbreviations() {
        abbreviations.clear();

        ConfigurationSection abbreviations = getConfig().getConfigurationSection("money-abbreviation-format.translations");
        if (abbreviations == null) return;

        for (String key : abbreviations.getKeys(false)) {
            long bound = getConfig().getLong("money-abbreviation-format.translations." + key);
            this.abbreviations.put(bound, key);
        }
    }

    private void loadConfigAndUpdateIt() {
        saveDefaultConfig();

        File file = new File(getDataFolder(), "config.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        List<String> ignore = new ArrayList<>();
        for (String path : SPECIAL_SECTIONS) {
            if (config.contains(path)) ignore.add(path);
        }

        Predicate<FileConfiguration> noVersion = temp -> !temp.contains("config-version");

        // Update translations (vX.X{0} -> v1.9.6{1}).
        handleConfigChanges(
                file,
                config,
                "config.yml",
                noVersion,
                temp -> temp.set("money-abbreviation-format.translations", null),
                1);

        try {
            ConfigUpdater.update(this, "config.yml", file, ignore);
        } catch (IOException exception) {
            exception.printStackTrace();
        }

        reloadConfig();
    }

    @SuppressWarnings("SameParameterValue")
    private void handleConfigChanges(@NotNull File file, FileConfiguration config, String fileTargetName, Predicate<FileConfiguration> predicate, Consumer<FileConfiguration> consumer, int newVersion) {
        if (!file.getName().equals(fileTargetName) || !predicate.test(config)) return;

        consumer.accept(config);
        config.set("config-version", newVersion);

        try {
            config.save(file);
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    private Team createTeam(String teamName, Team.Option toDisable) {
        @SuppressWarnings("ConstantConditions") Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

        // Get team.
        Team team = scoreboard.getTeam(teamName);

        // Remove entries if exists.
        if (team != null) {
            try {
                team.getEntries().forEach(team::removeEntry);
            } catch (IllegalStateException ignore) {
            }
        } else {
            team = scoreboard.registerNewTeam(teamName);
            team.setOption(toDisable, Team.OptionStatus.NEVER);
        }

        return team;
    }

    private void saveModels(String... names) {
        for (String name : names) {
            File file = new File(getDataFolder() + File.separator + "models", name + ".yml");
            if (file.exists()) continue;

            saveResource("models" + File.separator + name + ".yml", false);
        }
    }

    @Override
    public void onDisable() {
        // If disabling on startup, prevent errors in console and remove games.
        if (gameManager != null) gameManager.getGames().forEach(Game::remove);
    }

    public boolean hasDependencies(String... dependencies) {
        for (String plugin : dependencies) {
            if (!hasDependency(plugin)) return false;
        }
        return true;
    }

    public boolean hasDependency(String plugin) {
        return getServer().getPluginManager().getPlugin(plugin) != null;
    }

    public Plugin setupEconomy() {
        RegisteredServiceProvider<Economy> provider = getServer().getServicesManager().getRegistration(Economy.class);
        if (provider == null) return null;

        Plugin plugin = provider.getPlugin();
        if (provider.getProvider().equals(economy)) return plugin;

        getLogger().info("Using " + plugin.getDescription().getFullName() + " as the economy provider.");
        economy = provider.getProvider();
        return plugin;
    }

    public Team getHideTeam() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (hideTeam == null || (manager != null && manager.getMainScoreboard().getTeam("rouletteHide") == null)) {
            hideTeam = createTeam("rouletteHide", Team.Option.NAME_TAG_VISIBILITY);
        }
        return hideTeam;
    }

    public Team getCollisionTeam() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (collisionTeam == null || (manager != null && manager.getMainScoreboard().getTeam("rouletteCollide") == null)) {
            collisionTeam = createTeam("rouletteCollide", Team.Option.COLLISION_RULE);
        }
        return collisionTeam;
    }
}