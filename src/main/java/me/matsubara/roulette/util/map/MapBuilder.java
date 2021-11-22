package me.matsubara.roulette.util.map;

import com.cryptomorin.xseries.ReflectionUtils;
import me.matsubara.roulette.RoulettePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.*;
import org.bukkit.map.MapView.Scale;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"unused", "UnusedReturnValue"})
public final class MapBuilder {

    private final RoulettePlugin PLUGIN = JavaPlugin.getPlugin(RoulettePlugin.class);

    private MapView view;
    private BufferedImage image;
    private ItemStack item;

    private Integer id;

    private final List<Text> texts;
    private final MapCursorCollection cursors;

    private boolean rendered;
    private boolean renderOnce;

    private final boolean isNewVersion;

    public MapBuilder() {
        cursors = new MapCursorCollection();
        texts = new ArrayList<>();
        rendered = false;
        renderOnce = true;
        isNewVersion = ReflectionUtils.VER > 12;
    }

    /**
     * Get the image that's being used.
     *
     * @return the image used.
     */
    public BufferedImage getImage() {
        return image;
    }

    /**
     * Set an image to be used.
     *
     * @param image the buffered image to use.
     * @return the instance of this class.
     */
    public MapBuilder setImage(@NotNull BufferedImage image, boolean resize) {
        // Resize image to fit in the map.
        if (resize && image.getWidth() != 128 && image.getHeight() != 128) {
            this.image = MapPalette.resizeImage(image);
        } else {
            this.image = image;
        }
        return this;
    }

    /**
     * Set the id for this map.
     *
     * @param id id for this map.
     */
    public MapBuilder setId(int id) {
        this.id = id;
        return this;
    }

    /**
     * Set and image to be used.
     *
     * @param x,   y the coordinates to add the text.
     * @param font the font to be used.
     * @param text the string that will be displayed.
     * @return the instance of this class.
     */
    public MapBuilder addText(int x, int y, @NotNull MapFont font, @NotNull String text) {
        if (font.isValid(text)) this.texts.add(new Text(x, y, font, text));
        else PLUGIN.getLogger().warning("The line for the map (\"" + text + "\") contains invalid characters!");
        return this;
    }

    /**
     * Gets the list of all the texts used.
     *
     * @return a List of all the texts.
     */
    public List<Text> getTexts() {
        return texts;
    }

    /**
     * Adds a cursor to the map.
     *
     * @param x,        y the coordinates to add the cursor.
     * @param direction the direction to display the cursor.
     * @param type      the type of the cursor.
     * @return the instance of this class.
     */
    @SuppressWarnings("deprecation")
    public MapBuilder addCursor(int x, int y, @NotNull CursorDirection direction, @NotNull CursorType type) {
        cursors.addCursor(x, y, (byte) direction.getId(), (byte) type.getId());
        return this;
    }

    /**
     * Gets all the currently used cursors.
     *
     * @return a MapCursorCollection with all current cursors.
     */
    public MapCursorCollection getCursors() {
        return cursors;
    }

    /**
     * Sets whether the image should only be rendered once.
     * Good for static images and reduces lag.
     *
     * @param renderOnce the value to determine if it's going to be rendered once.
     * @return the instance of this class.
     */
    public MapBuilder setRenderOnce(boolean renderOnce) {
        this.renderOnce = renderOnce;
        return this;
    }

