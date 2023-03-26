package me.matsubara.roulette.model.stand;

import com.cryptomorin.xseries.ReflectionUtils;
import com.google.common.collect.Lists;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.hook.ViaExtension;
import me.matsubara.roulette.util.Lang3Utils;
import me.matsubara.roulette.util.PluginUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.EulerAngle;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

@SuppressWarnings({"ConstantConditions"})
public final class PacketStand {

    // Plugin instance.
    private static final RoulettePlugin PLUGIN = JavaPlugin.getPlugin(RoulettePlugin.class);

    // Entity instance.
    private Object stand;

    // Entity location.
    private Location location;

    // Set with the unique id of the players who aren't seeing the entity due to the distance.
    private Set<UUID> ignored;

    // Entity unique id.
    private int entityId;

    // Entity passengers.
    private int[] passengersId;

    // Entity properties.
    private StandSettings settings;

    // Protocol version of the server, only needed for 1.17.
    private static int PROTOCOL = -1;

    // Version of the server.
    private static final int VERSION = ReflectionUtils.VER;

    // Methods factory.
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    @TestOnly
    private static final boolean FORCE_SYNC = true;

    // Classes.
    private static final Class<?> CRAFT_CHAT_MESSAGE;
    private static final Class<?> CRAFT_ENTITY;
    private static final Class<?> CRAFT_WORLD;
    private static final Class<?> CRAFT_ITEM_STACK;
    private static final Class<?> WORLD;
    private static final Class<?> WORLD_SERVER;
    private static final Class<?> ENTITY;
    private static final Class<?> ENTITY_LIVING;
    private static final Class<?> ENTITY_ARMOR_STAND;
    private static final Class<?> PACKET_SPAWN_ENTITY_LIVING;
    private static final Class<?> PACKET_ENTITY_HEAD_ROTATION;
    private static final Class<?> PACKET_ENTITY_TELEPORT;
    private static final Class<?> PACKET_ENTITY_LOOK;
    private static final Class<?> DATA_WATCHER;
    private static final Class<?> PACKET_ENTITY_METADATA;
    private static final Class<?> PACKET_MOUNT;
    private static final Class<?> PACKET_ENTITY_EQUIPMENT;
    private static final Class<?> ENUM_ITEM_SLOT;
    private static final Class<?> ITEM_STACK;
    private static final Class<?> PACKET_ENTITY_DESTROY;
    private static final Class<?> PAIR;
    private static final Class<?> SHARED_CONSTANTS;
    private static final Class<?> GAME_VERSION;
    private static final Class<?> VECTOR3F;
    private static final Class<?> I_CHAT_BASE_COMPONENT;
    private static final Class<?> ATTRIBUTE_MODIFIABLE;
    private static final Class<?> ATTRIBUTE_BASE;
    private static final Class<?> GENERIC_ATTRIBUTES;
    private static final Class<?> PACKET_UPDATE_ATTRIBUTES;

    // Methods.
    private static final MethodHandle getHandle;
    private static final MethodHandle getDataWatcher;
    private static final MethodHandle asNMSCopy;
    private static final MethodHandle of;
    private static final MethodHandle getBukkitEntity;
    private static final MethodHandle setFlag;
    private static final MethodHandle fromStringOrNull;
    private static final MethodHandle getId;
    private static final MethodHandle setLocation;
    private static final MethodHandle setInvisible;
    private static final MethodHandle setArms;
    private static final MethodHandle setBasePlate;
    private static final MethodHandle setSmall;
    private static final MethodHandle setMarker;
    private static final MethodHandle setCustomName;
    private static final MethodHandle setCustomNameVisible;
    private static final MethodHandle setHeadPose;
    private static final MethodHandle setBodyPose;
    private static final MethodHandle setLeftArmPose;
    private static final MethodHandle setRightArmPose;
    private static final MethodHandle setLeftLegPose;
    private static final MethodHandle setRightLegPose;
    private static final MethodHandle getAttributeInstance;
    private static final MethodHandle setValue;
    private static final MethodHandle getNonDefaultValues;

    // Constructors.
    private static final MethodHandle entityArmorStand;
    private static final MethodHandle packetSpawnEntityLiving;
    private static final MethodHandle packetEntityHeadRotation;
    private static final MethodHandle packetEntityTeleport;
    private static final MethodHandle packetEntityLook;
    private static final MethodHandle packetEntityMetadata;
    private static final MethodHandle packetMount;
    private static final MethodHandle packetEntityEquipment;
    private static final MethodHandle packetEntityDestroy;
    private static final MethodHandle vector3f;
    private static final MethodHandle packetUpdateAttributes;

