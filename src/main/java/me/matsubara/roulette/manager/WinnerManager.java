package me.matsubara.roulette.manager;

import com.google.common.base.Strings;
import lombok.Getter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.WinType;
import me.matsubara.roulette.game.data.Slot;
import me.matsubara.roulette.manager.winner.Winner;
import me.matsubara.roulette.util.PluginUtils;
import me.matsubara.roulette.util.map.MapBuilder;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.MapInitializeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapFont;
import org.bukkit.map.MapView;
import org.bukkit.map.MinecraftFont;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

@Getter
public final class WinnerManager implements Listener {

    private final RoulettePlugin plugin;
    private final Set<Winner> winners;

    private File file;
    private FileConfiguration configuration;
    private BufferedImage image;

    public WinnerManager(RoulettePlugin plugin) {
        this.plugin = plugin;
        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
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

    @EventHandler
    public void onMapInitialize(@NotNull MapInitializeEvent event) {
        MapView view = event.getMap();

        for (Winner winner : winners) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(winner.getUuid());

            String playerName = player.getName();
            if (playerName == null) continue;

            for (Winner.WinnerData data : winner.getWinnerData()) {
                if (data.hasValidId() && data.getMapId() == view.getId()) {
                    if (render(playerName, data, view) == null) break;
                }
            }
        }
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

        ConfigurationSection winners = configuration.getConfigurationSection("winners");
        if (winners == null) return;

        for (String path : winners.getKeys(false)) {
            Winner winner = new Winner(UUID.fromString(path));

            ConfigurationSection games = configuration.getConfigurationSection("winners." + path);
            if (games == null) continue;

            for (String innerPath : games.getKeys(false)) {
                String game = configuration.getString("winners." + path + "." + innerPath + ".game");
                Integer mapId;
                try {
                    String asString = configuration.getString("winners." + path + "." + innerPath + ".map-id");
                    //noinspection ConstantConditions
                    mapId = Integer.parseInt(asString);
                } catch (NumberFormatException exception) {
                    mapId = null;
                }
                double money = configuration.getDouble("winners." + path + "." + innerPath + ".money");
                long date = configuration.getLong("winners." + path + "." + innerPath + ".date");
                Slot slot = Slot.valueOf(configuration.getString("winners." + path + "." + innerPath + ".slot"));
                Slot winnerSlot = Slot.valueOf(configuration.getString("winners." + path + "." + innerPath + ".winner"));

                WinType winType = WinType.valueOf(configuration.getString("winners." + path + "." + innerPath + ".win-type"));

                double originalMoney = configuration.getDouble("winners." + path + "." + innerPath + ".original-money");

                winner.add(game, mapId, money, date, slot, winnerSlot, winType, originalMoney);
            }

            this.winners.add(winner);
        }
    }

    public void saveWinner(Winner winner) {
        winners.add(winner);

        for (Winner.WinnerData data : winner.getWinnerData()) {
            UUID uuid = winner.getUuid();
            int index = winner.getWinnerData().indexOf(data) + 1;
            configuration.set("winners." + uuid + "." + index + ".game", data.getGame());
            configuration.set("winners." + uuid + "." + index + ".map-id", data.getMapId());
            configuration.set("winners." + uuid + "." + index + ".money", data.getMoney());
            configuration.set("winners." + uuid + "." + index + ".date", data.getDate());
            configuration.set("winners." + uuid + "." + index + ".slot", data.getSelected().name());
            configuration.set("winners." + uuid + "." + index + ".winner", data.getWinner().name());
            configuration.set("winners." + uuid + "." + index + ".win-type", data.getType().name());
            configuration.set("winners." + uuid + "." + index + ".original-money", data.getOriginalMoney());
        }

        saveConfig();
    }

    public Map.@Nullable Entry<Winner.WinnerData, ItemStack> render(String playerName, Winner.WinnerData data, @Nullable MapView view) {
        MapFont font = MinecraftFont.Font;

        MapBuilder builder = new MapBuilder(plugin);
        if (image != null) builder.setImage(image, true);

        String moneyFormatted = PluginUtils.format(data.getMoney());
        String originalFormatted = PluginUtils.format(data.getOriginalMoney());
        String date = new SimpleDateFormat(ConfigManager.Config.MAP_IMAGE_DATE_FORMAT.asString()).format(new Date(data.getDate()));
        String selected = PluginUtils.getSlotName(data.getSelected());
        String winner = PluginUtils.getSlotName(data.getWinner());

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
                    .replace("%money%", moneyFormatted)
                    .replace("%original-money%", originalFormatted)
                    .replace("%date%", date)
                    .replace("%selected-slot%", selected)
                    .replace("%winner-slot%", winner));
        }

        if (view != null) {
            view.setScale(MapView.Scale.NORMAL);
            view.getRenderers().forEach(view::removeRenderer);
            view.addRenderer(builder);
            return null;
        }

        ItemStack item = builder.build(playerName, moneyFormatted, originalFormatted, date, selected, winner);
        data.setMapId(builder.getView().getId());
        return new AbstractMap.SimpleEntry<>(data, item);
    }

    @SuppressWarnings("unused")
    public void deleteWinner(@NotNull Winner winner) {
        configuration.set("winners." + winner.getUuid(), null);
        saveConfig();
    }

    public @Nullable Winner getByUniqueId(UUID uuid) {
        for (Winner winner : winners) {
            if (winner.getUuid().equals(uuid)) return winner;
        }
        return null;
    }

    private void saveConfig() {
        try {
            configuration.save(file);
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
}