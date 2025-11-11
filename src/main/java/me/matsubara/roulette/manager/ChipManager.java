package me.matsubara.roulette.manager;

import com.google.common.base.Predicates;
import lombok.Getter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.file.Messages;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.data.Chip;
import me.matsubara.roulette.util.ItemBuilder;
import me.matsubara.roulette.util.PluginUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Getter
public final class ChipManager {

    private final RoulettePlugin plugin;
    private final List<Chip> chips;

    private File file;
    private FileConfiguration configuration;

    private static final String BET_ALL_SKIN = "e36e94f6c34a35465fce4a90f2e25976389eb9709a12273574ff70fd4daa6852";

    public ChipManager(RoulettePlugin plugin) {
        this.plugin = plugin;
        this.chips = new ArrayList<>();
        load();
    }

    private void load() {
        file = new File(plugin.getDataFolder(), "chips.yml");
        if (!file.exists()) {
            plugin.saveResource("chips.yml", false);
        }
        configuration = new YamlConfiguration();
        try {
            configuration.load(file);
            update();
        } catch (IOException | InvalidConfigurationException exception) {
            exception.printStackTrace();
        }
    }

    private void update() {
        chips.clear();

        ConfigurationSection section = configuration.getConfigurationSection("chips");
        if (section == null) return;

        int loaded = 0;

        for (String path : section.getKeys(false)) {
            String displayName = hasDisplayName(path) ? getDisplayName(path) : null;
            List<String> lore = hasLore(path) ? getLore(path) : null;
            String url = configuration.getString("chips." + path + ".url");
            double price = configuration.getDouble("chips." + path + ".price");

            Chip chip = new Chip(path, displayName, lore, url, price);
            chips.add(chip);
            loaded++;
        }

        if (loaded > 0) {
            plugin.getLogger().info("All chips have been loaded from chips.yml!");
            chips.sort(Comparator.comparing(Chip::price));
            return;
        }

        plugin.getLogger().info("No chips have been loaded from chips.yml, why don't you create one?");
    }

    private boolean hasDisplayName(String path) {
        return configuration.get("chips." + path + ".display-name") != null;
    }

    private boolean hasLore(String path) {
        return configuration.get("chips." + path + ".lore") != null;
    }

    private String getDisplayName(String path) {
        return PluginUtils.translate(configuration.getString("chips." + path + ".display-name"));
    }

    private @NotNull List<String> getLore(String path) {
        return PluginUtils.translate(configuration.getStringList("chips." + path + ".lore"));
    }

    public List<Chip> getChipsByGame(@NotNull Game game) {
        return chips.stream()
                .filter(Predicates.not(game::isChipDisabled))
                .toList();
    }

    public @Nullable Chip getChipByPrice(Game game, double money) {
        for (Chip chip : getChipsByGame(game)) {
            if (money == chip.price()) return chip;
        }
        return null;
    }

    public @NotNull Chip getExistingOrBetAll(Game game, double money) {
        // If the bet-all money is the same of one chip from chips.yml, use that chip.
        Chip chip = plugin.getChipManager().getChipByPrice(game, money);
        if (chip != null) return chip;

        // If the @bet-all item has URL, use it. Otherwise, use a default one.
        String skin = plugin.getConfig().getString("chip-menu.items.bet-all.url", BET_ALL_SKIN);

        return new Chip("bet-all", skin, money);
    }

    public Double getMinAmount(Game game) {
        // If no chip, return "unreacheable" value.
        List<Chip> chips = getChipsByGame(game);
        return chips.isEmpty() ? Double.MAX_VALUE : chips.get(0).price();
    }

    public Double getMaxAmount(Game game) {
        // If no chip, return "unreacheable" value.
        List<Chip> chips = getChipsByGame(game);
        return chips.isEmpty() ? Double.MAX_VALUE : chips.get(chips.size() - 1).price();
    }

    public boolean hasEnoughMoney(Game game, Player player) {
        double minAmount = getMinAmount(game);
        if (plugin.getEconomyExtension().has(player, minAmount)) return true;

        plugin.getMessages().send(player,
                Messages.Message.MIN_REQUIRED,
                message -> message.replace("%money%", PluginUtils.format(minAmount)));
        return false;
    }

    public @Nullable Chip getByName(String name) {
        for (Chip chip : chips) {
            if (chip.name().equalsIgnoreCase(name)) return chip;
        }
        return null;
    }

    public ItemStack createChipItem(@NotNull Chip chip, String guiName, boolean isShop) {
        ItemBuilder builder = plugin.getItem(guiName + ".items.chip");

        if (isShop) {
            String displayName = chip.displayName();
            if (displayName != null) builder.setDisplayName(displayName);

            List<String> lore = chip.lore();
            if (lore != null) builder.setLore(lore);
        }

        return builder
                .setHead(chip.url(), true)
                .replace("%money%", PluginUtils.format(chip.price()))
                .setData(plugin.getChipNameKey(), PersistentDataType.STRING, chip.name())
                .build();
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