    // Fields.
    private static final MethodHandle maxHealth;

    static {
        // Initialize classes.
        CRAFT_CHAT_MESSAGE = (VERSION > 12) ? ReflectionUtils.getCraftClass("util.CraftChatMessage") : null;
        CRAFT_ENTITY = ReflectionUtils.getCraftClass("entity.CraftEntity");
        CRAFT_WORLD = ReflectionUtils.getCraftClass("CraftWorld");
        CRAFT_ITEM_STACK = ReflectionUtils.getCraftClass("inventory.CraftItemStack");
        WORLD = ReflectionUtils.getNMSClass("world.level", "World");
        WORLD_SERVER = ReflectionUtils.getNMSClass("server.level", "WorldServer");
        ENTITY = ReflectionUtils.getNMSClass("world.entity", "Entity");
        ENTITY_LIVING = ReflectionUtils.getNMSClass("world.entity", "EntityLiving");
        ENTITY_ARMOR_STAND = ReflectionUtils.getNMSClass("world.entity.decoration", "EntityArmorStand");
        PACKET_SPAWN_ENTITY_LIVING = ReflectionUtils.getNMSClass(
                "network.protocol.game",
                VERSION > 18 ? "PacketPlayOutSpawnEntity" : "PacketPlayOutSpawnEntityLiving"); // SpawnEntityLiving is removed since 1.19.
        PACKET_ENTITY_HEAD_ROTATION = ReflectionUtils.getNMSClass("network.protocol.game", "PacketPlayOutEntityHeadRotation");
        PACKET_ENTITY_TELEPORT = ReflectionUtils.getNMSClass("network.protocol.game", "PacketPlayOutEntityTeleport");
        PACKET_ENTITY_LOOK = ReflectionUtils.getNMSClass("network.protocol.game", "PacketPlayOutEntity$PacketPlayOutEntityLook");
        DATA_WATCHER = ReflectionUtils.getNMSClass("network.syncher", "DataWatcher");
        PACKET_ENTITY_METADATA = ReflectionUtils.getNMSClass("network.protocol.game", "PacketPlayOutEntityMetadata");
        PACKET_MOUNT = ReflectionUtils.getNMSClass("network.protocol.game", "PacketPlayOutMount");
        PACKET_ENTITY_EQUIPMENT = ReflectionUtils.getNMSClass("network.protocol.game", "PacketPlayOutEntityEquipment");
        ENUM_ITEM_SLOT = ReflectionUtils.getNMSClass("world.entity", "EnumItemSlot");
        ITEM_STACK = ReflectionUtils.getNMSClass("world.item", "ItemStack");
        PACKET_ENTITY_DESTROY = ReflectionUtils.getNMSClass("network.protocol.game", "PacketPlayOutEntityDestroy");
        PAIR = (VERSION > 15) ? getUnversionedClass("com.mojang.datafixers.util.Pair") : null;
        SHARED_CONSTANTS = (VERSION == 17) ? ReflectionUtils.getNMSClass("SharedConstants") : null;
        GAME_VERSION = (VERSION == 17) ? getUnversionedClass("com.mojang.bridge.game.GameVersion") : null;
        VECTOR3F = ReflectionUtils.getNMSClass("core", "Vector3f");
        I_CHAT_BASE_COMPONENT = ReflectionUtils.getNMSClass("network.chat", "IChatBaseComponent");
        ATTRIBUTE_MODIFIABLE = ReflectionUtils.getNMSClass("world.entity.ai.attributes", (VERSION > 15) ? "AttributeModifiable" : "AttributeInstance");
        ATTRIBUTE_BASE = ReflectionUtils.getNMSClass("world.entity.ai.attributes", (VERSION > 15) ? "AttributeBase" : "IAttribute");
        GENERIC_ATTRIBUTES = ReflectionUtils.getNMSClass("world.entity.ai.attributes", "GenericAttributes");
        PACKET_UPDATE_ATTRIBUTES = ReflectionUtils.getNMSClass("network.protocol.game", "PacketPlayOutUpdateAttributes");

        // Initialize methods.
        getHandle = getMethod(CRAFT_WORLD, "getHandle", MethodType.methodType(WORLD_SERVER));
        getDataWatcher = getMethod(ENTITY_ARMOR_STAND, "getDataWatcher", MethodType.methodType(DATA_WATCHER), false, true, "ai", "al", "aj");
        asNMSCopy = getMethod(CRAFT_ITEM_STACK, "asNMSCopy", MethodType.methodType(ITEM_STACK, ItemStack.class), true, true);
        of = (PAIR == null) ? null : getMethod(PAIR, "of", MethodType.methodType(PAIR, Object.class, Object.class), true, true);
        getBukkitEntity = getMethod(ENTITY_ARMOR_STAND, "getBukkitEntity", MethodType.methodType(CRAFT_ENTITY));
        getAttributeInstance = getMethod(ENTITY_ARMOR_STAND, "getAttributeInstance", MethodType.methodType(ATTRIBUTE_MODIFIABLE, ATTRIBUTE_BASE), false, true, "a");
        setValue = getMethod(ATTRIBUTE_MODIFIABLE, "setValue", MethodType.methodType(void.class, double.class), false, true, "a");
        getNonDefaultValues = getMethod(DATA_WATCHER, "getNonDefaultValues", MethodType.methodType(List.class), false, false, "c");

        // Since 1.18 is obfuscated af, we're using getBukkitEntity() and then bukkit methods.
        if (VERSION < 18) {
            setFlag = getMethod(ENTITY_ARMOR_STAND, "setFlag", MethodType.methodType(void.class, int.class, boolean.class));
            fromStringOrNull = (CRAFT_CHAT_MESSAGE == null) ? null : getMethod(CRAFT_CHAT_MESSAGE, "fromStringOrNull", MethodType.methodType(I_CHAT_BASE_COMPONENT, String.class), true, true);
            getId = getMethod(ENTITY_ARMOR_STAND, "getId", MethodType.methodType(int.class));
            setLocation = getMethod(ENTITY_ARMOR_STAND, "setLocation", MethodType.methodType(void.class, double.class, double.class, double.class, float.class, float.class));
            setInvisible = getMethod(ENTITY_ARMOR_STAND, "setInvisible", MethodType.methodType(void.class, boolean.class));
            setArms = getMethod(ENTITY_ARMOR_STAND, "setArms", MethodType.methodType(void.class, boolean.class));
            setBasePlate = getMethod(ENTITY_ARMOR_STAND, "setBasePlate", MethodType.methodType(void.class, boolean.class));
            setSmall = getMethod(ENTITY_ARMOR_STAND, "setSmall", MethodType.methodType(void.class, boolean.class));
            setMarker = getMethod(ENTITY_ARMOR_STAND, (VERSION == 8) ? "n" : "setMarker", MethodType.methodType(void.class, boolean.class));
            setCustomName = getMethod(ENTITY_ARMOR_STAND, "setCustomName", MethodType.methodType(void.class, (CRAFT_CHAT_MESSAGE == null) ? String.class : I_CHAT_BASE_COMPONENT));
            setCustomNameVisible = getMethod(ENTITY_ARMOR_STAND, "setCustomNameVisible", MethodType.methodType(void.class, boolean.class));
            setHeadPose = getMethod(ENTITY_ARMOR_STAND, "setHeadPose", MethodType.methodType(void.class, VECTOR3F));
            setBodyPose = getMethod(ENTITY_ARMOR_STAND, "setBodyPose", MethodType.methodType(void.class, VECTOR3F));
            setLeftArmPose = getMethod(ENTITY_ARMOR_STAND, "setLeftArmPose", MethodType.methodType(void.class, VECTOR3F));
            setRightArmPose = getMethod(ENTITY_ARMOR_STAND, "setRightArmPose", MethodType.methodType(void.class, VECTOR3F));
            setLeftLegPose = getMethod(ENTITY_ARMOR_STAND, "setLeftLegPose", MethodType.methodType(void.class, VECTOR3F));
            setRightLegPose = getMethod(ENTITY_ARMOR_STAND, "setRightLegPose", MethodType.methodType(void.class, VECTOR3F));
        } else {
            // Get setFlag() method by its parameter types since the name is obfuscated.
            setFlag = getSetFlagMethod();
            fromStringOrNull = null;
            getId = null;
            setLocation = null;
            setInvisible = null;
            setArms = null;
            setBasePlate = null;
            setSmall = null;
            setMarker = null;
            setCustomName = null;
            setCustomNameVisible = null;
            setHeadPose = null;
            setBodyPose = null;
            setLeftArmPose = null;
            setRightArmPose = null;
            setLeftLegPose = null;
            setRightLegPose = null;
        }

        // Initialize constructors.
        entityArmorStand = getConstructor(ENTITY_ARMOR_STAND, WORLD, double.class, double.class, double.class);
        packetSpawnEntityLiving = getConstructor(PACKET_SPAWN_ENTITY_LIVING, VERSION > 18 ? ENTITY : ENTITY_LIVING);
        packetEntityHeadRotation = getConstructor(PACKET_ENTITY_HEAD_ROTATION, ENTITY, byte.class);
        packetEntityTeleport = getConstructor(PACKET_ENTITY_TELEPORT, ENTITY);
        packetEntityLook = getConstructor(PACKET_ENTITY_LOOK, int.class, byte.class, byte.class, boolean.class);
        MethodHandle metadata = getConstructor(PACKET_ENTITY_METADATA, false, int.class, DATA_WATCHER, boolean.class);
        packetEntityMetadata = metadata != null ? metadata : getConstructor(PACKET_ENTITY_METADATA, int.class, List.class);
        packetMount = (VERSION > 16) ? getConstructor(PACKET_MOUNT, ENTITY) : getConstructor(PACKET_MOUNT);
        packetEntityEquipment = (VERSION > 15) ?
                getConstructor(PACKET_ENTITY_EQUIPMENT, int.class, List.class) :
                getConstructor(PACKET_ENTITY_EQUIPMENT, int.class, ENUM_ITEM_SLOT, ITEM_STACK);
        packetEntityDestroy = getConstructor(PACKET_ENTITY_DESTROY, PROTOCOL == 755 ? int.class : int[].class);
        vector3f = getConstructor(VECTOR3F, float.class, float.class, float.class);
        packetUpdateAttributes = getConstructor(PACKET_UPDATE_ATTRIBUTES, int.class, Collection.class);

        try {
            // Get protocol version, only needed for 1.17.
            if (VERSION == 17) {
                MethodHandle getVersion = getMethod(SHARED_CONSTANTS, "getGameVersion", MethodType.methodType(GAME_VERSION), true, true);
                MethodHandle getProtocol = getMethod(GAME_VERSION, "getProtocolVersion", MethodType.methodType(int.class));

                Object gameVersion = getVersion.invoke();
                PROTOCOL = (int) getProtocol.invoke(gameVersion);
            }
        } catch (Throwable exception) {
            exception.printStackTrace();
        }

        maxHealth = getField(GENERIC_ATTRIBUTES, "MAX_HEALTH", "maxHealth", "a");
    }

