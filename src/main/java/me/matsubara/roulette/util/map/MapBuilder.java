package me.matsubara.roulette.util.map;

import lombok.Getter;
import lombok.Setter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.manager.ConfigManager;
import me.matsubara.roulette.util.ItemBuilder;
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
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public final class MapBuilder extends MapRenderer {

    private final RoulettePlugin plugin;
    private final Graphics2D dummyGraphics = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB).createGraphics();
    private final Font font = new Font("", Font.PLAIN, 12);

    private MapView view;
    private BufferedImage image;
    private ItemStack item;

    private final List<Text> texts = new ArrayList<>();

    private boolean rendered;
    private boolean built;

    private static final int MIDDLE_Y = (128 - 8) / 2;

    public MapBuilder(RoulettePlugin plugin) {
        this.plugin = plugin;
    }

    public void setImage(@NotNull BufferedImage image, boolean resize) {
        // Resize image to fit in the map.
        if (resize && image.getWidth() != 128 && image.getHeight() != 128) {
            this.image = MapPalette.resizeImage(image);
        } else {
            this.image = image;
        }
    }

    public void addText(int x, int y, @NotNull MapFont font, @NotNull String text) {
        texts.add(new Text(x, y, font, text));
    }

    @Override
    public void render(@NotNull MapView view, @NotNull MapCanvas canvas, @NotNull Player player) {
        if (rendered) return;

        if (image != null) {
            drawImage(image, 0, 0, canvas);
        }

        // Write text centered.
        texts.forEach(text -> drawText(text.getMessage(), -1, text.getY(), canvas));

        rendered = true;
    }

    @SuppressWarnings("deprecation")
    private void drawText(String message, int x, int y, MapCanvas canvas) {
        image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);

        Graphics2D graphics = image.createGraphics();
        graphics.setFont(font);
        graphics.setColor(MapPalette.getColor(MapPalette.DARK_GRAY));

        x = x == -1 ? (int) ((128 - getStringWidth(message)) / 2) : x;
        y = y == -1 ? MIDDLE_Y : y;

        // We need to add an offset because the coords were tracked with the default minecraft font.
        graphics.drawString(message, x, y + 7);
        graphics.dispose();

        drawImage(image, 0, 0, canvas);
    }

    private double getStringWidth(String string) {
        dummyGraphics.setFont(font);
        return dummyGraphics.getFontMetrics().getStringBounds(string, dummyGraphics).getWidth();
    }

    @SuppressWarnings("deprecation")
    private void drawImage(@NotNull BufferedImage image, int x, int y, MapCanvas canvas) {
        int width = image.getWidth();
        int height = image.getHeight();

        int[] pixels = new int[width * height];
        image.getRGB(0, 0, width, height, pixels, 0, width);

        Byte[] bytes = new Byte[width * height];
        for (int i = 0; i < pixels.length; i++) {
            bytes[i] = (pixels[i] >> 24) == 0x00 ? null : MapPalette.matchColor(new Color(pixels[i], true));
        }

        int tX = x == -1 ? ((128 - width) / 2) : x;
        int tY = y == -1 ? ((128 - height) / 2) : y;

        for (int xx = 0; xx < this.image.getWidth(); xx++) {
            for (int yy = 0; yy < this.image.getHeight(); yy++) {
                Byte color = bytes[yy * this.image.getWidth() + xx];
                if (color != null) canvas.setPixel(tX + xx, tY + yy, color);
            }
        }
    }

    public @Nullable ItemStack build(String playerName, String moneyFormatted, String originalFormatted, String date, String selected, String winner) {
        if (built) return null;

        view = Bukkit.createMap(Bukkit.getWorlds().get(0));
        view.setScale(Scale.NORMAL);
        view.getRenderers().forEach(view::removeRenderer);
        view.addRenderer(this);

        built = (item = new ItemBuilder(Material.FILLED_MAP)
                .setDisplayName(ConfigManager.Config.MAP_IMAGE_ITEM_DISPLAY_NAME.asString())
                .setLore(ConfigManager.Config.MAP_IMAGE_ITEM_LORE.asList())
                .replace("%player%", playerName)
                .replace("%money%", moneyFormatted)
                .replace("%original-money%", originalFormatted)
                .replace("%date%", date)
                .replace("%selected-slot%", selected)
                .replace("%winner-slot%", winner)
                .setMapView(view)
                .build()) != null;

        return item;
    }
}