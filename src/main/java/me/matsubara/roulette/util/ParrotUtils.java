package me.matsubara.roulette.util;

import com.cryptomorin.xseries.XEntityType;
import com.cryptomorin.xseries.XSound;
import com.google.common.collect.ImmutableList;
import org.bukkit.Difficulty;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ParrotUtils {

    private static final Map<EntityType, XSound> MOB_SOUND_MAP = new HashMap<>();
    private static final List<EntityType> VALID_MOB;

    static {
        Map<XEntityType, XSound> temp = new HashMap<>();
        temp.put(XEntityType.BLAZE, XSound.ENTITY_PARROT_IMITATE_BLAZE);
        temp.put(XEntityType.BOGGED, XSound.ENTITY_PARROT_IMITATE_BOGGED);
        temp.put(XEntityType.BREEZE, XSound.ENTITY_PARROT_IMITATE_BREEZE);
        temp.put(XEntityType.CAVE_SPIDER, XSound.ENTITY_PARROT_IMITATE_SPIDER);
        temp.put(XEntityType.CREEPER, XSound.ENTITY_PARROT_IMITATE_CREEPER);
        temp.put(XEntityType.DROWNED, XSound.ENTITY_PARROT_IMITATE_DROWNED);
        temp.put(XEntityType.ELDER_GUARDIAN, XSound.ENTITY_PARROT_IMITATE_ELDER_GUARDIAN);
        temp.put(XEntityType.ENDER_DRAGON, XSound.ENTITY_PARROT_IMITATE_ENDER_DRAGON);
        temp.put(XEntityType.ENDERMITE, XSound.ENTITY_PARROT_IMITATE_ENDERMITE);
        temp.put(XEntityType.EVOKER, XSound.ENTITY_PARROT_IMITATE_EVOKER);
        temp.put(XEntityType.GHAST, XSound.ENTITY_PARROT_IMITATE_GHAST);
        temp.put(XEntityType.GUARDIAN, XSound.ENTITY_PARROT_IMITATE_GUARDIAN);
        temp.put(XEntityType.HOGLIN, XSound.ENTITY_PARROT_IMITATE_HOGLIN);
        temp.put(XEntityType.HUSK, XSound.ENTITY_PARROT_IMITATE_HUSK);
        temp.put(XEntityType.ILLUSIONER, XSound.ENTITY_PARROT_IMITATE_ILLUSIONER);
        temp.put(XEntityType.MAGMA_CUBE, XSound.ENTITY_PARROT_IMITATE_MAGMA_CUBE);
        temp.put(XEntityType.PHANTOM, XSound.ENTITY_PARROT_IMITATE_PHANTOM);
        temp.put(XEntityType.PIGLIN, XSound.ENTITY_PARROT_IMITATE_PIGLIN);
        temp.put(XEntityType.PIGLIN_BRUTE, XSound.ENTITY_PARROT_IMITATE_PIGLIN_BRUTE);
        temp.put(XEntityType.PILLAGER, XSound.ENTITY_PARROT_IMITATE_PILLAGER);
        temp.put(XEntityType.RAVAGER, XSound.ENTITY_PARROT_IMITATE_RAVAGER);
        temp.put(XEntityType.SHULKER, XSound.ENTITY_PARROT_IMITATE_SHULKER);
        temp.put(XEntityType.SILVERFISH, XSound.ENTITY_PARROT_IMITATE_SILVERFISH);
        temp.put(XEntityType.SKELETON, XSound.ENTITY_PARROT_IMITATE_SKELETON);
        temp.put(XEntityType.SLIME, XSound.ENTITY_PARROT_IMITATE_SLIME);
        temp.put(XEntityType.SPIDER, XSound.ENTITY_PARROT_IMITATE_SPIDER);
        temp.put(XEntityType.STRAY, XSound.ENTITY_PARROT_IMITATE_STRAY);
        temp.put(XEntityType.VEX, XSound.ENTITY_PARROT_IMITATE_VEX);
        temp.put(XEntityType.VINDICATOR, XSound.ENTITY_PARROT_IMITATE_VINDICATOR);
        temp.put(XEntityType.WARDEN, XSound.ENTITY_PARROT_IMITATE_WARDEN);
        temp.put(XEntityType.WITCH, XSound.ENTITY_PARROT_IMITATE_WITCH);
        temp.put(XEntityType.WITHER, XSound.ENTITY_PARROT_IMITATE_WITHER);
        temp.put(XEntityType.WITHER_SKELETON, XSound.ENTITY_PARROT_IMITATE_WITHER_SKELETON);
        temp.put(XEntityType.ZOGLIN, XSound.ENTITY_PARROT_IMITATE_ZOGLIN);
        temp.put(XEntityType.ZOMBIE, XSound.ENTITY_PARROT_IMITATE_ZOMBIE);
        temp.put(XEntityType.ZOMBIE_VILLAGER, XSound.ENTITY_PARROT_IMITATE_ZOMBIE_VILLAGER);

        temp.forEach((type, sound) -> {
            if (!type.isSupported() || !sound.isSupported()) return;
            MOB_SOUND_MAP.put(type.get(), sound);
        });

        VALID_MOB = ImmutableList.copyOf(MOB_SOUND_MAP.keySet());
    }

    public static XSound getAmbient(@NotNull World world) {
        Random random = PluginUtils.RANDOM;
        if (world.getDifficulty() != Difficulty.PEACEFUL && random.nextInt(100) == 0) {
            EntityType type = VALID_MOB.get(random.nextInt(VALID_MOB.size()));
            return MOB_SOUND_MAP.getOrDefault(type, XSound.ENTITY_PARROT_AMBIENT);
        }
        return XSound.ENTITY_PARROT_AMBIENT;
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