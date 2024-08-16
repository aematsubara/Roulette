package me.matsubara.roulette;

import com.cryptomorin.xseries.reflection.XReflection;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.EventManager;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
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
import me.matsubara.roulette.util.ItemBuilder;
import me.matsubara.roulette.util.PluginUtils;
import me.matsubara.roulette.util.config.ConfigChanges;
import me.matsubara.roulette.util.config.ConfigFileUtils;
import net.milkbowl.vault.economy.Economy;
import org.apache.commons.lang3.RandomUtils;
import org.bukkit.*;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionType;
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

    private final String[] DEPENDENCIES = {"packetevents", "Vault"};
    private final NavigableMap<Long, String> abbreviations = new TreeMap<>();

    private Team hideTeam;

    private GlowingEntities glowingEntities;

    private static final Set<String> SPECIAL_SECTIONS = Sets.newHashSet("custom-win-multiplier.slots", "money-abbreviation-format.translations", "not-enough-money");
    private static final List<String> GUI_TYPES = List.of("game-menu", "shop", "confirmation-gui");

    public NamespacedKey itemIdKey = new NamespacedKey(this, "ItemID");
    public NamespacedKey chipNameKey = new NamespacedKey(this, "ChipName");
    public NamespacedKey rouletteRuleKey = new NamespacedKey(this, "RouletteRule");

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().getSettings()
                .reEncodeByDefault(true)
                .checkForUpdates(false);
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        PluginManager pluginManager = getServer().getPluginManager();

        // Disable the plugin if the server version is older than 1.17.
        if (XReflection.MINOR_NUMBER < 17) {
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
        EventManager eventManager = PacketEvents.getAPI().getEventManager();
        eventManager.registerListener(new PlayerNPCInteract(this));
        eventManager.registerListener(new SteerVehicle(this));
        eventManager.registerListener(new UseEntity(this));

        // Register bukkit events.
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

        // Initialize npc pool (before games get loaded).
        npcPool = new NPCPool(this);

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

        try {
            glowingEntities = new GlowingEntities(this);
        } catch (IllegalStateException exception) {
            getLogger().warning("Your server version doesn't support glowing for betting chips; this feature is disabled.");
            glowingEntities = null;
        }

        reloadAbbreviations();
    }

    @Override
    public void onDisable() {
        PacketEvents.getAPI().terminate();

        // If disabling on startup, prevent errors in console and remove games.
        if (gameManager != null) gameManager.getGames().forEach(Game::remove);
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
                                2)
                        .addChange(
                                temp -> temp.getInt("config-version") == 2,
                                temp -> {
                                    // Changed to a GUI.
                                    temp.set("confirmation-gui", null);
                                },
                                3)
                        .addChange(
                                temp -> temp.getInt("config-version") == 3,
                                temp -> {
                                    // These were moved to @{variable-text}.
                                    temp.set("types", null);
                                    temp.set("state", null);
                                    temp.set("only-american", null);
                                    temp.set("unnamed-croupier", null);
                                },
                                4)
                        .build());

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

    private void saveModels(String... names) {
        for (String name : names) {
            File file = new File(getDataFolder() + File.separator + "models", name + ".yml");
            if (file.exists()) continue;

            saveResource("models" + File.separator + name + ".yml", false);
        }
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

    public ItemBuilder getItem(@NotNull String path) {
        FileConfiguration config = getConfig();

        String name = config.getString(path + ".display-name");
        List<String> lore = config.getStringList(path + ".lore");

        String url = config.getString(path + ".url");

        String materialPath = path + ".material";

        String materialName = config.getString(materialPath, "STONE");
        Material material = PluginUtils.getOrNull(Material.class, materialName);

        ItemBuilder builder = new ItemBuilder(material)
                .setData(itemIdKey, PersistentDataType.STRING, path.contains(".") ? path.substring(path.lastIndexOf(".") + 1) : path)
                .setLore(lore);
        if (name != null) builder.setDisplayName(name);

        String amountString = config.getString(path + ".amount");
        if (amountString != null) {
            int amount = PluginUtils.getRangedAmount(amountString);
            builder.setAmount(amount);
        }

        if (material == Material.PLAYER_HEAD && url != null) {
            // Use UUID from path to allow stacking heads.
            UUID itemUUID = UUID.nameUUIDFromBytes(path.getBytes());
            builder.setHead(itemUUID, url, true);
        }

        int modelData = config.getInt(path + ".model-data", Integer.MIN_VALUE);
        if (modelData != Integer.MIN_VALUE) builder.setCustomModelData(modelData);

        for (String enchantmentString : config.getStringList(path + ".enchantments")) {
            if (Strings.isNullOrEmpty(enchantmentString)) continue;
            String[] data = PluginUtils.splitData(enchantmentString);

            Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(data[0].toLowerCase()));

            int level;
            try {
                level = PluginUtils.getRangedAmount(data[1]);
            } catch (IndexOutOfBoundsException | IllegalArgumentException exception) {
                level = 1;
            }

            if (enchantment != null) builder.addEnchantment(enchantment, level);
        }

        for (String flag : config.getStringList(path + ".flags")) {
            builder.addItemFlags(ItemFlag.valueOf(flag.toUpperCase()));
        }

        String tippedArrow = config.getString(path + ".tipped");
        if (tippedArrow != null) {
            PotionType potionType = PluginUtils.getOrEitherRandomOrNull(PotionType.class, tippedArrow);
            if (potionType != null) builder.setBasePotionData(potionType);
        }

        Object leather = config.get(path + ".leather-color");
        if (leather instanceof String leatherColor) {
            Color color = PluginUtils.getColor(leatherColor);
            if (color != null) builder.setLeatherArmorMetaColor(color);
        } else if (leather instanceof List<?> list) {
            List<Color> colors = new ArrayList<>();

            for (Object object : list) {
                if (!(object instanceof String string)) continue;
                if (string.equalsIgnoreCase("$RANDOM")) continue;

                Color color = PluginUtils.getColor(string);
                if (color != null) colors.add(color);
            }

            if (!colors.isEmpty()) {
                Color color = colors.get(RandomUtils.nextInt(0, colors.size()));
                builder.setLeatherArmorMetaColor(color);
            }
        }

        if (config.contains(path + ".firework")) {
            ConfigurationSection section = config.getConfigurationSection(path + ".firework.firework-effects");
            if (section == null) return builder;

            Set<FireworkEffect> effects = new HashSet<>();
            for (String effect : section.getKeys(false)) {
                FireworkEffect.Builder effectBuilder = FireworkEffect.builder();

                String type = config.getString(path + ".firework.firework-effects." + effect + ".type");
                if (type == null) continue;

                FireworkEffect.Type effectType = PluginUtils.getOrEitherRandomOrNull(FireworkEffect.Type.class, type);

                boolean flicker = config.getBoolean(path + ".firework.firework-effects." + effect + ".flicker");
                boolean trail = config.getBoolean(path + ".firework.firework-effects." + effect + ".trail");

                effects.add((effectType != null ?
                        effectBuilder.with(effectType) :
                        effectBuilder)
                        .flicker(flicker)
                        .trail(trail)
                        .withColor(getFireworkColors(path, effect, "colors"))
                        .withFade(getFireworkColors(path, effect, "fade-colors"))
                        .build());
            }

            String powerString = config.getString(path + ".firework.power");
            int power = PluginUtils.getRangedAmount(powerString != null ? powerString : "");

            if (!effects.isEmpty()) builder.initializeFirework(power, effects.toArray(new FireworkEffect[0]));
        }

        String damageString = config.getString(path + ".damage");
        if (damageString != null) {
            int maxDurability = builder.build().getType().getMaxDurability();

            int damage;
            if (damageString.equalsIgnoreCase("$RANDOM")) {
                damage = RandomUtils.nextInt(1, maxDurability);
            } else if (damageString.contains("%")) {
                damage = Math.round(maxDurability * ((float) PluginUtils.getRangedAmount(damageString.replace("%", "")) / 100));
            } else {
                damage = PluginUtils.getRangedAmount(damageString);
            }

            if (damage > 0) builder.setDamage(Math.min(damage, maxDurability));
        }

        return builder;
    }

    private @NotNull Set<Color> getFireworkColors(String path, String effect, String needed) {
        Set<Color> colors = new HashSet<>();
        for (String colorString : getConfig().getStringList(path + ".firework.firework-effects." + effect + "." + needed)) {
            Color color = PluginUtils.getColor(colorString);
            if (color != null) colors.add(color);
        }
        return colors;
    }
}