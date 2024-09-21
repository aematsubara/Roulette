package me.matsubara.roulette.util;

import com.cryptomorin.xseries.reflection.XReflection;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import lombok.Getter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.data.Slot;
import me.matsubara.roulette.manager.ConfigManager;
import net.md_5.bungee.api.ChatColor;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PluginUtils {

    public static Random RANDOM = new Random();
    public static PersistentDataType<byte[], UUID> UUID_TYPE = new UUIDTagType();
    public static final Pattern PATTERN = Pattern.compile("&(#[\\da-fA-F]{6})");

    private static final RoulettePlugin PLUGIN = JavaPlugin.getPlugin(RoulettePlugin.class);
    private static final double BUKKIT_VIEW_DISTANCE = Math.pow(Bukkit.getViewDistance() << 4, 2);

    private static final Slot[][] TABLE_GRID = {
            {Slot.SLOT_3, Slot.SLOT_6, Slot.SLOT_9, Slot.SLOT_12, Slot.SLOT_15, Slot.SLOT_18, Slot.SLOT_21, Slot.SLOT_24, Slot.SLOT_27, Slot.SLOT_30, Slot.SLOT_33, Slot.SLOT_36, Slot.SLOT_COLUMN_3},
            {Slot.SLOT_2, Slot.SLOT_5, Slot.SLOT_8, Slot.SLOT_11, Slot.SLOT_14, Slot.SLOT_17, Slot.SLOT_20, Slot.SLOT_23, Slot.SLOT_26, Slot.SLOT_29, Slot.SLOT_32, Slot.SLOT_35, Slot.SLOT_COLUMN_2},
            {Slot.SLOT_1, Slot.SLOT_4, Slot.SLOT_7, Slot.SLOT_10, Slot.SLOT_13, Slot.SLOT_16, Slot.SLOT_19, Slot.SLOT_22, Slot.SLOT_25, Slot.SLOT_28, Slot.SLOT_31, Slot.SLOT_34, Slot.SLOT_COLUMN_1},
            {Slot.SLOT_DOZEN_1, Slot.SLOT_DOZEN_2, Slot.SLOT_DOZEN_3},
            {Slot.SLOT_LOW, Slot.SLOT_EVEN, Slot.SLOT_RED, Slot.SLOT_BLACK, Slot.SLOT_ODD, Slot.SLOT_HIGH}
    };

    private static final BlockFace[] AXIS = {
            BlockFace.NORTH,
            BlockFace.EAST,
            BlockFace.SOUTH,
            BlockFace.WEST};

    private static final BlockFace[] RADIAL = {
            BlockFace.NORTH,
            BlockFace.NORTH_EAST,
            BlockFace.EAST,
            BlockFace.SOUTH_EAST,
            BlockFace.SOUTH,
            BlockFace.SOUTH_WEST,
            BlockFace.WEST,
            BlockFace.NORTH_WEST};

    public static final Color[] COLORS;
    private static final Map<String, org.bukkit.Color> COLORS_BY_NAME = new HashMap<>();

    private static final Class<?> CRAFT_META_SKULL = XReflection.getCraftClass("inventory.CraftMetaSkull");

    private static final MethodHandle SET_PROFILE = Reflection.getMethod(CRAFT_META_SKULL, "setProfile", false, GameProfile.class);
    private static final MethodHandle SET_OWNER_PROFILE = SET_PROFILE != null ? null : Reflection.getMethod(SkullMeta.class, "setOwnerProfile", false, PlayerProfile.class);

    static {
        for (Field field : org.bukkit.Color.class.getDeclaredFields()) {
            if (!field.getType().equals(org.bukkit.Color.class)) continue;

            try {
                COLORS_BY_NAME.put(field.getName(), (org.bukkit.Color) field.get(null));
            } catch (IllegalAccessException ignored) {
            }
        }

        COLORS = COLORS_BY_NAME.values().toArray(new org.bukkit.Color[0]);
    }

    public static @NotNull BlockFace getFace(float yaw, boolean subCardinal) {
        return (subCardinal ? RADIAL[Math.round(yaw / 45f) & 0x7] : AXIS[Math.round(yaw / 90f) & 0x3]).getOppositeFace();
    }

    public static @NotNull Vector getDirection(@NotNull BlockFace face) {
        int modX = face.getModX(), modY = face.getModY(), modZ = face.getModZ();
        Vector direction = new Vector(modX, modY, modZ);
        if (modX != 0 || modY != 0 || modZ != 0) direction.normalize();
        return direction;
    }

    @Contract("_, _, _ -> new")
    public static @NotNull Vector offsetVector(@NotNull Vector vector, float yawDegrees, float pitchDegrees) {
        double yaw = Math.toRadians(-yawDegrees), pitch = Math.toRadians(-pitchDegrees);

        double cosYaw = Math.cos(yaw), cosPitch = Math.cos(pitch);
        double sinYaw = Math.sin(yaw), sinPitch = Math.sin(pitch);

        double initialX, initialY, initialZ, x, y, z;

        initialX = vector.getX();
        initialY = vector.getY();
        x = initialX * cosPitch - initialY * sinPitch;
        y = initialX * sinPitch + initialY * cosPitch;

        initialZ = vector.getZ();
        initialX = x;
        z = initialZ * cosYaw - initialX * sinYaw;
        x = initialZ * sinYaw + initialX * cosYaw;

        return new Vector(x, y, z);
    }

    public static ItemStack createHead(String url) {
        return createHead(url, true);
    }

    public static @Nullable ItemStack createHead(String url, boolean isMCUrl) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);

        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta == null) return null;

        applySkin(meta, url, isMCUrl);
        item.setItemMeta(meta);

        return item;
    }

    public static void applySkin(SkullMeta meta, String texture, boolean isUrl) {
        applySkin(meta, UUID.randomUUID(), texture, isUrl);
    }

    public static void applySkin(SkullMeta meta, UUID uuid, String texture, boolean isUrl) {
        try {
            // If the serialized profile field isn't set, ItemStack#isSimilar() and ItemStack#equals() throw an error.
            if (SET_PROFILE != null) {
                GameProfile profile = new GameProfile(uuid, "");

                String value = isUrl ? new String(Base64.getEncoder().encode(String
                        .format("{textures:{SKIN:{url:\"%s\"}}}", "http://textures.minecraft.net/texture/" + texture)
                        .getBytes())) : texture;

                profile.getProperties().put("textures", new Property("textures", value));
                SET_PROFILE.invoke(meta, profile);
            } else if (SET_OWNER_PROFILE != null) {
                PlayerProfile profile = Bukkit.createPlayerProfile(uuid, "");

                PlayerTextures textures = profile.getTextures();
                String url = isUrl ? "http://textures.minecraft.net/texture/" + texture : getURLFromTexture(texture);
                textures.setSkin(new URL(url));

                profile.setTextures(textures);
                SET_OWNER_PROFILE.invoke(meta, profile);
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    public static String getURLFromTexture(String texture) {
        // String decoded = new String(Base64.getDecoder().decode(texture));
        // return new URL(decoded.substring("{\"textures\":{\"SKIN\":{\"url\":\"".length(), decoded.length() - "\"}}}".length()));

        // Decode B64.
        String decoded = new String(Base64.getDecoder().decode(texture));

        // Get url from json.
        return JsonParser.parseString(decoded).getAsJsonObject()
                .getAsJsonObject("textures")
                .getAsJsonObject("SKIN")
                .get("url")
                .getAsString();
    }

    public static @NotNull String translate(String message) {
        Validate.notNull(message, "Message can't be null.");

        Matcher matcher = PATTERN.matcher(ChatColor.translateAlternateColorCodes('&', message));
        StringBuilder buffer = new StringBuilder();

        while (matcher.find()) {
            matcher.appendReplacement(buffer, ChatColor.of(matcher.group(1)).toString());
        }

        return matcher.appendTail(buffer).toString();
    }

    @Contract("_ -> param1")
    public static @NotNull List<String> translate(List<String> messages) {
        Validate.notNull(messages, "Messages can't be null.");

        messages.replaceAll(PluginUtils::translate);
        return messages;
    }

    public static String getSlotName(@NotNull Slot slot) {
        if (slot.isSingleInclusive()) {
            String number = slot.isDoubleZero() ? "00" : String.valueOf(slot.getInts()[0]);
            return switch (slot.getColor()) {
                case RED -> ConfigManager.Config.SINGLE_RED.asString().replace("%number%", number);
                case BLACK -> ConfigManager.Config.SINGLE_BLACK.asString().replace("%number%", number);
                default -> ConfigManager.Config.SINGLE_ZERO.asString().replace("%number%", number);
            };
        }

        if (slot.isColumn() || slot.isDozen()) {
            return PLUGIN.getConfigManager().getColumnOrDozen(slot.isColumn() ? "column" : "dozen", slot.getColumnOrDozen());
        }

        return switch (slot) {
            case SLOT_LOW -> ConfigManager.Config.LOW.asString();
            case SLOT_EVEN -> ConfigManager.Config.EVEN.asString();
            case SLOT_ODD -> ConfigManager.Config.ODD.asString();
            case SLOT_HIGH -> ConfigManager.Config.HIGH.asString();
            case SLOT_RED -> ConfigManager.Config.RED.asString();
            default -> ConfigManager.Config.BLACK.asString();
        };
    }

    public static String format(double value) {
        return format(value, ConfigManager.Config.MONEY_ABBREVIATION_FORMAT_ENABLED.asBool());
    }

    public static String format(double value, boolean abbreviation) {
        return abbreviation ? format(value, PLUGIN.getAbbreviations()) : PLUGIN.getEconomy().format(value);
    }

    @SuppressWarnings("IntegerDivisionInFloatingPointContext")
    private static String format(double value, NavigableMap<Long, String> lang) {
        if (value == Long.MIN_VALUE) return format(Long.MIN_VALUE + 1, lang);
        if (value < 0) return "-" + format(-value, lang);
        if (value < 1000) return PLUGIN.getEconomy().format(value);

        Map.Entry<Long, String> entry = lang.floorEntry((long) value);
        Long divideBy = entry.getKey();
        String suffix = entry.getValue();

        long truncated = (long) value / (divideBy / 10);
        boolean hasDecimal = truncated < 100 && (truncated / 10.0d) != (truncated / 10);
        return "$" + (hasDecimal ? (truncated / 10.0d) + suffix : (truncated / 10) + suffix);
    }

    public static String[] splitData(String string) {
        String[] split = StringUtils.split(StringUtils.deleteWhitespace(string), ',');
        if (split.length == 0) split = StringUtils.split(string, ' ');
        return split;
    }

    public static <T extends Enum<T>> T getRandomFromEnum(@NotNull Class<T> clazz) {
        T[] constants = clazz.getEnumConstants();
        return constants[RANDOM.nextInt(0, constants.length)];
    }

    public static <T extends Enum<T>> T getOrEitherRandomOrNull(Class<T> clazz, @NotNull String name) {
        if (name.equalsIgnoreCase("$RANDOM")) return getRandomFromEnum(clazz);
        return getOrNull(clazz, name);
    }

    public static <T extends Enum<T>> T getOrNull(Class<T> clazz, String name) {
        if (name == null) return null;
        return getOrDefault(clazz, name, null);
    }

    public static <T extends Enum<T>> T getOrDefault(Class<T> clazz, @NotNull String name, T defaultValue) {
        try {
            return Enum.valueOf(clazz, name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return defaultValue;
        }
    }

    public static int getRangedAmount(@NotNull String string) {
        String[] data = string.split("-");
        if (data.length == 1) {
            try {
                return Integer.parseInt(data[0]);
            } catch (IllegalArgumentException ignored) {
            }
        } else if (data.length == 2) {
            try {
                int min = Integer.parseInt(data[0]);
                int max = Integer.parseInt(data[1]);
                return RANDOM.nextInt(min, max + 1);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return 1;
    }

    public static org.bukkit.Color getRandomColor() {
        return COLORS[RANDOM.nextInt(0, COLORS.length)];
    }

    public static org.bukkit.Color getColor(@NotNull String string) {
        if (string.equalsIgnoreCase("$RANDOM")) return getRandomColor();

        if (string.matches(PATTERN.pattern())) {
            java.awt.Color temp = ChatColor.of(string.substring(1)).getColor();
            return org.bukkit.Color.fromRGB(temp.getRed(), temp.getGreen(), temp.getBlue());
        }

        return COLORS_BY_NAME.get(string);
    }

    public static <E extends Enum<E>> E getNextOrPreviousEnum(@NotNull E current, boolean next) {
        return getNextOrPrevious(current.getDeclaringClass().getEnumConstants(), current.ordinal(), next);
    }

    public static <E> E getNextOrPrevious(E @NotNull [] values, int currentIndex, boolean next) {
        int length = values.length;
        return next ? values[(currentIndex + 1) % length] : values[(currentIndex - 1 + length) % length];
    }

    public static <E> E getNextOrPrevious(@NotNull List<E> values, int currentIndex, boolean next) {
        int length = values.size();
        return next ? values.get((currentIndex + 1) % length) : values.get((currentIndex - 1 + length) % length);
    }

    public static <T, R> R getAvailable(Player player,
                                        T @NotNull [] array,
                                        T currentValue,
                                        @NotNull BiPredicate<Player, T> keepLookingWhile,
                                        boolean right,
                                        @NotNull Function<T, R> converter) {
        T object = currentValue;
        do {
            object = getNextOrPrevious(array, ArrayUtils.indexOf(array, object), right);
        } while (keepLookingWhile.test(player, object));
        return converter.apply(object);
    }

    public static double getRenderDistance() {
        return Math.min(
                Math.pow(PLUGIN.getConfigManager().getRenderDistance(), 2),
                BUKKIT_VIEW_DISTANCE);
    }

    public static boolean isInRange(@NotNull Location first, @NotNull Location second) {
        if (!Objects.equals(first.getWorld(), second.getWorld())) return false;
        return first.distanceSquared(second) <= getRenderDistance();
    }

    public static byte @NotNull [] toBytes(@NotNull UUID uuid) {
        ByteBuffer buffer = ByteBuffer.wrap(new byte[16]);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    public static @NotNull UUID toUUID(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        long firstLong = buffer.getLong();
        long secondLong = buffer.getLong();
        return new UUID(firstLong, secondLong);
    }

    private static Slot[][] initTableGrid(Game game) {
        Slot[][] clone = TABLE_GRID.clone();
        if (game.getType().isEuropean()) {
            // Single 0.
            clone[1] = ArrayUtils.insert(0, clone[1], Slot.SLOT_0);
            return clone;
        }

        // Double 0.
        clone[0] = ArrayUtils.insert(0, clone[0], Slot.SLOT_00);
        clone[2] = ArrayUtils.insert(0, clone[2], Slot.SLOT_0);
        return clone;
    }

    @Getter
    public static class SlotHolder {
        private final Slot slot;

        public SlotHolder(Slot slot) {
            this.slot = slot;
        }

        @Contract(value = "_ -> new", pure = true)
        public static @NotNull SlotHolder of(Slot slot) {
            return new SlotHolder(slot);
        }

        @Contract("_, _ -> new")
        public static @NotNull SlotHolderPair of(Slot slot, Slot similar) {
            return new SlotHolderPair(slot, similar);
        }
    }

    @Getter
    public static class SlotHolderPair extends SlotHolder {
        private final Slot similar;

        private SlotHolderPair(Slot slot, Slot similar) {
            super(slot);
            this.similar = similar;
        }
    }

    public static @Nullable SlotHolder moveFromSlot(Game game, Slot current, boolean up, boolean down, boolean left, boolean right) {
        // The player didn't move his bet.
        if (!up && !down && !left && !right) return SlotHolder.of(current);

        int row = -1, column = -1;

        Slot[][] grid = initTableGrid(game);

        // Find the current row and column.
        for (int i = 0; i < grid.length; i++) {
            int index = ArrayUtils.indexOf(grid[i], current);
            if (index == -1) continue;

            row = i;
            column = index;
        }

        if (row == -1) return SlotHolder.of(current);

        boolean european = game.getType().isEuropean(),
                zero = current.isZero(),
                doubleZero = current.isDoubleZero();

        // Handle up.
        if (up) {
            // Column 3.
            if (row == 0) return null;

            // Column 1 & 2.
            if (row < 3) {
                // We are at 0 (0x1), can't go up.
                if (european && zero) return null;

                if (!european) {
                    // We are at 0 (2x0), go to 00 (0x0).
                    if (zero) return SlotHolder.of(grid[0][0]);

                    // We are at 00 (0x0), can't go up.
                    if (doubleZero) return null;
                }

                // If the current row has a zero, we want to reduce a column, otherwise increase one.
                if (grid[row].length != 13 && column > 0) column--;
                else column++;

                // Go up.
                return SlotHolder.of(grid[row - 1][column]);
            }

            // Dozens, go to the first slot of the dozen (not zeros).
            if (row == 3) {
                Slot[] slots = grid[row - 1];
                int extra = slots.length != 13 ? 1 : 0;
                return SlotHolder.of(slots[(column * 4) + extra]);
            }

            // Even-money slots, go to the top dozen.
            return SlotHolder.of(grid[row - 1][column / 2]);
        }

        // Handle down.
        if (down) {
            // Even-money slot, we can't go down.
            if (row == 4) return null;

            // Dozens, go to the first element of the pair below.
            if (row == 3) {
                Slot[] slots = grid[row + 1];
                int goToColumn = column * 2;
                return SlotHolder.of(slots[goToColumn], slots[goToColumn + 1]);
            }

            // Column 1.
            if (row == 2) {
                // Can't go below 0.
                if (zero) return null;

                // Can't go below column slot.
                if (column == grid[row].length - 1) return null;

                // Go to a dozen.
                if (grid[row].length != 13) column--;
                return SlotHolder.of(grid[row + 1][column / 4]);
            }

            // We are at 0 (0x1), can't go down.
            if (european && zero) return null;

            if (!european) {
                // We are at 0 (2x0), can't go down.
                if (zero) return null;
                // We are at 00 (0x0), go to 0 (2x0).
                if (doubleZero) return SlotHolder.of(grid[2][0]);
            }

            // If the current row has a zero, we want to reduce a column, otherwise increase one.
            if (grid[row].length != 13 && column > 0) column--;
            else column++;

            // Go down.
            return SlotHolder.of(grid[row + 1][column]);
        }

        return SlotHolder.of(handleSideways(european, current, grid, row, column, left, right));
    }

    private static @Nullable Slot handleSideways(
            boolean european,
            @NotNull Slot current,
            Slot[][] grid,
            int row,
            int column,
            boolean left,
            boolean right) {
        boolean zero = current.isZero(), anyZero = current.isAnyZero();
        int length = grid[row].length;

        // No more slots at the left of zeros.
        if (anyZero && left) {
            return null;
        }

        // If single zero and current is 1 or 3, go to 0.
        if (row < 3 && left && european && !zero && column == 0) {
            return grid[1][0];
        }

        // If double zero and current is 2, go to 0.
        if (row < 3 && left && !european && column == 0) {
            return grid[2][0];
        }

        // No more slots at the left or right.
        if ((right && column == length - 1) || left && column == 0) return null;

        // Move to the side.
        int goToColumn = column + (right ? 1 : -1);
        return grid[row][goToColumn];
    }

    private static class UUIDTagType implements PersistentDataType<byte[], UUID> {

        @Override
        public @NotNull Class<byte[]> getPrimitiveType() {
            return byte[].class;
        }

        @Override
        public @NotNull Class<UUID> getComplexType() {
            return UUID.class;
        }

        @Override
        public byte @NotNull [] toPrimitive(@NotNull UUID complex, @NotNull PersistentDataAdapterContext context) {
            return PluginUtils.toBytes(complex);
        }

        @Override
        public @NotNull UUID fromPrimitive(byte @NotNull [] primitive, @NotNull PersistentDataAdapterContext context) {
            return PluginUtils.toUUID(primitive);
        }
    }
}