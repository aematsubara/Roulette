package me.matsubara.roulette.manager;

import lombok.Getter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.data.Chip;
import me.matsubara.roulette.util.PluginUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
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
            chips.sort(Comparator.comparing(Chip::getPrice));
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

    public Double getMinAmount() {
        // If no chip, return "unreacheable" value.
        if (chips.isEmpty()) return Double.MAX_VALUE;
        return chips.get(0).getPrice();
    }

    public @Nullable Chip getByName(String name) {
        for (Chip chip : chips) {
            if (chip.getName().equalsIgnoreCase(name)) return chip;
        }
        return null;
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