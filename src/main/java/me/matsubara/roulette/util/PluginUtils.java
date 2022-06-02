package me.matsubara.roulette.util;

import com.comphenix.protocol.wrappers.EnumWrappers;
import com.cryptomorin.xseries.ReflectionUtils;
import com.cryptomorin.xseries.SkullUtils;
import com.cryptomorin.xseries.XMaterial;
import com.github.juliarn.npc.modifier.MetadataModifier;
import com.github.juliarn.npc.modifier.NPCModifier;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.data.Slot;
import me.matsubara.roulette.manager.ConfigManager;
import net.md_5.bungee.api.ChatColor;
import org.apache.commons.lang.Validate;
import org.bukkit.Color;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PluginUtils {

    private final static RoulettePlugin PLUGIN = JavaPlugin.getPlugin(RoulettePlugin.class);

    private final static Pattern PATTERN = Pattern.compile("&#([\\da-fA-F]{6})");

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

    // Pose enum objects.
    private static Object SNEAKING;
    private static Object STANDING;

    public final static Color[] COLORS = getColors();

    private static Color[] getColors() {
        Field[] fields = Color.class.getDeclaredFields();

        List<Color> results = new ArrayList<>();
        for (Field field : fields) {
            if (!field.getType().equals(Color.class)) continue;

            try {
                results.add((Color) field.get(null));
            } catch (IllegalAccessException exception) {
                exception.printStackTrace();
            }
        }
        return results.toArray(new Color[0]);
    }

    static {
        if (ReflectionUtils.VER > 13) {
            Class<?> ENTITY_POSE = ReflectionUtils.getNMSClass("world.entity", "EntityPose");

            Method valueOf = null;

            try {
                //noinspection ConstantConditions
                valueOf = ENTITY_POSE.getMethod("valueOf", String.class);

                int ver = ReflectionUtils.VER;
                SNEAKING = valueOf.invoke(null, ver == 14 ? "SNEAKING" : "CROUCHING");
                STANDING = valueOf.invoke(null, "STANDING");
            } catch (IllegalArgumentException exception) {
                // The only way this exception can occur is if the server is using obfuscated code (in 1.17).
                assert valueOf != null;

                try {
                    SNEAKING = valueOf.invoke(null, "f");
                    STANDING = valueOf.invoke(null, "a");
                } catch (ReflectiveOperationException ignore) {
                }
            } catch (ReflectiveOperationException exception) {
                exception.printStackTrace();
            }
        }
    }

    /**
     * Fixed sneaking metadata that works on 1.14 too.
     */
    @SuppressWarnings("unchecked")
    public static final MetadataModifier.EntityMetadata<Boolean, Byte> SNEAKING_METADATA = new MetadataModifier.EntityMetadata<>(
            0,
            Byte.class,
            Collections.emptyList(),
            input -> (byte) (input ? 0x02 : 0),
            new MetadataModifier.EntityMetadata<>(
                    6,
                    (Class<Object>) EnumWrappers.getEntityPoseClass(),
                    Collections.emptyList(),
                    input -> (input ? SNEAKING : STANDING),
                    () -> NPCModifier.MINECRAFT_VERSION >= 14));

    public static BlockFace getFace(float yaw, boolean subCardinal) {
        return (subCardinal ? RADIAL[Math.round(yaw / 45f) & 0x7] : AXIS[Math.round(yaw / 90f) & 0x3]).getOppositeFace();
    }

    public static Vector getDirection(BlockFace face) {
        int modX = face.getModX(), modY = face.getModY(), modZ = face.getModZ();
        Vector direction = new Vector(modX, modY, modZ);
        if (modX != 0 || modY != 0 || modZ != 0) direction.normalize();
        return direction;
    }

    public static Vector offsetVector(Vector vector, float yawDegrees, float pitchDegrees) {
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

    public static ItemStack createHead(String url, boolean isMCUrl) {
        ItemStack item = XMaterial.PLAYER_HEAD.parseItem();
        if (item == null) return null;

        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta == null) return null;

        item.setItemMeta(SkullUtils.applySkin(meta, isMCUrl ? "http://textures.minecraft.net/texture/" + url : url));
        return item;
    }

    public static String translate(String message) {
        Validate.notNull(message, "Message can't be null.");

        if (ReflectionUtils.VER < 16) return oldTranslate(message);

        Matcher matcher = PATTERN.matcher(oldTranslate(message));
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            matcher.appendReplacement(buffer, ChatColor.of("#" + matcher.group(1)).toString());
        }

        return matcher.appendTail(buffer).toString();
    }

    public static List<String> translate(List<String> messages) {
        Validate.notNull(messages, "Messages can't be null.");

        messages.replaceAll(PluginUtils::translate);
        return messages;
    }

    private static String oldTranslate(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public static String getSlotName(Slot slot) {
        if (slot.isSingleInclusive()) {
            String number = slot.isDoubleZero() ? "00" : String.valueOf(slot.getInts()[0]);
            switch (slot.getColor()) {
                case RED:
                    return ConfigManager.Config.SINGLE_RED.asString().replace("%number%", number);
                case BLACK:
                    return ConfigManager.Config.SINGLE_BLACK.asString().replace("%number%", number);
                default:
                    return ConfigManager.Config.SINGLE_ZERO.asString().replace("%number%", number);
            }
        } else if (slot.isColumn() || slot.isDozen()) {
            return PLUGIN.getConfigManager().getColumnOrDozen(slot.isColumn() ? "column" : "dozen", slot.getColumnOrDozen());
        }
        switch (slot) {
            case SLOT_LOW:
                return ConfigManager.Config.LOW.asString();
            case SLOT_EVEN:
                return ConfigManager.Config.EVEN.asString();
            case SLOT_ODD:
                return ConfigManager.Config.ODD.asString();
            case SLOT_HIGH:
                return ConfigManager.Config.HIGH.asString();
            case SLOT_RED:
                return ConfigManager.Config.RED.asString();
            default:
                return ConfigManager.Config.BLACK.asString();
        }
    }
}