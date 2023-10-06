package me.matsubara.roulette;

import com.comphenix.protocol.ProtocolLibrary;
import com.cryptomorin.xseries.ReflectionUtils;
import com.google.common.collect.Sets;
import lombok.Getter;
import me.matsubara.roulette.command.MainCommand;
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
import me.matsubara.roulette.util.GlowingEntities;
import me.matsubara.roulette.util.config.ConfigChanges;
import me.matsubara.roulette.util.config.ConfigFileUtils;
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
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

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

    private GlowingEntities glowingEntities;

    private static final Set<String> SPECIAL_SECTIONS = Sets.newHashSet("custom-win-multiplier.slots", "money-abbreviation-format.translations", "not-enough-money");
    private static final List<String> GUI_TYPES = List.of("game-menu", "shop");

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
            MainCommand main = new MainCommand(this);
            mainCommand.setExecutor(main);
            mainCommand.setTabCompleter(main);
        }

        // Register placeholder for PAPI.
        if (hasDependency("PlaceholderAPI")) new PAPIExtension(this);
        if (hasDependency("Essentials")) essXExtension = new EssXExtension(this);

        // Team used to disable the nametag of NPCs.
        getHideTeam();

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

        inputManager = new InputManager(this);
        messageManager = new MessageManager(this);
        standManager = new StandManager(this);

        // Save models to /models.
        saveModels(GameType.AMERICAN.getModelName(), GameType.EUROPEAN.getModelName());

        saveDefaultConfig();
        updateConfigs();

        // AFTER updating configs.
        gameManager = new GameManager(this);
        winnerManager = new WinnerManager(this);

        glowingEntities = new GlowingEntities(this);

        reloadAbbreviations();
    }

    private void fillIgnoredSections(FileConfiguration config) {
        for (String guiType : GUI_TYPES) {
            ConfigurationSection section = config.getConfigurationSection(guiType);
            if (section == null) continue;

            for (String key : section.getKeys(false)) {
                SPECIAL_SECTIONS.add(guiType + "." + key);
            }
        }
    }

    public void updateConfigs() {
        String folder = getDataFolder().getPath();

        ConfigFileUtils.updateConfig(
                this,
                folder,
                "config.yml",
                file -> reloadConfig(),
                file -> saveDefaultConfig(),
                config -> {
                    fillIgnoredSections(config);
                    return SPECIAL_SECTIONS.stream().filter(config::contains).toList();
                },
                ConfigChanges.builder()
                        // Update translations (vX.X{0} -> v1.9.6{1}).
                        .addChange(
                                temp -> !temp.contains("config-version"),
                                temp -> temp.set("money-abbreviation-format.translations", null),
                                1)
                        // Move sounds & types to multiline format (vX.X{1} -> v2.0{2}).
                        .addChange(
                                temp -> temp.getInt("config-version") == 1,
                                temp -> {
                                    // Sounds.
                                    temp.set("sounds.click", temp.getString("sound.click", "BLOCK_NOTE_BLOCK_PLING"));
                                    temp.set("sounds.countdown", temp.getString("sound.countdown", "ENTITY_EXPERIENCE_ORB_PICKUP"));
                                    temp.set("sounds.spinning", temp.getString("sound.spinning", "BLOCK_METAL_PRESSURE_PLATE_CLICK_ON"));
                                    temp.set("sounds.swap-chair", temp.getString("sound.swap-chair", "ENTITY_PLAYER_ATTACK_CRIT"));
                                    temp.set("sounds.select", temp.getString("sound.select", "BLOCK_WOOL_PLACE"));
                                    // Types.
                                    temp.set("types.european", temp.getString("type.european", "&a(European)"));
                                    temp.set("types.american", temp.getString("type.american", "&a(American)"));
                                },
                                2).build());

        ConfigFileUtils.updateConfig(
                this,
                folder,
                "messages.yml",
                file -> messageManager.setConfiguration(YamlConfiguration.loadConfiguration(file)),
                file -> saveResource("messages.yml"),
                config -> Collections.emptyList(),
                Collections.emptyList());
    }

    public void saveResource(String name) {
        File file = new File(getDataFolder(), name);
        if (!file.exists()) saveResource(name, false);
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

    private void saveModels(String @NotNull ... names) {
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

    public boolean hasDependencies(String @NotNull ... dependencies) {
        for (String plugin : dependencies) {
            if (!hasDependency(plugin)) return false;
        }
        return true;
    }

    public boolean hasDependency(String plugin) {
        return getServer().getPluginManager().getPlugin(plugin) != null;
    }

    public @Nullable Plugin setupEconomy() {
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
            hideTeam = createHideTeam();
        }
        return hideTeam;
    }

    private @NotNull Team createHideTeam() {
        @SuppressWarnings("ConstantConditions") Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

        // Get team.
        Team team = scoreboard.getTeam("rouletteHide");

        // Remove entries if exists.
        if (team != null) {
            try {
                team.getEntries().forEach(team::removeEntry);
            } catch (IllegalStateException ignore) {
            }
        } else {
            team = scoreboard.registerNewTeam("rouletteHide");
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        }

        return team;
    }
}