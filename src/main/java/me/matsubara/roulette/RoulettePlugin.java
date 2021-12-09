package me.matsubara.roulette;

import com.comphenix.protocol.ProtocolLibrary;
import com.cryptomorin.xseries.ReflectionUtils;
import com.github.juliarn.npc.NPCPool;
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
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;

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

    @Override
    public void onEnable() {
        // Disable plugin if server version is older than 1.12.
        if (ReflectionUtils.VER < 12 || ReflectionUtils.VER == 16) {
            getLogger().info("This plugin only works from 1.12 and up (except 1.16), disabling...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Disable plugin if dependencies aren't isntalled.
        if (!hasDependencies(DEPENDENCIES)) {
            getLogger().severe("You need to install all the dependencies to be able to use this plugin, disabling...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Disable plugin if we can't set up economy manager.
        if (!setupEconomy()) {
            getLogger().severe("You need to install an economy provider (like EssentialsX, CMI, etc...) to be able to use this plugin, disabling...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Register protocol events.
        ProtocolLibrary.getProtocolManager().addPacketListener(new SteerVehicle(this));
        ProtocolLibrary.getProtocolManager().addPacketListener(new UseEntity(this));

        // Register bukkit events.
        getServer().getPluginManager().registerEvents(new PlayerNPCInteract(this), this);
        getServer().getPluginManager().registerEvents(new EntityDamageByEntity(this), this);
        getServer().getPluginManager().registerEvents(new InventoryClick(this), this);
        getServer().getPluginManager().registerEvents(new InventoryClose(this), this);
        getServer().getPluginManager().registerEvents(new PlayerQuit(this), this);

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
        hideTeam = createTeam("rouletteHide", Team.Option.NAME_TAG_VISIBILITY);

        // Team used to disable collisions for players inside the game (sitting on chair).
        collisionTeam = createTeam("rouletteCollide", Team.Option.COLLISION_RULE);

        // Initialize managers.
        chipManager = new ChipManager(this);

        configManager = new ConfigManager(this);

        // Initialize npc pool (before games get loaded).
        npcPool = NPCPool.builder(this)
                .spawnDistance(configManager.getRenderDistance())
                .tabListRemoveTicks(40)
                .build();

        gameManager = new GameManager(this);
        inputManager = new InputManager(this);
        messageManager = new MessageManager(this);
        standManager = new StandManager(this);
        winnerManager = new WinnerManager(this);

        saveDefaultConfig();

        // Save models to /models.
        saveModels(GameType.AMERICAN.getModelName(), GameType.EUROPEAN.getModelName());
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

            saveResource("models/" + name + ".yml", false);
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
        return getServer().getPluginManager().isPluginEnabled(plugin);
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> provider = getServer().getServicesManager().getRegistration(Economy.class);
        if (provider == null) return false;

        getLogger().info("Using " + provider.getPlugin().getName() + " as the economy provider.");
        economy = provider.getProvider();
        return true;
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

    public StandManager getStandManager() {
        return standManager;
    }

    public WinnerManager getWinnerManager() {
        return winnerManager;
    }

    public Team getHideTeam() {
        return hideTeam;
    }

    public Team getCollisionTeam() {
        return collisionTeam;
    }
}