package me.matsubara.roulette.manager;

import com.google.common.base.Strings;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.WinType;
import me.matsubara.roulette.game.data.Slot;
import me.matsubara.roulette.manager.winner.Winner;
import me.matsubara.roulette.util.Lang3Utils;
import me.matsubara.roulette.util.PluginUtils;
import me.matsubara.roulette.util.map.MapBuilder;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapFont;
import org.bukkit.map.MinecraftFont;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

@SuppressWarnings("unused")
public final class WinnerManager {

    private final RoulettePlugin plugin;
    private final Set<Winner> winners;

    private File file;
    private FileConfiguration configuration;
    private BufferedImage image;

    public WinnerManager(RoulettePlugin plugin) {
        this.plugin = plugin;
        this.winners = new HashSet<>();

        // Load image if not loaded.
        File image = new File(plugin.getDataFolder(), "image.png");
        if (!image.exists()) plugin.saveResource("image.png", false);

        try {
            this.image = ImageIO.read(image);
        } catch (IOException exception) {
            plugin.getLogger().warning("The file \"image.png\" couldn't be found.");
        }

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

    public void renderMaps() {
        winners.forEach(this::renderMap);
    }

    public void renderMap(Winner winner) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(winner.getUUID());

        String playerName = player.getName();
        if (playerName == null) return;

        for (Winner.WinnerData data : winner.getWinnerData()) {
            if (data.getMapId() != -1) renderMap(playerName, data);
        }
    }

    public Map.Entry<Winner.WinnerData, ItemStack> renderMap(String playerName, Winner.WinnerData data) {
        MapFont font = MinecraftFont.Font;

        MapBuilder builder = new MapBuilder().setRenderOnce(true);
        if (image != null) builder.setImage(image, true);

        Integer mapId = data.getMapId();
        if (mapId != null) builder.setId(mapId);

        for (String text : ConfigManager.Config.MAP_IMAGE_TEXT.asList()) {
            if (Strings.isNullOrEmpty(text) || text.equalsIgnoreCase("none")) continue;
            String[] split = Lang3Utils.split(Lang3Utils.deleteWhitespace(text), ',');
            if (split.length == 0) continue;

            int posY;
            try {
                posY = Integer.parseInt(Lang3Utils.deleteWhitespace(split[0]));
            } catch (NumberFormatException exception) {
                continue;
            }

            Economy economy = plugin.getEconomy();
            builder.addText(0, posY, font, split[1]
                    .replace("%player%", playerName)
                    .replace("%money%", economy.format(data.getMoney()))
                    .replace("%original-money%", economy.format(data.getOriginalMoney()))
                    .replace("%date%", new SimpleDateFormat("dd-MM-yyyy").format(new Date(data.getDate())))
                    .replace("%selected-slot%", PluginUtils.getSlotName(data.getSelected()))
                    .replace("%winner-slot%", PluginUtils.getSlotName(data.getWinner()))
                    .replace("%type%", data.getWinType().getFormatName()));
        }

        // For some reason, the map doesn't exist in the server (probably a crash?).
        if (builder.build().getView() == null) return null;

        data.setMapId((int) MapBuilder.getMapId(builder.getView()));
        return new AbstractMap.SimpleEntry<>(data, builder.getItem());
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