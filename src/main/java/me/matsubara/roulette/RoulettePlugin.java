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
import me.matsubara.roulette.game.data.Bet;
import me.matsubara.roulette.game.data.Slot;
import me.matsubara.roulette.game.data.WinData;
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
import me.matsubara.roulette.manager.data.DataManager;
import me.matsubara.roulette.manager.data.PlayerResult;
import me.matsubara.roulette.npc.NPCPool;
import me.matsubara.roulette.util.GlowingEntities;
import me.matsubara.roulette.util.ItemBuilder;
import me.matsubara.roulette.util.PluginUtils;
import me.matsubara.roulette.util.config.ConfigFileUtils;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.apache.commons.lang3.StringUtils;
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
    private DataManager dataManager;
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
    private static final List<String> GUI_TYPES = List.of(
            "confirmation-menu",
            "game-menu",
            "croupier-menu",
            "chip-menu",
            "game-chip-menu",
            "bets-menu",
            "sessions-menu",
            "session-result-menu",
            "table-menu");

    public NamespacedKey itemIdKey = new NamespacedKey(this, "ItemID");
    public NamespacedKey chipNameKey = new NamespacedKey(this, "ChipName");
    public NamespacedKey betIndexKey = new NamespacedKey(this, "BetIndex");
    public NamespacedKey rouletteRuleKey = new NamespacedKey(this, "RouletteRule");
    public NamespacedKey customizationGroupIdKey = new NamespacedKey(this, "CustomizationGroupID");
    public NamespacedKey sessionKey = new NamespacedKey(this, "Session");
    public NamespacedKey playerResultIndexKey = new NamespacedKey(this, "PlayerResultIndex");

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

        // If the old winners.yml file exists, then we want to rename the plugin folder to "Roulette_old"
        // since we want to have a fresh plugin folder.
        if (new File(getDataFolder(), "winners.yml").exists()) {
            File folder = new File(getDataFolder().getPath());
            File old = new File(folder.getParent(), "Roulette_old");
            if (folder.renameTo(old)) {
                getLogger().warning("Files from the old version of Roulette have been detected, moving them to " + old.getPath());
            } else {
                getLogger().severe("Files from the old version of Roulette have been detected but they can't be removed, " +
                        "you'll have to stop the server and remove them manually.");
                pluginManager.disablePlugin(this);
                return;
            }
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

        // Save dab animation.
        saveFile("dab_animation.txt");

        // AFTER updating configs.
        gameManager = new GameManager(this);
        dataManager = new DataManager(this);
        winnerManager = new WinnerManager(this);

        try {
            glowingEntities = new GlowingEntities(this);
        } catch (IllegalStateException exception) {
            getLogger().warning("Your server version doesn't support glowing for betting chips; this feature is disabled.");
            glowingEntities = null;
        }

        reloadAbbreviations();
    }

    public @NotNull File saveFile(@SuppressWarnings("SameParameterValue") String name) {
        File file = new File(getDataFolder(), name);
        if (!file.exists()) saveResource(name, false);
        return file;
    }

    @Override
    public void onDisable() {
        PacketEvents.getAPI().terminate();

        // If disabling on startup, prevent errors in console.
        if (gameManager != null) gameManager.getGames().forEach(Game::remove);
    }

    public boolean deposit(OfflinePlayer player, double money) {
        EconomyResponse response = economy.depositPlayer(player, money);
        if (response.transactionSuccess()) return true;

        getLogger().warning(String.format("It wasn't possible to deposit $%s to %s.", money, player.getName()));
        return false;
    }

    public boolean withdrawFailed(OfflinePlayer player, double money) {
        EconomyResponse response = economy.withdrawPlayer(player, money);
        if (response.transactionSuccess()) return false;

        getLogger().warning(String.format("It wasn't possible to withdraw $%s to %s.", money, player.getName()));
        return true;
    }

    public double getExpectedMoney(@NotNull Bet bet) {
        return getExpectedMoney(bet.getChip().price(), bet.getSlot(), bet.getWinData().winType());
    }

    public double getExpectedMoney(@NotNull PlayerResult result) {
        return getExpectedMoney(result.money(), result.slot(), result.win());
    }

    public double getExpectedMoney(double price, Slot slot, @Nullable WinData.WinType winType) {
        if (winType == null) return price;

        double money;
        if (winType.isNormalWin()) {
            money = price * slot.getMultiplier(this);
        } else if (winType.isLaPartageWin() || winType.isSurrenderWin()) {
            // Half-money if is partage.
            money = price / 2;
        } else {
            // Original money.
            money = price;
        }
        return money;
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
                Collections.emptyList());

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
                Color color = colors.get(PluginUtils.RANDOM.nextInt(0, colors.size()));
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
                damage = PluginUtils.RANDOM.nextInt(1, maxDurability);
            } else if (damageString.contains("%")) {
                damage = Math.round(maxDurability * ((float) PluginUtils.getRangedAmount(damageString.replace("%", "")) / 100));
            } else {
                damage = PluginUtils.getRangedAmount(damageString);
            }

            if (damage > 0) builder.setDamage(Math.min(damage, maxDurability));
        }

        return builder;
    }

    public String formatWinType(WinData.@NotNull WinType winType) {
        String shortName = winType.getShortName();
        String defaultName = Optional.ofNullable(winType.getRule())
                .map(Enum::name)
                .orElse("none")
                .replace("_", " ");

        return getConfig().getString(
                "variable-text.rules." + (shortName != null ? shortName : "none"),
                StringUtils.capitalize(defaultName));
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