package me.matsubara.roulette.util;

import com.cryptomorin.xseries.XEntityType;
import com.cryptomorin.xseries.XSound;
import com.cryptomorin.xseries.reflection.XReflection;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Parrot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.util.*;

public class ParrotUtils {

    private static final Map<EntityType, Sound> MOB_SOUND_MAP = new HashMap<>();

    // Class.
    private static final Class<?> PARROT_CLAZZ = Reflection.getNMSClass("world.entity.animal", "Parrot", "EntityParrot");
    private static final Class<?> ENTITY_TYPE_CLAZZ = Reflection.getNMSClass("world.entity", "EntityType", "EntityTypes");
    private static final Class<?> LEVEL_CLAZZ = Reflection.getNMSClass("world.level", "Level", "World");
    private static final Class<?> COMPOUND_TAG_CLAZZ = Reflection.getNMSClass("nbt", "CompoundTag", "NBTTagCompound");
    private static final @SuppressWarnings("deprecation") Class<?> ENTITY_CLAZZ = XReflection.getNMSClass("world.entity", "Entity");
    private static final @SuppressWarnings("deprecation") Class<?> CRAFT_WORLD_CLAZZ = XReflection.getCraftClass("CraftWorld");
    private static final @SuppressWarnings("deprecation") Class<?> CRAFT_ENTITY_CLAZZ = XReflection.getCraftClass("entity.CraftEntity");

    // Method.
    private static final MethodHandle GET_WORLD_HANDLE = Reflection.getMethod(CRAFT_WORLD_CLAZZ, "getHandle");
    private static final MethodHandle GET_BUKKIT_ENTITY = Reflection.getMethod(ENTITY_CLAZZ, "getBukkitEntity");
    private static final MethodHandle SAVE = Reflection.getMethod(CRAFT_ENTITY_CLAZZ, "save");

    // Constructor.
    private static final MethodHandle PARROT_CONSTRUCTOR = Reflection.getConstructor(PARROT_CLAZZ, ENTITY_TYPE_CLAZZ, LEVEL_CLAZZ);
    private static final MethodHandle COMPOUND_TAG_CONSTRUCTOR = Reflection.getConstructor(COMPOUND_TAG_CLAZZ);

    // Field.
    private static final Object PARROT_TYPE;
    public static final Object EMPTY_NBT;

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

        String parrotType;
        if (XReflection.supports(20, 6)) {
            parrotType = "ax";
        } else if (XReflection.supports(20, 3)) {
            parrotType = "au";
        } else if (XReflection.supports(19, 4)) {
            parrotType = "at";
        } else if (XReflection.supports(19, 3)) {
            parrotType = "ap";
        } else if (XReflection.supports(19)) {
            parrotType = "ao";
        } else if (XReflection.supports(17)) {
            parrotType = "al";
        } else parrotType = null;

        PARROT_TYPE = parrotType != null ? Reflection.getFieldValue(Reflection.getField(ENTITY_TYPE_CLAZZ, ENTITY_TYPE_CLAZZ, parrotType, true, "PARROT")) : null;

        Object temp;
        try {
            temp = COMPOUND_TAG_CONSTRUCTOR != null ? COMPOUND_TAG_CONSTRUCTOR.invoke() : null;
        } catch (Throwable throwable) {
            temp = null;
        }
        EMPTY_NBT = temp;
    }

    public static @Nullable Object createParrot(World world, Parrot.Variant variant) {
        Preconditions.checkNotNull(PARROT_TYPE);
        Preconditions.checkNotNull(PARROT_CONSTRUCTOR);
        Preconditions.checkNotNull(GET_WORLD_HANDLE);
        Preconditions.checkNotNull(GET_BUKKIT_ENTITY);
        Preconditions.checkNotNull(SAVE);

        try {
            Object craftWorld = CRAFT_WORLD_CLAZZ.cast(world);

            Object parrot = PARROT_CONSTRUCTOR.invoke(PARROT_TYPE, GET_WORLD_HANDLE.invoke(craftWorld));

            Entity invoke = (Entity) GET_BUKKIT_ENTITY.invoke(parrot);
            if (invoke instanceof Parrot temp) temp.setVariant(variant);

            return SAVE.invoke(invoke);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            return null;
        }
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