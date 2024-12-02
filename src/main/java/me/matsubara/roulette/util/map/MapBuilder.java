package me.matsubara.roulette.util.map;

import com.cryptomorin.xseries.reflection.XReflection;
import lombok.Getter;
import lombok.Setter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.file.Config;
import me.matsubara.roulette.manager.data.PlayerResult;
import me.matsubara.roulette.manager.data.RouletteSession;
import me.matsubara.roulette.util.PluginUtils;
import net.md_5.bungee.api.ChatColor;
import org.apache.commons.lang3.function.TriFunction;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.*;
import org.bukkit.map.MapView.Scale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
@Setter
public final class MapBuilder extends MapRenderer {

    private final RoulettePlugin plugin;
    private final UUID playerUUID;
    private final RouletteSession session;
    private final List<Text> texts = new ArrayList<>();
    private final @SuppressWarnings("deprecation") Color color = MapPalette.getColor(MapPalette.DARK_GRAY); // Default color.
    private final MapFont font = MinecraftFont.Font;

    private MapView view;
    private BufferedImage image;
    private ItemStack item;
    private boolean rendered;

    private static final Pattern ACCENT_PATTERN = Pattern.compile("\\p{M}");
    public static TriFunction<RoulettePlugin, PlayerResult, String, String> MAP_REPLACER = new TriFunction<>() {
        @Override
        public @NotNull String apply(@NotNull RoulettePlugin plugin, @NotNull PlayerResult result, @NotNull String string) {
            RouletteSession session = result.session();
            if (session == null) return string;

            String playerName = Objects.requireNonNullElse(Bukkit.getOfflinePlayer(result.playerUUID()).getName(), "???");
            String moneyFormatted = PluginUtils.format(plugin.getExpectedMoney(result));
            String originalFormatted = PluginUtils.format(result.money());
            String date = new SimpleDateFormat(Config.DATE_FORMAT.asString()).format(new Date(session.timestamp()));
            String selected = PluginUtils.getSlotName(result.slot());
            String winner = PluginUtils.getSlotName(session.slot());
            return string.replace("%player%", playerName)
                    .replace("%money%", moneyFormatted)
                    .replace("%original-money%", originalFormatted)
                    .replace("%date%", date)
                    .replace("%selected-slot%", org.bukkit.ChatColor.stripColor(selected))
                    .replace("%winner-slot%", org.bukkit.ChatColor.stripColor(winner))
                    .replace("%table%", session.name());
        }
    };

    public MapBuilder(RoulettePlugin plugin, UUID playerUUID, RouletteSession session) {
        this.plugin = plugin;
        this.playerUUID = playerUUID;
        this.session = session;
    }

    public void setImage(@NotNull BufferedImage image, boolean resize) {
        // Resize image to fit in the map.
        if (resize && image.getWidth() != 128 && image.getHeight() != 128) {
            this.image = MapPalette.resizeImage(image);
        } else {
            this.image = image;
        }
    }

    public void addText(int x, int y, @NotNull String text) {
        texts.add(new Text(x, y, text));
    }

    @Override
    public void render(@NotNull MapView view, @NotNull MapCanvas canvas, @NotNull Player player) {
        if (rendered) return;

        if (image != null) {
            canvas.drawImage(0, 0, image);
        }

        // Write text centered.
        for (Text text : texts) {
            try {
                drawText(canvas, text.x(), text.y(), removeAccents(text.message()));
            } catch (IllegalArgumentException exception) {
                // Invalid characters or colors.
            }
        }

        rendered = true;
    }

    private String removeAccents(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return ACCENT_PATTERN.matcher(normalized).replaceAll("");
    }

    @SuppressWarnings("deprecation")
    public void drawText(MapCanvas canvas, int x, int y, @NotNull String text) {
        Map<Pair<Integer, Integer>, Color> colorMap = new LinkedHashMap<>();

        Matcher matcher = PluginUtils.PATTERN.matcher(text);
        StringBuilder buffer = new StringBuilder();
        while (matcher.find()) {
            int start = matcher.start(), end = matcher.end();
            colorMap.put(Pair.of(start, end), ChatColor.of(matcher.group(1)).getColor());
            matcher.appendReplacement(buffer, ""); // Remove pattern.
        }

        // Try to center.
        String temp = matcher.appendTail(buffer).toString();
        if (x == -1) x = (128 - font.getWidth(temp)) / 2;

        for (int i = 0; i < text.length(); i++) {
            MapFont.CharacterSprite sprite = font.getChar(text.charAt(i));
            if (sprite == null) continue;

            // Don't draw color characters.
            Map.Entry<Pair<Integer, Integer>, Color> colorEntry = getColor(colorMap, i);
            if (colorEntry != null) {
                Pair<Integer, Integer> coords = colorEntry.getKey();
                Integer start = coords.getKey(), end = coords.getValue();
                if (i >= start && i < end) continue;
            }

            // Get color or use default.
            Color color = colorEntry != null ? colorEntry.getValue() : this.color;
            byte byteColor = MapPalette.matchColor(color);

            for (int h = 0; h < font.getHeight(); h++) {
                for (int w = 0; w < sprite.getWidth(); w++) {
                    if (!sprite.get(h, w)) continue;

                    int targetX = x + w;
                    int targetY = y + h;

                    if (XReflection.supports(19)) {
                        canvas.setPixelColor(targetX, targetY, color);
                    } else {
                        canvas.setPixel(targetX, targetY, byteColor);
                    }
                }
            }

            x += sprite.getWidth() + 1;
        }
    }

    private @Nullable Map.Entry<Pair<Integer, Integer>, Color> getColor(@NotNull Map<Pair<Integer, Integer>, Color> colorMap, int index) {
        Map.Entry<Pair<Integer, Integer>, Color> color = null;
        for (Map.Entry<Pair<Integer, Integer>, Color> entry : colorMap.entrySet()) {
            Integer start = entry.getKey().getKey();
            if (index >= start) color = entry;
        }
        return color;
    }

    public @Nullable ItemStack build() {
        if (item != null) return item;

        view = Bukkit.createMap(Bukkit.getWorlds().get(0));
        view.setScale(Scale.NORMAL);
        view.getRenderers().forEach(view::removeRenderer);
        view.addRenderer(this);

        return item = plugin.getItem("map-image.item")
                .setType(Material.FILLED_MAP)
                .replace(line -> plugin.getWinnerManager().loreReplacer(playerUUID, session, line))
                .setMapView(view)
                .build();
    }
}