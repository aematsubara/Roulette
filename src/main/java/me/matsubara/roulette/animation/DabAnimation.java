package me.matsubara.roulette.animation;

import lombok.Getter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.file.Config;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.model.stand.PacketStand;
import me.matsubara.roulette.model.stand.StandSettings;
import me.matsubara.roulette.model.stand.animator.ArmorStandAnimator;
import me.matsubara.roulette.model.stand.data.ItemSlot;
import me.matsubara.roulette.util.ColorUtils;
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
import java.util.Set;

@Getter
public class DabAnimation extends BukkitRunnable {

    private final Game game;
    private final int speed;
    private final boolean glowing;
    private final Map<ArmorStandAnimator, Integer> animators = new HashMap<>();
    private final Set<Player> seeing;

    private static final int THRESHOLD = Integer.MAX_VALUE - 5000;

    public DabAnimation(@NotNull Game game, Player player, Location location) {
        this.game = game;
        this.speed = Config.DAB_ANIMATION_RAINBOW_EFFECT_SPEED.asInt();
        this.glowing = Config.DAB_ANIMATION_RAINBOW_EFFECT_GLOWING.asBool();
        this.seeing = game.getSeeingPlayers();

        RoulettePlugin plugin = game.getPlugin();
        File file = new File(plugin.getDataFolder(), "dab_animation.txt");

        StandSettings settings = new StandSettings();
        settings.getEquipment().put(ItemSlot.HEAD, new ItemBuilder(Material.PLAYER_HEAD)
                .setOwningPlayer(player)
                .build());
        settings.setBasePlate(false);
        settings.setArms(true);

        int amount = Config.DAB_ANIMATION_AMOUNT.asInt();
        int radius = Config.DAB_ANIMATION_RADIUS.asInt();

        for (int i = 0; i < amount; i++) {
            double angle = (2 * Math.PI / amount) * i;
            double offsetX = Math.cos(angle) * radius;
            double offsetZ = Math.sin(angle) * radius;

            Location spawn = location.clone().add(offsetX, 0.0d, offsetZ);
            spawn.setY(player.getWorld().getHighestBlockYAt(spawn) + 1);
            lookAt(spawn, location);

            int count = PluginUtils.RANDOM.nextInt(THRESHOLD);
            Color color = ColorUtils.convertCountToRGB(count);

            StandSettings clone = settings.clone();
            setEquipment(clone, color);

            ArmorStandAnimator animator = new ArmorStandAnimator(plugin, seeing, file, clone, spawn);
            handleGlowingColor(animator.getStand(), color);

            animators.put(animator, count);
        }

        game.setDabAnimation(this);
        runTaskTimerAsynchronously(plugin, 1L, 1L);
    }

    @Override
    public void cancel() throws IllegalStateException {
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

            PacketStand stand = animator.getStand();
            if (!animator.isSpawned()) {
                seeing.forEach(stand::spawn);
                animator.setSpawned(true);
            }

            Integer count = entry.getValue();
            Color color = ColorUtils.convertCountToRGB(count);

            setEquipment(stand.getSettings(), color);
            stand.sendEquipment(seeing);

            animator.update();

            // Glow the stand to the closest chat color.
            handleGlowingColor(stand, color);

            int temp = count + speed;
            animators.put(animator, temp >= THRESHOLD ? 0 : temp);
        }
    }

    private void handleGlowingColor(PacketStand stand, Color color) {
        if (!glowing) return;

        GlowingEntities glowing = game.getPlugin().getGlowingEntities();
        if (glowing == null) return;

        World world = game.getLocation().getWorld();
        if (world == null) return;

        int id = stand.getId();
        String team = stand.getUniqueId().toString();
        ChatColor glow = ColorUtils.getClosestChatColor(color);

        try {
            for (Player player : world.getPlayers()) {
                glowing.setGlowing(id, team, player, glow);
                stand.getSettings().setGlow(true);
                stand.sendMetadata(seeing);
            }
        } catch (ReflectiveOperationException ignored) {

        }
    }

    private void setEquipment(@NotNull StandSettings settings, Color color) {
        Map<ItemSlot, ItemStack> equipment = settings.getEquipment();
        equipment.put(ItemSlot.CHEST, createArmor(Material.LEATHER_CHESTPLATE, color));
        equipment.put(ItemSlot.LEGS, createArmor(Material.LEATHER_LEGGINGS, color));
        equipment.put(ItemSlot.FEET, createArmor(Material.LEATHER_BOOTS, color));
    }

    private @NotNull ItemStack createArmor(Material material, Color color) {
        return new ItemBuilder(material)
                .setLeatherArmorMetaColor(color)
                .build();
    }

    private void lookAt(@NotNull Location origin, @NotNull Location target) {
        Vector direction = target.toVector().subtract(origin.toVector()).normalize();

        float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
        float pitch = (float) Math.toDegrees(Math.asin(-direction.getY()));

        origin.setYaw(yaw);
        origin.setPitch(pitch);
    }
}