    /**
     * Builds an ItemStack of the map.
     *
     * @return the ItemStack of the map containing what's been set from the above methods.
     */
    @SuppressWarnings({"deprecation", "ConstantConditions"})
    public MapBuilder build() {
        Material material = isNewVersion ? Material.FILLED_MAP : Material.valueOf("MAP");

        if (id != null) {
            item = new ItemStack(material, 1, (short) id.intValue());

            MapMeta meta = (MapMeta) item.getItemMeta();
            if (ReflectionUtils.VER > 12) {
                meta.setMapId(id);

                if (!meta.hasMapId()) {
                    Bukkit.getMap(id);
                }

                view = meta.getMapView();
            } else {
                try {
                    //noinspection JavaReflectionMemberAccess
                    view = (MapView) Bukkit.class.getMethod("getMap", short.class).invoke(null, id.shortValue());
                } catch (ReflectiveOperationException exception) {
                    exception.printStackTrace();
                }
            }
        } else {
            item = new ItemStack(material);
            view = Bukkit.createMap(Bukkit.getWorlds().get(0));
        }

        view.setScale(Scale.NORMAL);
        view.getRenderers().forEach(view::removeRenderer);
        view.addRenderer(new MapRenderer() {
            @Override
            public void render(@NotNull MapView mapView, @NotNull MapCanvas mapCanvas, @NotNull Player player) {
                if (rendered && renderOnce) return;

                if (player.isOnline()) {
                    if (image != null) {
                        mapCanvas.drawImage(0, 0, image);
                    }

                    if (!texts.isEmpty()) {
                        // Write text centered.
                        texts.forEach(text -> mapCanvas.drawText((128 - MinecraftFont.Font.getWidth(text.getMessage())) / 2,
                                text.getY(),
                                text.getFont(),
                                text.getMessage()));
                    }

                    if (cursors.size() > 0) {
                        mapCanvas.setCursors(cursors);
                    }

                    rendered = true;
                }
            }
        });

        if (isNewVersion) {
            MapMeta mapMeta = (MapMeta) item.getItemMeta();
            if (mapMeta != null) mapMeta.setMapView(view);
            item.setItemMeta(mapMeta);
        } else {
            item.setDurability(getMapId(view));
        }
        return this;
    }

    /**
     * Check if the item being used is built.
     *
     * @return if the item is built.
     */
    public boolean isBuilt() {
        return item != null;
    }

    /**
     * Get the item that's being used.
     *
     * @return the item used.
     */
    @Nullable
    public ItemStack getItem() {
        return item;
    }

    /**
     * Get the map view that's being used.
     *
     * @return the map view used.
     */
    public MapView getView() {
        return view;
    }

    /**
     * Gets a map id cross-version using reflection.
     *
     * @param mapView the map to get the id.
     * @return the instance of this class.
     */
    public static short getMapId(@NotNull MapView mapView) {
        try {
            return (short) mapView.getId();
        } catch (NoSuchMethodError error) {
            try {
                return (short) Class.forName("org.bukkit.map.MapView").getMethod("getId", new Class[0]).invoke(mapView, new Object[0]);
            } catch (ReflectiveOperationException exception) {
                exception.printStackTrace();
                return -1;
            }
        }
    }

    /**
     * An enum containing user-friendly cursor directions.
     * Instead of using the integers, you can instead use this enum.
     */
    public enum CursorDirection {
        SOUTH(0),
        SOUTH_WEST_SOUTH(1),
        SOUTH_WEST(2),
        SOUTH_WEST_WEST(3),
        WEST(4),
        NORTH_WEST_WEST(5),
        NORTH_WEST(6),
        NORTH_WEST_NORTH(7),
        NORTH(8),
        NORTH_EAST_NORTH(9),
        NORTH_EAST(10),
        NORTH_EAST_EAST(11),
        EAST(12),
        SOUTH_EAST_EAST(13),
        SOUNT_EAST(14),
        SOUTH_EAST_SOUTH(15);

        private final int id;

        CursorDirection(int id) {
            this.id = id;
        }

        /**
         * Returns the actual integer to use.
         *
         * @return the integer of the specified enum type.
         */
        public int getId() {
            return this.id;
        }
    }

    /**
     * An enum containing user-friendly cursor types.
     * Instead of using the integers, you can instead use this enum.
     */
    public enum CursorType {
        WHITE_POINTER(0),
        GREEN_POINTER(1),
        RED_POINTER(2),
        BLUE_POINTER(3),
        WHITE_CLOVER(4),
        RED_BOLD_POINTER(5),
        WHITE_DOT(6),
        LIGHT_BLUE_SQUARE(7);

        private final int id;

        CursorType(int id) {
            this.id = id;
        }

        /**
         * Returns the actual integer to use.
         *
         * @return the integer of the specified enum type.
         */
        public int getId() {
            return this.id;
        }
    }
}