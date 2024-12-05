package me.matsubara.roulette.util;

import com.cryptomorin.xseries.reflection.XReflection;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

public class ColorUtils {

    public static final Map<ChatColor, String> GLOW_COLOR_URL = new LinkedHashMap<>();
    public static final ChatColor[] GLOW_COLORS;

    private static final int BIT_MASK = 0xFF;

    static {
        GLOW_COLOR_URL.put(ChatColor.BLACK, "2a52d579afe2fdf7b8ecfa746cd016150d96beb75009bb2733ade15d487c42a1");
        GLOW_COLOR_URL.put(ChatColor.DARK_BLUE, "fe4c1b36e5d8e2fa6d55134753eefb2f52302d20f4dac554b1afe5711b93cc");
        GLOW_COLOR_URL.put(ChatColor.DARK_GREEN, "eb879f5764385ed6bb90755bb041574882e2f41ab9323576016cfbe7f16397a");
        GLOW_COLOR_URL.put(ChatColor.DARK_AQUA, "d5bbd4a69d208dd25dd95ad4b0f5c7c4b2e0d626161bb1ebf3bcc7e88fd4a960");
        GLOW_COLOR_URL.put(ChatColor.DARK_RED, "97d0b9b3c419d3e321397bedc6dcd649e51cc2fa36b883b02f4da39582cdff1b");
        GLOW_COLOR_URL.put(ChatColor.DARK_PURPLE, "b09fa999c27a947a0aa5d4478da26ab0f189f180a7fb1ec8adcef6df76879");
        GLOW_COLOR_URL.put(ChatColor.GOLD, "c8e44023e11eeb5b293d086351e29e6ffaec01b768dc1460b1be54b809bd6dbf");
        GLOW_COLOR_URL.put(ChatColor.GRAY, "1b9c45d6c7cd0116436c31ed4d8dc825de03e806edb64e9a67f540b8aaae85");
        GLOW_COLOR_URL.put(ChatColor.DARK_GRAY, "b2554dda80ea64b18bc375b81ce1ed1907fc81aea6b1cf3c4f7ad3144389f64c");
        GLOW_COLOR_URL.put(ChatColor.BLUE, "3b5106b060eaf398217349f3cfb4f2c7c4fd9a0b0307a17eba6af7889be0fbe6");
        GLOW_COLOR_URL.put(ChatColor.GREEN, "ac01f6796eb63d0e8a759281d037f7b3843090f9a456a74f786d049065c914c7");
        GLOW_COLOR_URL.put(ChatColor.AQUA, "4548789b968c70ec9d1de272d0bb93a70134f2c0e60acb75e8d455a1650f3977");
        GLOW_COLOR_URL.put(ChatColor.RED, "3c4d7a3bc3de833d3032e85a0bf6f2bef7687862b3c6bc40ce731064f615dd9d");
        GLOW_COLOR_URL.put(ChatColor.LIGHT_PURPLE, "205c17650e5d747010e8b69a6f2363fd11eb93f81c6ce99bf03895cefb92baa");
        GLOW_COLOR_URL.put(ChatColor.YELLOW, "200bf4bf14c8699c0f9209ca79fe18253e901e9ec3876a2ba095da052f69eba7");
        GLOW_COLOR_URL.put(ChatColor.WHITE, "1884d5dabe073e28e6b7eb166ff61247905c79f838b6f5752e7ad406091eeaf3");
        GLOW_COLORS = GLOW_COLOR_URL.keySet().toArray(ChatColor[]::new);
    }

    public static int convertARGBtoRGB(int argb) {
        int red = (argb >> 16) & BIT_MASK;
        int green = (argb >> 8) & BIT_MASK;
        int blue = argb & BIT_MASK;
        return (red << 16) | (green << 8) | blue;
    }

    public static @NotNull Color convertCountToRGB(int count) {
        int red = (int) (Math.sin(count * 0.01d) * 127 + 128);
        int green = (int) (Math.sin(count * 0.01d + 2) * 127 + 128);
        int blue = (int) (Math.sin(count * 0.01d + 4) * 127 + 128);
        return Color.fromRGB(red, green, blue);
    }

    public static ChatColor getClosestChatColor(Color from) {
        ChatColor closest = null;
        double minDistance = Double.MAX_VALUE;

        for (ChatColor color : GLOW_COLORS) {
            int rgb = color.asBungee().getColor().getRGB();

            Color to = XReflection.supports(19, 4) ?
                    Color.fromARGB(rgb) :
                    Color.fromRGB(ColorUtils.convertARGBtoRGB(rgb));

            double distance = colorDistance(from, to);
            if (distance < minDistance) {
                minDistance = distance;
                closest = color;
            }
        }

        return closest;
    }

    private static double colorDistance(@NotNull Color first, @NotNull Color second) {
        int redDiff = first.getRed() - second.getRed();
        int greenDiff = first.getGreen() - second.getGreen();
        int blueDiff = first.getBlue() - second.getBlue();
        return Math.sqrt(redDiff * redDiff + greenDiff * greenDiff + blueDiff * blueDiff);
    }
}