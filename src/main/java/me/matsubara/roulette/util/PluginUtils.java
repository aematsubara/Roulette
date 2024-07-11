package me.matsubara.roulette.util;

import com.cryptomorin.xseries.reflection.XReflection;
import com.google.common.base.Preconditions;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.data.Slot;
import me.matsubara.roulette.manager.ConfigManager;
import net.md_5.bungee.api.ChatColor;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PluginUtils {

    private static final RoulettePlugin PLUGIN = JavaPlugin.getPlugin(RoulettePlugin.class);

    private static final Pattern PATTERN = Pattern.compile("&#([\\da-fA-F]{6})");

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

    private static final MethodHandle SET_PROFILE;
    private static final MethodHandle PROFILE;

    static {
        for (Field field : org.bukkit.Color.class.getDeclaredFields()) {
            if (!field.getType().equals(org.bukkit.Color.class)) continue;

            try {
                COLORS_BY_NAME.put(field.getName(), (org.bukkit.Color) field.get(null));
            } catch (IllegalAccessException ignored) {
            }
        }

        COLORS = COLORS_BY_NAME.values().toArray(new org.bukkit.Color[0]);

        Class<?> craftMetaSkull = XReflection.getCraftClass("inventory.CraftMetaSkull");
        Preconditions.checkNotNull(craftMetaSkull);

        SET_PROFILE = Reflection.getMethod(craftMetaSkull, "setProfile", GameProfile.class);
        PROFILE = Reflection.getFieldSetter(craftMetaSkull, "profile");
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
        GameProfile profile = new GameProfile(uuid, "");

        String textureValue = texture;
        if (isUrl) {
            textureValue = "http://textures.minecraft.net/texture/" + textureValue;
            byte[] encodedData = Base64.getEncoder().encode(String.format("{textures:{SKIN:{url:\"%s\"}}}", textureValue).getBytes());
            textureValue = new String(encodedData);
        }

        profile.getProperties().put("textures", new Property("textures", textureValue));

        try {
            // If the serialized profile field isn't set, ItemStack#isSimilar() and ItemStack#equals() throw an error.
            (SET_PROFILE == null ? PROFILE : SET_PROFILE).invoke(meta, profile);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    public static String translate(String message) {
        Validate.notNull(message, "Message can't be null.");

        if (XReflection.MINOR_NUMBER < 16) return oldTranslate(message);

        Matcher matcher = PATTERN.matcher(oldTranslate(message));
        StringBuilder builder = new StringBuilder();

        while (matcher.find()) {
            matcher.appendReplacement(builder, ChatColor.of("#" + matcher.group(1)).toString());
        }

        return matcher.appendTail(builder).toString();
    }

    @Contract("_ -> param1")
    public static @NotNull List<String> translate(List<String> messages) {
        Validate.notNull(messages, "Messages can't be null.");

        messages.replaceAll(PluginUtils::translate);
        return messages;
    }

    @Contract("_ -> new")
    private static @NotNull String oldTranslate(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
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
        return ConfigManager.Config.MONEY_ABBREVIATION_FORMAT_ENABLED.asBool() ?
                format(value, PLUGIN.getAbbreviations()) :
                PLUGIN.getEconomy().format(value);
    }

    @SuppressWarnings("IntegerDivisionInFloatingPointContext")
    public static String format(double value, NavigableMap<Long, String> lang) {
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
        return constants[RandomUtils.nextInt(0, constants.length)];
    }

    public static <T extends Enum<T>> T getOrEitherRandomOrNull(Class<T> clazz, @NotNull String name) {
        if (name.equalsIgnoreCase("$RANDOM")) return getRandomFromEnum(clazz);
        return getOrNull(clazz, name);
    }

    public static <T extends Enum<T>> T getOrNull(Class<T> clazz, String name) {
        return getOrDefault(clazz, name, null);
    }

    public static <T extends Enum<T>> T getOrDefault(Class<T> clazz, String name, T defaultValue) {
        try {
            return Enum.valueOf(clazz, name.toUpperCase());
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
                return RandomUtils.nextInt(min, max + 1);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return 1;
    }

    public static org.bukkit.Color getRandomColor() {
        return COLORS[RandomUtils.nextInt(0, COLORS.length)];
    }

    public static org.bukkit.Color getColor(@NotNull String string) {
        if (string.equalsIgnoreCase("$RANDOM")) return getRandomColor();

        if (string.matches(PATTERN.pattern())) {
            java.awt.Color temp = ChatColor.of(string.substring(1)).getColor();
            return org.bukkit.Color.fromRGB(temp.getRed(), temp.getGreen(), temp.getBlue());
        }

        return COLORS_BY_NAME.get(string);
    }
}