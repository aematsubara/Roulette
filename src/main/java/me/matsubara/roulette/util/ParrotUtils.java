package me.matsubara.roulette.util;

import com.cryptomorin.xseries.XEntityType;
import com.cryptomorin.xseries.XSound;
import com.google.common.collect.Lists;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ParrotUtils {

    private static final Map<EntityType, Sound> MOB_SOUND_MAP = new HashMap<>();

    static {
        Map<XEntityType, XSound> tempMap = new HashMap<>();
        tempMap.put(XEntityType.BLAZE, XSound.ENTITY_PARROT_IMITATE_BLAZE);
        tempMap.put(XEntityType.BOGGED, XSound.ENTITY_PARROT_IMITATE_BOGGED);
        tempMap.put(XEntityType.BREEZE, XSound.ENTITY_PARROT_IMITATE_BREEZE);
        tempMap.put(XEntityType.CAVE_SPIDER, XSound.ENTITY_PARROT_IMITATE_SPIDER);
        tempMap.put(XEntityType.CREEPER, XSound.ENTITY_PARROT_IMITATE_CREEPER);
        tempMap.put(XEntityType.DROWNED, XSound.ENTITY_PARROT_IMITATE_DROWNED);
        tempMap.put(XEntityType.ELDER_GUARDIAN, XSound.ENTITY_PARROT_IMITATE_ELDER_GUARDIAN);
        tempMap.put(XEntityType.ENDER_DRAGON, XSound.ENTITY_PARROT_IMITATE_ENDER_DRAGON);
        tempMap.put(XEntityType.ENDERMITE, XSound.ENTITY_PARROT_IMITATE_ENDERMITE);
        tempMap.put(XEntityType.EVOKER, XSound.ENTITY_PARROT_IMITATE_EVOKER);
        tempMap.put(XEntityType.GHAST, XSound.ENTITY_PARROT_IMITATE_GHAST);
        tempMap.put(XEntityType.GUARDIAN, XSound.ENTITY_PARROT_IMITATE_GUARDIAN);
        tempMap.put(XEntityType.HOGLIN, XSound.ENTITY_PARROT_IMITATE_HOGLIN);
        tempMap.put(XEntityType.HUSK, XSound.ENTITY_PARROT_IMITATE_HUSK);
        tempMap.put(XEntityType.ILLUSIONER, XSound.ENTITY_PARROT_IMITATE_ILLUSIONER);
        tempMap.put(XEntityType.MAGMA_CUBE, XSound.ENTITY_PARROT_IMITATE_MAGMA_CUBE);
        tempMap.put(XEntityType.PHANTOM, XSound.ENTITY_PARROT_IMITATE_PHANTOM);
        tempMap.put(XEntityType.PIGLIN, XSound.ENTITY_PARROT_IMITATE_PIGLIN);
        tempMap.put(XEntityType.PIGLIN_BRUTE, XSound.ENTITY_PARROT_IMITATE_PIGLIN_BRUTE);
        tempMap.put(XEntityType.PILLAGER, XSound.ENTITY_PARROT_IMITATE_PILLAGER);
        tempMap.put(XEntityType.RAVAGER, XSound.ENTITY_PARROT_IMITATE_RAVAGER);
        tempMap.put(XEntityType.SHULKER, XSound.ENTITY_PARROT_IMITATE_SHULKER);
        tempMap.put(XEntityType.SILVERFISH, XSound.ENTITY_PARROT_IMITATE_SILVERFISH);
        tempMap.put(XEntityType.SKELETON, XSound.ENTITY_PARROT_IMITATE_SKELETON);
        tempMap.put(XEntityType.SLIME, XSound.ENTITY_PARROT_IMITATE_SLIME);
        tempMap.put(XEntityType.SPIDER, XSound.ENTITY_PARROT_IMITATE_SPIDER);
        tempMap.put(XEntityType.STRAY, XSound.ENTITY_PARROT_IMITATE_STRAY);
        tempMap.put(XEntityType.VEX, XSound.ENTITY_PARROT_IMITATE_VEX);
        tempMap.put(XEntityType.VINDICATOR, XSound.ENTITY_PARROT_IMITATE_VINDICATOR);
        tempMap.put(XEntityType.WARDEN, XSound.ENTITY_PARROT_IMITATE_WARDEN);
        tempMap.put(XEntityType.WITCH, XSound.ENTITY_PARROT_IMITATE_WITCH);
        tempMap.put(XEntityType.WITHER, XSound.ENTITY_PARROT_IMITATE_WITHER);
        tempMap.put(XEntityType.WITHER_SKELETON, XSound.ENTITY_PARROT_IMITATE_WITHER_SKELETON);
        tempMap.put(XEntityType.ZOGLIN, XSound.ENTITY_PARROT_IMITATE_ZOGLIN);
        tempMap.put(XEntityType.ZOMBIE, XSound.ENTITY_PARROT_IMITATE_ZOMBIE);
        tempMap.put(XEntityType.ZOMBIE_VILLAGER, XSound.ENTITY_PARROT_IMITATE_ZOMBIE_VILLAGER);

        tempMap.forEach((type, sound) -> {
            if (type.isSupported() && sound.isSupported()) {
                MOB_SOUND_MAP.put(type.get(), sound.parseSound());
            }
        });
    }

    public static Sound getAmbient(@NotNull World world) {
        Random random = PluginUtils.RANDOM;
        if (world.getDifficulty() != Difficulty.PEACEFUL && random.nextInt(100) == 0) {
            List<EntityType> list = Lists.newArrayList(MOB_SOUND_MAP.keySet());
            return getImitatedSound(list.get(random.nextInt(list.size())));
        }
        return Sound.ENTITY_PARROT_AMBIENT;
    }

    public static Sound getImitatedSound(EntityType type) {
        return MOB_SOUND_MAP.getOrDefault(type, Sound.ENTITY_PARROT_AMBIENT);
    }

    public static @Nullable Sound imitateNearbyMobs(@NotNull Location location) {
        World world = location.getWorld();
        if (world == null) return null;

        Random random = PluginUtils.RANDOM;
        if (random.nextInt(50) != 0) return null;

        List<Entity> nearby = new ArrayList<>(world.getNearbyEntities(location, 15.0d, 15.0d, 15.0d, temp -> MOB_SOUND_MAP.containsKey(temp.getType())));
        if (nearby.isEmpty()) return null;

        Entity randomNearby = nearby.get(random.nextInt(nearby.size()));
        if (randomNearby.isSilent()) return null;

        return getImitatedSound(randomNearby.getType());
    }

    public static float getPitch() {
        Random random = PluginUtils.RANDOM;
        return (random.nextFloat() - random.nextFloat()) * 0.2f + 1.0f;
    }

    public enum ParrotShoulder {
        LEFT,
        RIGHT;

        public boolean isLeft() {
            return this == LEFT;
        }

        public boolean isRight() {
            return this == RIGHT;
        }
    }
}