    public PacketStand(Location location, StandSettings settings) {
        this(location, settings, true);
    }

    public PacketStand(Location location, StandSettings settings, boolean showEveryone) {
        Lang3Utils.notNull(location.getWorld(), "World can't be null.");

        try {
            Object craftWorld = CRAFT_WORLD.cast(location.getWorld());
            Object nmsWorld = getHandle.invoke(craftWorld);

            this.stand = entityArmorStand.invoke(nmsWorld, location.getX(), location.getY(), location.getZ());
            this.location = location;
            this.ignored = new HashSet<>();
            this.entityId = (getId != null) ? (int) getId.invoke(stand) : getBukkitEntity().getEntityId();
            this.passengersId = new int[]{};
            this.settings = settings;

            // Set the initial location of this entity.
            setLocation(location);

            // Set settings.
            setInvisible(settings.isInvisible());
            setSmall(settings.isSmall());
            setBasePlate(settings.hasBasePlate());
            setArms(settings.hasArms());
            setMarker(settings.isMarker());
            setOnFire(settings.isOnFire());
            if (settings.getCustomName() != null) setCustomName(settings.getCustomName());
            setCustomNameVisible(settings.isCustomNameVisible());

            // Set poses.
            setHeadPose(settings.getHeadPose());
            setBodyPose(settings.getBodyPose());
            setLeftArmPose(settings.getLeftArmPose());
            setRightArmPose(settings.getRightArmPose());
            setLeftLegPose(settings.getLeftLegPose());
            setRightLegPose(settings.getRightLegPose());

            // Set equipment.
            setEquipment(settings.getHelmet(), ItemSlot.HEAD);
            setEquipment(settings.getChestplate(), ItemSlot.CHEST);
            setEquipment(settings.getLeggings(), ItemSlot.LEGS);
            setEquipment(settings.getBoots(), ItemSlot.FEET);
            setEquipment(settings.getMainHand(), ItemSlot.MAINHAND);
            setEquipment(settings.getOffHand(), ItemSlot.OFFHAND);

            // Send spawn and teleport packet to all players.
            if (showEveryone) spawn();
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    public int getEntityId() {
        return entityId;
    }

    public Location getLocation() {
        return location;
    }

    @SuppressWarnings("unused")
    public StandSettings getSettings() {
        return settings;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isInRange(Location location) {
        int renderDistance = PLUGIN.getConfigManager().getRenderDistance();
        double distance = Math.min(renderDistance * renderDistance, Math.pow(Bukkit.getViewDistance() << 4, 2));

        if (!this.location.getWorld().equals(location.getWorld())) return false;
        try {
            return this.location.distanceSquared(location) <= distance;
        } catch (IllegalArgumentException exception) {
            // Player isn't in the same world, don't show.
            return false;
        }
    }

    /**
     * Spawn the entity to every player in the same world.
     */
    public void spawn() {
        for (Player player : location.getWorld().getPlayers()) {
            spawn(player);
        }
    }

    /**
     * Spawn the entity for a specific player.
     */
    public void spawn(Player player) {
        if (!isInRange(player.getLocation())) return;
        if (!PLUGIN.isEnabled()) return;

        try {
            Object packetSpawn = packetSpawnEntityLiving.invoke(stand);
            sendPacket(player, packetSpawn);

            ignored.remove(player.getUniqueId());

            Bukkit.getScheduler().runTaskAsynchronously(PLUGIN, () -> {
                showPassenger(player);
                updateLocation();
                updateRotation();
                updateMetadata();
                updateEquipment();
                hideHearts();
            });
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    /**
     * Set the current location of the entity.
     * You must teleport the entity using teleport(), or teleport(Location) instead of this method.
     * If this entity isn't spawned yet, you don't need to call any method, just spawn the entity.
     *
     * @see PacketStand#updateLocation()
     * @see PacketStand#teleport(Location)
     */
    public void setLocation(Location location) {
        try {
            this.location = location;
            if (setLocation != null) {
                setLocation.invoke(stand, location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
            } else {
                getBukkitEntity().teleport(location);
            }
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    /**
     * Teleports the entity to the previously established location.
     *
     * @see PacketStand#setLocation(Location)
     */
    public void updateLocation() {
        try {
            Object packetTeleport = packetEntityTeleport.invoke(stand);
            sendPacket(packetTeleport, true);
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    /**
     * Updates the entity rotation.
     */
    public void updateRotation() {
        try {
            byte yaw = (byte) (location.getYaw() * 256.0f / 360.0f);
            byte pitch = (byte) (location.getPitch() * 256.0f / 360.0f);

            Object packetRotation = packetEntityHeadRotation.invoke(stand, yaw);
            sendPacket(packetRotation, true);

            Object packetLook = packetEntityLook.invoke(entityId, yaw, pitch, true);
            sendPacket(packetLook, true);
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    /**
     * Set the current location of the entity.
     */
    public void teleport(Location location) {
        setLocation(location);
        updateLocation();
        updateRotation();
    }

    public void setInvisible(boolean invisible) {
        try {
            settings.setInvisible(invisible);
            if (setInvisible != null) {
                setInvisible.invoke(stand, invisible);
            } else {
                getBukkitEntity().setVisible(!invisible);
            }
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    public void setArms(boolean arms) {
        try {
            settings.setArms(arms);
            if (setArms != null) {
                setArms.invoke(stand, arms);
            } else {
                getBukkitEntity().setArms(arms);
            }
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    public void setBasePlate(boolean baseplate) {
        try {
            settings.setBasePlate(baseplate);
            if (setBasePlate != null) {
                // For some reason must be negated.
                setBasePlate.invoke(stand, !baseplate);
            } else {
                getBukkitEntity().setBasePlate(baseplate);
            }
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    public void setSmall(boolean small) {
        try {
            settings.setSmall(small);
            if (setSmall != null) {
                setSmall.invoke(stand, small);
            } else {
                getBukkitEntity().setSmall(small);
            }
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    public void setMarker(boolean marker) {
        try {
            settings.setMarker(marker);
            if (setMarker != null) {
                setMarker.invoke(stand, marker);
            } else {
                getBukkitEntity().setMarker(marker);
            }
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    public void setOnFire(boolean fire) {
        // Only works on 1.9+.
        if (VERSION < 9) return;

        settings.setOnFire(fire);

        try {
            setFlag.invoke(stand, 0, fire);
            updateMetadata();
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    public void setCustomName(@Nullable String name) {
        try {
            if (name != null) {
                name = PluginUtils.translate(name);
            } else {
                name = "";
                setCustomNameVisible(false);
            }
            if (setCustomName != null) {
                setCustomName.invoke(stand, (CRAFT_CHAT_MESSAGE == null) ? name : fromStringOrNull.invoke(name));
            } else {
                getBukkitEntity().setCustomName(name);
            }
            settings.setCustomName(name);
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    public void setCustomNameVisible(boolean customNameVisible) {
        try {
            settings.setCustomNameVisible(customNameVisible);
            if (setCustomNameVisible != null) {
                setCustomNameVisible.invoke(stand, customNameVisible);
            } else {
                getBukkitEntity().setCustomNameVisible(customNameVisible);
            }
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    /**
     * Set a player as a passenger of this entity and show to everyone.
     */
    public void setPassenger(Player player) {
        // If player isn't in range, can't be a passenger.
        if (player != null && isIgnored(player)) return;

        // If the array is empty, entity won't have passengers.
        this.passengersId = (player != null) ? new int[]{player.getEntityId()} : new int[]{};
        sendPassenger(null);
    }

    /**
     * Show passenger of this entity (if it has) to a player.
     */
    public void showPassenger(Player notSeeing) {
        if (!hasPassenger()) return;
        sendPassenger(notSeeing);
    }

    /**
     * Show the passenger of this entity to a certain player or everyone (if @to is null).
     */
    private void sendPassenger(@Nullable Player to) {
        try {
            Object packetMount;
            if (VERSION > 16) {
                packetMount = PacketStand.packetMount.invoke(stand);
            } else {
                packetMount = PacketStand.packetMount.invoke();
            }

            Field a = PACKET_MOUNT.getDeclaredField("a");
            a.setAccessible(true);
            a.set(packetMount, entityId);

            Field b = PACKET_MOUNT.getDeclaredField("b");
            b.setAccessible(true);
            b.set(packetMount, passengersId);

            if (to != null) {
                sendPacket(to, packetMount);
            } else {
                sendPacket(packetMount);
            }
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    public boolean hasPassenger() {
        return passengersId != null && passengersId.length > 0;
    }

    public boolean isPassenger(Player player) {
        for (int passengerId : passengersId) {
            if (passengerId == player.getEntityId()) return true;
        }
        return false;
    }

    public void hideHearts() {
        // Hide hearts when riding.
        try {
            Object maxHealthAttribute = maxHealth.invoke();

            Object attribute = getAttributeInstance.invoke(stand, maxHealthAttribute);
            setValue.invoke(attribute, 1);

            Object packet = packetUpdateAttributes.invoke(entityId, Lists.newArrayList(attribute));
            sendPacket(packet);
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    public Object getVector3f(EulerAngle angle) {
        try {
            return vector3f.invoke((float) Math.toDegrees(angle.getX()), (float) Math.toDegrees(angle.getY()), (float) Math.toDegrees(angle.getZ()));
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
        return null;
    }

    public void setHeadPose(EulerAngle headPose) {
        try {
            settings.setHeadPose(headPose);
            if (setHeadPose != null) {
                setHeadPose.invoke(stand, getVector3f(headPose));
            } else {
                getBukkitEntity().setHeadPose(headPose);
            }
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    public void setBodyPose(EulerAngle bodyPose) {
        try {
            settings.setBodyPose(bodyPose);
            if (setBodyPose != null) {
                setBodyPose.invoke(stand, getVector3f(bodyPose));
            } else {
                getBukkitEntity().setBodyPose(bodyPose);
            }
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    public void setLeftArmPose(EulerAngle leftArmPose) {
        try {
            settings.setLeftArmPose(leftArmPose);
            if (setLeftArmPose != null) {
                setLeftArmPose.invoke(stand, getVector3f(leftArmPose));
            } else {
                getBukkitEntity().setLeftArmPose(leftArmPose);
            }
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    public void setRightArmPose(EulerAngle rightArmPose) {
        try {
            settings.setRightArmPose(rightArmPose);
            if (setRightArmPose != null) {
                setRightArmPose.invoke(stand, getVector3f(rightArmPose));
            } else {
                getBukkitEntity().setRightArmPose(rightArmPose);
            }
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    public void setLeftLegPose(EulerAngle leftLegPose) {
        try {
            settings.setLeftLegPose(leftLegPose);
            if (setLeftLegPose != null) {
                setLeftLegPose.invoke(stand, getVector3f(leftLegPose));
            } else {
                getBukkitEntity().setLeftLegPose(leftLegPose);
            }
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    public void setRightLegPose(EulerAngle rightLegPose) {
        try {
            settings.setRightLegPose(rightLegPose);
            if (setRightLegPose != null) {
                setRightLegPose.invoke(stand, getVector3f(rightLegPose));
            } else {
                getBukkitEntity().setRightLegPose(rightLegPose);
            }
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    public enum ItemSlot {
        MAINHAND,
        OFFHAND,
        FEET,
        LEGS,
        CHEST,
        HEAD;

        private final char[] alphabet = "abcdefghijklmnopqrstuvwxyz".toCharArray();

        public Object get() {
            try {
                Field field = ENUM_ITEM_SLOT.getField(VERSION > 16 ? "" + alphabet[ordinal()] : name());
                return field.get(null);
            } catch (Throwable exception) {
                exception.printStackTrace();
                return null;
            }
        }
    }

    public void setEquipment(ItemStack item, ItemSlot slot) {
        if (slot == ItemSlot.MAINHAND) settings.setMainHand(item);
        if (slot == ItemSlot.OFFHAND) settings.setOffHand(item);
        if (slot == ItemSlot.HEAD) settings.setHelmet(item);
        if (slot == ItemSlot.CHEST) settings.setChestplate(item);
        if (slot == ItemSlot.LEGS) settings.setLeggings(item);
        if (slot == ItemSlot.FEET) settings.setBoots(item);

        try {
            Object itemStack = asNMSCopy.invoke(item);

            Object packetEquipment;
            if (VERSION > 15) {
                List<Object> list = new ArrayList<>();
                list.add(of.invoke(slot.get(), itemStack));

                packetEquipment = packetEntityEquipment.invoke(entityId, list);
            } else {
                packetEquipment = packetEntityEquipment.invoke(entityId, slot.get(), itemStack);
            }
            sendPacket(packetEquipment);
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    public void updateEquipment() {
        if (!settings.hasEquipment()) return;

        ItemStack helmet = settings.getHelmet();
        ItemStack chestplate = settings.getChestplate();
        ItemStack leggings = settings.getLeggings();
        ItemStack boots = settings.getBoots();
        ItemStack mainHand = settings.getMainHand();
        ItemStack offHand = settings.getOffHand();

        if (helmet != null) setEquipment(helmet, ItemSlot.HEAD);
        if (chestplate != null) setEquipment(chestplate, ItemSlot.CHEST);
        if (leggings != null) setEquipment(leggings, ItemSlot.LEGS);
        if (boots != null) setEquipment(boots, ItemSlot.FEET);
        if (mainHand != null) setEquipment(mainHand, ItemSlot.MAINHAND);
        if (offHand != null) setEquipment(offHand, ItemSlot.OFFHAND);
    }

    public void updateMetadata() {
        sendPacket(createMetadataPacket());
    }

    private Object createMetadataPacket() {
        try {
            Object watcher = getDataWatcher.invoke(stand);
            Object packetMetadata;
            if (getNonDefaultValues != null && packetEntityMetadata.type().parameterCount() == 2) {
                Object values = getNonDefaultValues.invoke(watcher);
                packetMetadata = packetEntityMetadata.invoke(entityId, values != null ? values : new ArrayList<>());
            } else {
                packetMetadata = packetEntityMetadata.invoke(entityId, watcher, true);
            }
            return packetMetadata;
        } catch (Throwable exception) {
            exception.printStackTrace();
            return null;
        }
    }

    public ArmorStand getBukkitEntity() {
        try {
            return ((ArmorStand) getBukkitEntity.invoke(stand));
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            return null;
        }
    }

    public void destroy() {
        for (Player player : location.getWorld().getPlayers()) {
            destroy(player);
        }
        ignored.clear();
    }

    public void destroy(Player player) {
        try {
            Object packetDestroy;
            if (PROTOCOL == 755) {
                packetDestroy = packetEntityDestroy.invoke(entityId);
            } else {
                packetDestroy = packetEntityDestroy.invoke(new int[]{entityId});
            }

            sendPacket(player, packetDestroy);
            ignored.add(player.getUniqueId());
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    public boolean isIgnored(Player player) {
        return ignored.contains(player.getUniqueId());
    }

    private void sendPacket(Player player, Object packet) {
        sendPacket(player, packet, false);
    }

    private void sendPacket(Player player, Object packet, boolean sync) {
        if (sync || FORCE_SYNC) ReflectionUtils.sendPacketSync(player, packet);
        else ReflectionUtils.sendPacket(player, packet);
    }

    private void sendPacket(Object packet) {
        sendPacket(packet, false);
    }

    private void sendPacket(Object packet, boolean sync) {
        boolean isEntityLook = packet.getClass().isAssignableFrom(PACKET_ENTITY_LOOK);
        boolean usingVia = PLUGIN.hasDependency("ViaVersion");

        for (Player player : location.getWorld().getPlayers()) {
            if (isIgnored(player)) continue;

            // 755 = 1.17.
            if (isEntityLook && (VERSION > 16 || (usingVia && ViaExtension.getPlayerVersion(player) > 754))) {
                continue;
            }

            sendPacket(player, packet, sync);
        }
    }

    private static Class<?> getUnversionedClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    @SuppressWarnings("SameParameterValue")
    private static MethodHandle getField(Class<?> refc, String name, String... extraNames) {
        try {
            Field field = refc.getDeclaredField(name);
            field.setAccessible(true);
            return LOOKUP.unreflectGetter(field);
        } catch (ReflectiveOperationException exception) {
            if (extraNames != null && extraNames.length > 0) {
                if (extraNames.length == 1) {
                    return getField(refc, extraNames[0]);
                }
                for (String extra : extraNames) {
                    int index = Lang3Utils.indexOf(extraNames, extra);
                    String[] rest = Lang3Utils.remove(extraNames, index);
                    return getField(refc, extra, rest);
                }
            }
            exception.printStackTrace();
            return null;
        }
    }

    private static MethodHandle getConstructor(Class<?> refc, Class<?>... types) {
        return getConstructor(refc, true, types);
    }

    private static MethodHandle getConstructor(Class<?> refc, boolean printStackTrace, Class<?>... types) {
        try {
            Constructor<?> constructor = refc.getDeclaredConstructor(types);
            constructor.setAccessible(true);
            return LOOKUP.unreflectConstructor(constructor);
        } catch (ReflectiveOperationException exception) {
            if (printStackTrace) exception.printStackTrace();
            return null;
        }
    }

    private static MethodHandle getMethod(Class<?> refc, String name, MethodType type) {
        return getMethod(refc, name, type, false, true);
    }

    private static MethodHandle getMethod(Class<?> refc, String name, MethodType type, boolean isStatic, boolean printStackTrace, String... extraNames) {
        try {
            if (isStatic) return LOOKUP.findStatic(refc, name, type);
            if (VERSION > 17) {
                Method method = refc.getMethod(name, type.parameterArray());
                if (!method.getReturnType().isAssignableFrom(type.returnType())) {
                    throw new NoSuchMethodException();
                }
                return LOOKUP.unreflect(method);
            }
            return LOOKUP.findVirtual(refc, name, type);
        } catch (ReflectiveOperationException exception) {
            if (extraNames != null && extraNames.length > 0) {
                if (extraNames.length == 1) {
                    return getMethod(refc, extraNames[0], type, isStatic, printStackTrace);
                }
                for (String extra : extraNames) {
                    int index = Lang3Utils.indexOf(extraNames, extra);
                    String[] rest = Lang3Utils.remove(extraNames, index);
                    return getMethod(refc, extra, type, isStatic, printStackTrace, rest);
                }
            }
            if (printStackTrace) exception.printStackTrace();
            return null;
        }
    }

    private static MethodHandle getSetFlagMethod() {
        // There are 2 methods with the same parameter types, the first one is what we want.
        for (Method method : ENTITY_ARMOR_STAND.getMethods()) {
            Parameter[] parameters = method.getParameters();
            if (parameters.length != 2) continue;
            if (!parameters[0].getType().equals(int.class)) continue;
            if (!parameters[1].getType().equals(boolean.class)) continue;

            try {
                return LOOKUP.unreflect(method);
            } catch (IllegalAccessException exception) {
                exception.printStackTrace();
                return null;
            }
        }
        return null;
    }
}