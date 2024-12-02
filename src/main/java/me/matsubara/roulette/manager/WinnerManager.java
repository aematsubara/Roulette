package me.matsubara.roulette.manager;

import lombok.Getter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.file.Config;
import me.matsubara.roulette.manager.data.DataManager;
import me.matsubara.roulette.manager.data.MapRecord;
import me.matsubara.roulette.manager.data.PlayerResult;
import me.matsubara.roulette.manager.data.RouletteSession;
import me.matsubara.roulette.util.PluginUtils;
import me.matsubara.roulette.util.map.MapBuilder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
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
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

@Getter
public final class WinnerManager implements Listener {

    private final RoulettePlugin plugin;
    private BufferedImage image;

    public WinnerManager(RoulettePlugin plugin) {
        this.plugin = plugin;
        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
        try {
            this.image = ImageIO.read(plugin.saveFile("background.png"));
        } catch (IOException exception) {
            plugin.getLogger().warning("The file {background.png} couldn't be found.");
        }
    }

    @EventHandler
    public void onMapInitialize(@NotNull MapInitializeEvent event) {
        MapView view = event.getMap();

        DataManager dataManager = plugin.getDataManager();
        for (MapRecord record : dataManager.getMaps()) {
            if (record.mapId() != view.getId()) continue;

            RouletteSession session = dataManager.getSessionByUUID(record.sessionUUID());
            if (session == null) break;

            render(record.playerUUID(), session, view);
            break;
        }
    }

    public @Nullable Map.Entry<Integer, ItemStack> render(UUID playerUUID, RouletteSession session) {
        return render(playerUUID, session, null);
    }

    public @Nullable Map.Entry<Integer, ItemStack> render(UUID playerUUID, RouletteSession session, @Nullable MapView view) {
        MapBuilder builder = new MapBuilder(plugin, playerUUID, session);
        if (image != null) builder.setImage(image, true);

        FileConfiguration config = plugin.getConfig();
        ConfigurationSection section = config.getConfigurationSection("map-image.lines");
        if (section == null) return null;

        MapFont font = MinecraftFont.Font;
        for (String path : section.getKeys(false)) {
            List<String> lines = config.getStringList("map-image.lines." + path + ".text");

            String coordsPath = "map-image.lines." + path + ".coords.";
            int x = config.getInt(coordsPath + ".x", -1);
            int y = config.getInt(coordsPath + ".y", -1);
            int height = font.getHeight(), amountOfLines = lines.size();

            if (y == -1) {
                y = (128 - (amountOfLines * height + (amountOfLines - 1))) / 2;
            }

            for (int i = 0; i < amountOfLines; i++) {
                String line = loreReplacer(playerUUID, session, lines.get(i));
                builder.addText(x, y + i * (height + 1), line);
            }
        }

        if (view != null) {
            view.setScale(MapView.Scale.NORMAL);
            view.getRenderers().forEach(view::removeRenderer);
            view.addRenderer(builder);
            return null;
        }

        ItemStack item = builder.build();
        return new AbstractMap.SimpleEntry<>(builder.getView().getId(), item);
    }

    public @NotNull String loreReplacer(UUID playerUUID, @NotNull RouletteSession session, @NotNull String string) {
        double money = session.results().stream()
                .filter(PlayerResult::won)
                .mapToDouble(plugin::getExpectedMoney)
                .sum();

        OfflinePlayer winner = Bukkit.getOfflinePlayer(playerUUID);
        String date = new SimpleDateFormat(Config.DATE_FORMAT.asString())
                .format(new Date(session.timestamp()));

        return string
                .replace("%player%", Objects.requireNonNullElse(winner.getName(), "???"))
                .replace("%money%", PluginUtils.format(money))
                .replace("%date%", date)
                .replace("%table%", session.name());
    }
}