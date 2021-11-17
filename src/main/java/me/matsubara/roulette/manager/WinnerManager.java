package me.matsubara.roulette.manager;

import com.google.common.base.Strings;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.WinType;
import me.matsubara.roulette.game.data.Slot;
import me.matsubara.roulette.manager.winner.Winner;
import me.matsubara.roulette.util.map.MapBuilder;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapFont;
import org.bukkit.map.MinecraftFont;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;

@SuppressWarnings("unused")
public final class WinnerManager {

    private final RoulettePlugin plugin;
    private final Set<Winner> winners;

    private File file;
    private FileConfiguration configuration;

    public WinnerManager(RoulettePlugin plugin) {
        this.plugin = plugin;
        this.winners = new HashSet<>();

        // Load image if not loaded.
        File image = new File(plugin.getDataFolder(), "image.png");
        if (!image.exists()) plugin.saveResource("image.png", false);

        load();
    }

    private void load() {
        file = new File(plugin.getDataFolder(), "winners.yml");
        if (!file.exists()) {
            plugin.saveResource("winners.yml", false);
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
        winners.clear();

        ConfigurationSection winners = getConfig().getConfigurationSection("winners");
        if (winners == null) return;

        for (String path : winners.getKeys(false)) {
            Winner winner = new Winner(UUID.fromString(path));

            ConfigurationSection games = getConfig().getConfigurationSection("winners." + path);
            if (games == null) continue;

            for (String innerPath : games.getKeys(false)) {
                String game = getConfig().getString("winners." + path + "." + innerPath + ".game");
                Integer mapId;
                try {
                    String asString = getConfig().getString("winners." + path + "." + innerPath + ".map-id");
                    //noinspection ConstantConditions
                    mapId = Integer.parseInt(asString);
                } catch (NumberFormatException exception) {
                    mapId = null;
                }
                double money = getConfig().getDouble("winners." + path + "." + innerPath + ".money");
                long date = getConfig().getLong("winners." + path + "." + innerPath + ".date");
                Slot slot = Slot.valueOf(getConfig().getString("winners." + path + "." + innerPath + ".slot"));
                Slot winnerSlot = Slot.valueOf(getConfig().getString("winners." + path + "." + innerPath + ".winner"));

                WinType winType = WinType.valueOf(getConfig().getString("winners." + path + "." + innerPath + ".win-type"));

                double originalMoney = getConfig().getDouble("winners." + path + "." + innerPath + ".original-money");

                winner.add(game, mapId, money, date, slot, winnerSlot, winType, originalMoney);
            }

            this.winners.add(winner);
        }

        // Render maps.
        this.winners.forEach(this::renderMap);
    }

    public void saveWinner(Winner winner) {
        winners.add(winner);

        for (Winner.WinnerData data : winner.getWinnerData()) {
            int index = winner.getWinnerData().indexOf(data) + 1;
            getConfig().set("winners." + winner.getUUID() + "." + index + ".game", data.getGame());
            getConfig().set("winners." + winner.getUUID() + "." + index + ".map-id", data.getMapId());
            getConfig().set("winners." + winner.getUUID() + "." + index + ".money", data.getMoney());
            getConfig().set("winners." + winner.getUUID() + "." + index + ".date", data.getDate());
            getConfig().set("winners." + winner.getUUID() + "." + index + ".slot", data.getSelected().name());
            getConfig().set("winners." + winner.getUUID() + "." + index + ".winner", data.getWinner().name());
            getConfig().set("winners." + winner.getUUID() + "." + index + ".win-type", data.getWinType().name());
            getConfig().set("winners." + winner.getUUID() + "." + index + ".original-money", data.getOriginalMoney());
        }

        saveConfig();
    }

    private void renderMap(Winner winner) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(winner.getUUID());
        if (player.getName() == null) return;

        for (Winner.WinnerData data : winner.getWinnerData()) {
            renderMap(data.getMapId(), player.getName(), data.getMoney());
        }
    }

    public Map.Entry<Integer, ItemStack> renderMap(@Nullable Integer mapId, String playerName, double money) {
        try {
            BufferedImage image = ImageIO.read(new File(plugin.getDataFolder(), "image.png"));
            MapFont font = MinecraftFont.Font;

            MapBuilder builder = new MapBuilder()
                    .setRenderOnce(true)
                    .setImage(image, true);

            if (mapId != null) builder.setId(mapId);

            for (String text : ConfigManager.Config.MAP_IMAGE_TEXT.asList()) {
                if (Strings.isNullOrEmpty(text) || text.equalsIgnoreCase("none")) continue;
                String[] split = StringUtils.split(StringUtils.deleteWhitespace(text), ',');
                if (split.length == 0) continue;

                int posY;
                try {
                    posY = Integer.parseInt(StringUtils.deleteWhitespace(split[0]));
                } catch (NumberFormatException exception) {
                    continue;
                }

                builder.addText(0, posY, font, split[1]
                        .replace("%player%", playerName)
                        .replace("%money%", plugin.getEconomy().format(money)));
            }

            builder.build();

            return new AbstractMap.SimpleEntry<>((int) MapBuilder.getMapId(builder.getView()), builder.build().getItem());
        } catch (IOException exception) {
            plugin.getLogger().warning("The file \"image.png\" couldn't be found.");
        }

        return null;
    }

    public void deleteWinner(Winner winner) {
        getConfig().set("winners." + winner.getUUID(), null);
        saveConfig();
    }

    public Winner getByUniqueId(UUID uuid) {
        for (Winner winner : winners) {
            if (winner.getUUID().equals(uuid)) return winner;
        }
        return null;
    }

    private void saveConfig() {
        try {
            getConfig().save(file);
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

    public Set<Winner> getWinnersSet() {
        return winners;
    }

    public FileConfiguration getConfig() {
        return configuration;
    }
}