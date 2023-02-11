package me.matsubara.roulette;

import com.comphenix.protocol.ProtocolLibrary;
import com.cryptomorin.xseries.ReflectionUtils;
import com.tchristofferson.configupdater.ConfigUpdater;
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
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    private Team hideTeam;
    private Team collisionTeam;

    private static final List<String> SPECIAL_SECTIONS = Collections.singletonList("custom-win-multiplier.slots");

    @Override
    public void onEnable() {
        PluginManager pluginManager = getServer().getPluginManager();

        // Disable plugin if server version is older than 1.12.
        if (ReflectionUtils.VER < 12 || ReflectionUtils.VER == 16) {
            getLogger().info("This plugin only works from 1.12 and up (except 1.16), disabling...");
            pluginManager.disablePlugin(this);
            return;
        }

        // Disable plugin if dependencies aren't installed.
        if (!hasDependencies(DEPENDENCIES)) {
            getLogger().severe("You need to install all the dependencies to be able to use this plugin, disabling...");
            pluginManager.disablePlugin(this);
            return;
        }

        Plugin economyProvider;

        // Disable plugin if we can't set up economy manager.
        if ((economyProvider = setupEconomy()) == null) {
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

        new BukkitRunnable() {
            @Override
            public void run() {
                // Sometimes the economy provider plugin is enabled AFTER this plugin, so we need to wait for it to be enabled.
                if (!pluginManager.isPluginEnabled(economyProvider)) return;

                winnerManager.renderMaps();
                cancel();
            }
        }.runTaskTimer(this, 20L, 20L);

        // Save models to /models.
        saveModels(GameType.AMERICAN.getModelName(), GameType.EUROPEAN.getModelName());
        loadConfigAndUpdateIt();
    }

    private void loadConfigAndUpdateIt() {
        saveDefaultConfig();

        File file = new File(getDataFolder(), "config.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        List<String> ignore = new ArrayList<>();
        for (String path : SPECIAL_SECTIONS) {
            if (config.contains(path)) ignore.add(path);
        }

        try {
            ConfigUpdater.update(this, "config.yml", file, ignore);
        } catch (IOException exception) {
            exception.printStackTrace();
        }

        reloadConfig();
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

    private Plugin setupEconomy() {
        RegisteredServiceProvider<Economy> provider = getServer().getServicesManager().getRegistration(Economy.class);
        if (provider == null) return null;

        Plugin plugin = provider.getPlugin();

        getLogger().info("Using " + plugin.getName() + " as the economy provider.");
        economy = provider.getProvider();
        return plugin;
    }

    public NPCPool getNPCPool() {
        return npcPool;
    }

    public Economy getEconomy() {
        return economy;
    }

    public EssXExtension getEssXExtension() {
        return essXExtension;
    }

    public ChipManager getChipManager() {
        return chipManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public InputManager getInputManager() {
        return inputManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    @SuppressWarnings("unused")
    public StandManager getStandManager() {
        return standManager;
    }

    public WinnerManager getWinnerManager() {
        return winnerManager;
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