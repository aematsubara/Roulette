package me.matsubara.roulette.animation;

import com.cryptomorin.xseries.reflection.XReflection;
import lombok.Getter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.manager.ConfigManager;
import me.matsubara.roulette.model.stand.PacketStand;
import me.matsubara.roulette.model.stand.StandSettings;
import me.matsubara.roulette.model.stand.animator.ArmorStandAnimator;
import me.matsubara.roulette.util.GlowingEntities;
import me.matsubara.roulette.util.ItemBuilder;
import me.matsubara.roulette.util.PluginUtils;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@Getter
public class DabAnimation extends BukkitRunnable {

    private final Game game;
    private final int speed;
    private final boolean glowing;
    private final Map<ArmorStandAnimator, Integer> animators = new HashMap<>();

    private static final int THRESHOLD = Integer.MAX_VALUE - 5000;
    private static final int BIT_MASK = 0xFF;

    public DabAnimation(@NotNull Game game, Player player, Location location) {
        this.game = game;
        this.speed = ConfigManager.Config.DAB_ANIMATION_RAINBOW_EFFECT_SPEED.asInt();
        this.glowing = ConfigManager.Config.DAB_ANIMATION_RAINBOW_EFFECT_GLOWING.asBool();

        RoulettePlugin plugin = game.getPlugin();
        File file = new File(plugin.getDataFolder(), "dab_animation.txt");

        ItemStack head = new ItemBuilder(Material.PLAYER_HEAD)
                .setOwningPlayer(player)
                .build();

        StandSettings settings = new StandSettings();
        settings.getEquipment().put(PacketStand.ItemSlot.HEAD, head);
        settings.setBasePlate(false);
        settings.setArms(true);

        int amount = ConfigManager.Config.DAB_ANIMATION_AMOUNT.asInt();
        int radius = ConfigManager.Config.DAB_ANIMATION_RADIUS.asInt();

        for (int i = 0; i < amount; i++) {
            double angle = (2 * Math.PI / amount) * i;
            double offsetX = Math.cos(angle) * radius;
            double offsetZ = Math.sin(angle) * radius;

            Location spawn = location.clone().add(offsetX, 0.0d, offsetZ);
            spawn.setY(player.getWorld().getHighestBlockYAt(spawn) + 1);
            lookAt(spawn, location);

            ArmorStandAnimator animator = new ArmorStandAnimator(file, settings.clone(), spawn);
            animators.put(animator, PluginUtils.RANDOM.nextInt(THRESHOLD));
        }

        game.setDabAnimation(this);
        runTaskTimer(plugin, 1L, 1L);
    }

    @Override
    public synchronized void cancel() throws IllegalStateException {
        super.cancel();

        animators.keySet().forEach(ArmorStandAnimator::stop);
        animators.clear();

        game.setDabAnimation(null);
    }

    @Override
    public void run() {
        if (!game.getState().isEnding()) {
            cancel();
            return;
        }

        for (Map.Entry<ArmorStandAnimator, Integer> entry : animators.entrySet()) {
            ArmorStandAnimator animator = entry.getKey();

            Integer count = entry.getValue();

            PacketStand stand = animator.getStand();
            Color color = convertCountToRGB(count);
            setArmor(stand, color);

            animator.update();

            // Glow the stand to the closest chat color.
            handleGlowingColor(stand, color);

            if ((count += speed) >= THRESHOLD) count = 0;
            animators.put(animator, count);
        }
    }

    private void handleGlowingColor(PacketStand stand, Color color) {
        if (!glowing) return;

        GlowingEntities glowing = game.getPlugin().getGlowingEntities();
        if (glowing == null) return;

        int id = stand.getEntityId();
        String team = stand.getEntityUniqueId().toString();
        ChatColor glow = getClosestChatColor(color);

        try {
            for (Player player : Bukkit.getOnlinePlayers()) {
                glowing.setGlowing(id, team, player, glow);
                stand.updateMetadata(player);
            }
        } catch (ReflectiveOperationException ignored) {

        }
    }

    private ChatColor getClosestChatColor(Color from) {
        ChatColor closest = null;
        double minDistance = Double.MAX_VALUE;

        for (ChatColor color : Game.GLOW_COLORS) {
            int rgb = color.asBungee().getColor().getRGB();

            Color to = XReflection.supports(19, 4) ?
                    Color.fromARGB(rgb) :
                    Color.fromRGB(convertARGBtoRGB(rgb));

            double distance = colorDistance(from, to);
            if (distance < minDistance) {
                minDistance = distance;
                closest = color;
            }
        }

        return closest;
    }

    private double colorDistance(@NotNull Color first, @NotNull Color second) {
        int redDiff = first.getRed() - second.getRed();
        int greenDiff = first.getGreen() - second.getGreen();
        int blueDiff = first.getBlue() - second.getBlue();
        return Math.sqrt(redDiff * redDiff + greenDiff * greenDiff + blueDiff * blueDiff);
    }

    private void setArmor(@NotNull PacketStand stand, Color color) {
        Map<PacketStand.ItemSlot, ItemStack> equipment = stand.getSettings().getEquipment();
        equipment.put(PacketStand.ItemSlot.CHEST, createArmor(Material.LEATHER_CHESTPLATE, color));
        equipment.put(PacketStand.ItemSlot.LEGS, createArmor(Material.LEATHER_LEGGINGS, color));
        equipment.put(PacketStand.ItemSlot.FEET, createArmor(Material.LEATHER_BOOTS, color));
        stand.updateEquipment(null);
    }

    private @NotNull ItemStack createArmor(Material material, Color color) {
        return new ItemBuilder(material)
                .setLeatherArmorMetaColor(color)
                .build();
    }

    private int convertARGBtoRGB(int argb) {
        int red = (argb >> 16) & BIT_MASK;
        int green = (argb >> 8) & BIT_MASK;
        int blue = argb & BIT_MASK;
        return (red << 16) | (green << 8) | blue;
    }

    private @NotNull Color convertCountToRGB(int count) {
        int red = (int) (Math.sin(count * 0.01d) * 127 + 128);
        int green = (int) (Math.sin(count * 0.01d + 2) * 127 + 128);
        int blue = (int) (Math.sin(count * 0.01d + 4) * 127 + 128);
        return Color.fromRGB(red, green, blue);
    }

    private void lookAt(@NotNull Location origin, @NotNull Location target) {
        Vector direction = target.toVector().subtract(origin.toVector()).normalize();

        float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
        float pitch = (float) Math.toDegrees(Math.asin(-direction.getY()));

        origin.setYaw(yaw);
        origin.setPitch(pitch);
    }
}