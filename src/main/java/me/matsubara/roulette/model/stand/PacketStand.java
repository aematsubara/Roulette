package me.matsubara.roulette.model.stand;

import com.cryptomorin.xseries.ReflectionUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.Getter;
import lombok.Setter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.util.Reflection;
import org.apache.commons.lang3.Validate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.EulerAngle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings({"ConstantConditions"})
@Getter
public final class PacketStand {

    // Plugin instance.
    private final RoulettePlugin plugin = JavaPlugin.getPlugin(RoulettePlugin.class);

    // Entity location.
    private Location location;

    // Entity yaw and pitch, ready to use in packets.
    private byte previousYaw, yaw;
    private byte pitch;

    // Set with the unique id of the players who aren't seeing the entity due to the distance.
    private final Map<UUID, IgnoreReason> ignored = new HashMap<>();

    public enum IgnoreReason {
        NONE,
        HOLOGRAM
    }

    // The render distance taken from the model.
    private final double renderDistance;

    // Entity id.
    private final int entityId;

    // Entity unique id.
    private final UUID entityUniqueId;

    // Entity properties.
    private final StandSettings settings;

    private @Setter boolean cacheMetadata;
    private List<Object> cachedMetadata;

    // Protocol version of the server, only needed for 1.17.
    private static int PROTOCOL = -1;

    // Version of the server.
    private static final int VERSION = ReflectionUtils.MINOR_NUMBER;

    private static final double BUKKIT_VIEW_DISTANCE = Math.pow(Bukkit.getViewDistance() << 4, 2);
    private final static char[] ALPHABET = "abcdefghijklmnopqrstuvwxyz".toCharArray();

    // Classes.
    private static final Class<?> CRAFT_CHAT_MESSAGE;
    private static final Class<?> CRAFT_ITEM_STACK;
    private static final Class<?> ENTITY;
    private static final Class<?> ENTITY_ARMOR_STAND;
    private static final Class<?> PACKET_SPAWN_ENTITY_LIVING;
    private static final Class<?> PACKET_ENTITY_HEAD_ROTATION;
    private static final Class<?> PACKET_ENTITY_TELEPORT;
    private static final Class<?> PACKET_ENTITY_METADATA;
    private static final Class<?> PACKET_ENTITY_EQUIPMENT;
    private static final Class<?> ENUM_ITEM_SLOT;
    private static final Class<?> ITEM_STACK;
    private static final Class<?> PACKET_ENTITY_DESTROY;
    private static final Class<?> PAIR;
    private static final Class<?> SHARED_CONSTANTS;
    private static final Class<?> GAME_VERSION;
    private static final Class<?> VECTOR3F;
    private static final Class<?> I_CHAT_BASE_COMPONENT;
    private static final Class<?> PACKET_DATA_SERIALIZER;
    private static final Class<?> DATA_WATCHER_OBJECT;
    private static final Class<?> DATA_WATCHER_SERIALIZER;
    private static final Class<?> DATA_WATCHER_ITEM;
    private static final Class<?> DATA_WATCHER_REGISTRY;
    private static final Class<?> ENTITY_TYPES;
    private static final Class<?> VEC_3D;

    // Methods.
    private static final MethodHandle asNMSCopy;
    private static final MethodHandle of;
    private static final MethodHandle fromStringOrNull;
    private static final MethodHandle writeInt;
    private static final MethodHandle writeByte;
    private static final MethodHandle writeUUID;
    private static final MethodHandle writeDouble;
    private static final MethodHandle writeShort;
    private static final MethodHandle writeBoolean;
    private static final MethodHandle getObject;
    private static final MethodHandle getValue;
    private static final MethodHandle getSerializer;
    private static final MethodHandle getIndex;
    private static final MethodHandle getTypeId;
    private static final MethodHandle serialize;
    private static final MethodHandle byString;

    // Constructors.
    private static final MethodHandle packetSpawnEntityLiving;
    private static final MethodHandle packetEntityHeadRotation;
    private static final MethodHandle packetEntityTeleport;
    private static final MethodHandle packetEntityEquipment;
    private static final MethodHandle packetEntityDestroy;
    private static final MethodHandle vector3f;
    private static final MethodHandle packetDataSerializer;
    private static final MethodHandle packetEntityMetadata;
    private static final MethodHandle dataWatcherItem;

    // Fields.
    private static final Object DWO_ENTITY_DATA;
    private static final Object DWO_ARMOR_STAND_DATA;
    private static final Object DWO_CUSTOM_NAME;
    private static final Object DWO_CUSTOM_NAME_VISIBLE;
    private static final Object DWO_HEAD_POSE;
    private static final Object DWO_BODY_POSE;
    private static final Object DWO_LEFT_ARM_POSE;
    private static final Object DWO_RIGHT_ARM_POSE;
    private static final Object DWO_LEFT_LEG_POSE;
    private static final Object DWO_RIGHT_LEG_POSE;
    private static final Object ZERO;
    private static final Object ARMOR_STAND;
    private static final int ENTITY_TYPE_ID;
    private static final AtomicInteger ENTITY_COUNTER;

    static {
        // Initialize classes.
        CRAFT_CHAT_MESSAGE = (VERSION > 12) ? ReflectionUtils.getCraftClass("util.CraftChatMessage") : null;
        CRAFT_ITEM_STACK = ReflectionUtils.getCraftClass("inventory.CraftItemStack");
        ENTITY = ReflectionUtils.getNMSClass("world.entity", "Entity");
        ENTITY_ARMOR_STAND = ReflectionUtils.getNMSClass("world.entity.decoration", "EntityArmorStand");
        PACKET_SPAWN_ENTITY_LIVING = ReflectionUtils.getNMSClass(
                "network.protocol.game",
                VERSION > 18 ? "PacketPlayOutSpawnEntity" : "PacketPlayOutSpawnEntityLiving"); // SpawnEntityLiving is removed since 1.19.
        PACKET_ENTITY_HEAD_ROTATION = ReflectionUtils.getNMSClass("network.protocol.game", "PacketPlayOutEntityHeadRotation");
        PACKET_ENTITY_TELEPORT = ReflectionUtils.getNMSClass("network.protocol.game", "PacketPlayOutEntityTeleport");
        PACKET_ENTITY_METADATA = ReflectionUtils.getNMSClass("network.protocol.game", "PacketPlayOutEntityMetadata");
        PACKET_ENTITY_EQUIPMENT = ReflectionUtils.getNMSClass("network.protocol.game", "PacketPlayOutEntityEquipment");
        ENUM_ITEM_SLOT = ReflectionUtils.getNMSClass("world.entity", "EnumItemSlot");
        ITEM_STACK = ReflectionUtils.getNMSClass("world.item", "ItemStack");
        PACKET_ENTITY_DESTROY = ReflectionUtils.getNMSClass("network.protocol.game", "PacketPlayOutEntityDestroy");
        PAIR = (VERSION > 15) ? Reflection.getUnversionedClass("com.mojang.datafixers.util.Pair") : null;
        SHARED_CONSTANTS = (VERSION == 17) ? ReflectionUtils.getNMSClass("SharedConstants") : null;
        GAME_VERSION = (VERSION == 17) ? Reflection.getUnversionedClass("com.mojang.bridge.game.GameVersion") : null;
        VECTOR3F = ReflectionUtils.getNMSClass("core", "Vector3f");
        I_CHAT_BASE_COMPONENT = ReflectionUtils.getNMSClass("network.chat", "IChatBaseComponent");
        PACKET_DATA_SERIALIZER = ReflectionUtils.getNMSClass("network", "PacketDataSerializer");
        DATA_WATCHER_OBJECT = ReflectionUtils.getNMSClass("network.syncher", "DataWatcherObject");
        DATA_WATCHER_SERIALIZER = ReflectionUtils.getNMSClass("network.syncher", "DataWatcherSerializer");
        DATA_WATCHER_ITEM = ReflectionUtils.getNMSClass("network.syncher", "DataWatcher$Item");
        DATA_WATCHER_REGISTRY = ReflectionUtils.getNMSClass("network.syncher", "DataWatcherRegistry");
        ENTITY_TYPES = ReflectionUtils.getNMSClass("world.entity", "EntityTypes");
        VEC_3D = ReflectionUtils.getNMSClass("world.phys", "Vec3D");

        // Initialize methods.
        asNMSCopy = Reflection.getMethod(CRAFT_ITEM_STACK, "asNMSCopy", MethodType.methodType(ITEM_STACK, ItemStack.class), true, true);
        of = (PAIR == null) ? null : Reflection.getMethod(PAIR, "of", MethodType.methodType(PAIR, Object.class, Object.class), true, true);
        fromStringOrNull = (CRAFT_CHAT_MESSAGE == null) ? null : Reflection.getMethod(CRAFT_CHAT_MESSAGE, "fromStringOrNull", MethodType.methodType(I_CHAT_BASE_COMPONENT, String.class), true, true);

        if (ReflectionUtils.supports(20, 2)) {
            writeInt = Reflection.getMethod(PACKET_DATA_SERIALIZER, "c", int.class);
        } else {
            writeInt = Reflection.getMethod(PACKET_DATA_SERIALIZER, "d", int.class);
        }
        writeByte = Reflection.getMethod(PACKET_DATA_SERIALIZER, "writeByte", int.class);
        writeUUID = Reflection.getMethod(PACKET_DATA_SERIALIZER, "a", UUID.class);
        writeDouble = Reflection.getMethod(PACKET_DATA_SERIALIZER, "writeDouble", double.class);
        writeShort = Reflection.getMethod(PACKET_DATA_SERIALIZER, "writeShort", int.class);
        writeBoolean = Reflection.getMethod(PACKET_DATA_SERIALIZER, "writeBoolean", boolean.class);
        getObject = Reflection.getMethod(DATA_WATCHER_ITEM, "a");
        getValue = Reflection.getMethod(DATA_WATCHER_ITEM, "b");
        getSerializer = Reflection.getMethod(DATA_WATCHER_OBJECT, "b");
        getIndex = Reflection.getMethod(DATA_WATCHER_OBJECT, "a");
        getTypeId = Reflection.getMethod(DATA_WATCHER_REGISTRY, "b", MethodType.methodType(int.class, DATA_WATCHER_SERIALIZER), true, true);
        serialize = Reflection.getMethod(DATA_WATCHER_SERIALIZER, "a", PACKET_DATA_SERIALIZER, Object.class);
        byString = Reflection.getMethod(ENTITY_TYPES, "a", MethodType.methodType(Optional.class, String.class), true, true);

        // Initialize constructors.
        if (VERSION > 18) {
            packetSpawnEntityLiving = Reflection.getConstructor(
                    PACKET_SPAWN_ENTITY_LIVING,
                    int.class,
                    UUID.class,
                    double.class,
                    double.class,
                    double.class,
                    float.class,
                    float.class,
                    ENTITY_TYPES,
                    int.class,
                    VEC_3D,
                    double.class);
        } else {
            packetSpawnEntityLiving = Reflection.getConstructor(PACKET_SPAWN_ENTITY_LIVING, PACKET_DATA_SERIALIZER);
        }
        packetEntityHeadRotation = Reflection.getConstructor(PACKET_ENTITY_HEAD_ROTATION, PACKET_DATA_SERIALIZER);
        packetEntityTeleport = Reflection.getConstructor(PACKET_ENTITY_TELEPORT, PACKET_DATA_SERIALIZER);
        packetEntityEquipment = (VERSION > 15) ?
                Reflection.getConstructor(PACKET_ENTITY_EQUIPMENT, int.class, List.class) :
                Reflection.getConstructor(PACKET_ENTITY_EQUIPMENT, int.class, ENUM_ITEM_SLOT, ITEM_STACK);
        packetEntityDestroy = Reflection.getConstructor(PACKET_ENTITY_DESTROY, PROTOCOL == 755 ? int.class : int[].class);
        vector3f = Reflection.getConstructor(VECTOR3F, float.class, float.class, float.class);
        packetDataSerializer = Reflection.getConstructor(PACKET_DATA_SERIALIZER, ByteBuf.class);
        packetEntityMetadata = Reflection.getConstructor(PACKET_ENTITY_METADATA, PACKET_DATA_SERIALIZER);
        dataWatcherItem = Reflection.getConstructor(DATA_WATCHER_ITEM, DATA_WATCHER_OBJECT, Object.class);

        try {
            // Get the protocol version, only needed for 1.17.
            if (VERSION == 17) {
                MethodHandle getVersion = Reflection.getMethod(SHARED_CONSTANTS, "getGameVersion", MethodType.methodType(GAME_VERSION), true, true);
                MethodHandle getProtocol = Reflection.getMethod(GAME_VERSION, "getProtocolVersion", MethodType.methodType(int.class));

                Object gameVersion = getVersion.invoke();
                PROTOCOL = (int) getProtocol.invoke(gameVersion);
            }
        } catch (Throwable exception) {
            exception.printStackTrace();
        }

        // Initialize fields.
        if (ReflectionUtils.supports(18)) {
            if (ReflectionUtils.supports(20, 2)) {
                DWO_ENTITY_DATA = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY, "ao"));
                DWO_CUSTOM_NAME = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY, "aU"));
                DWO_CUSTOM_NAME_VISIBLE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY, "aV"));
                DWO_ARMOR_STAND_DATA = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bC"));

                DWO_HEAD_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bD"));
                DWO_BODY_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bE"));
                DWO_LEFT_ARM_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bF"));
                DWO_RIGHT_ARM_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bG"));
                DWO_LEFT_LEG_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bH"));
                DWO_RIGHT_LEG_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bI"));
            } else if (ReflectionUtils.supports(20)) {
                DWO_ENTITY_DATA = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY, "an"));
                DWO_CUSTOM_NAME = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY, "aU"));
                DWO_CUSTOM_NAME_VISIBLE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY, "aV"));
                DWO_ARMOR_STAND_DATA = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bC"));

                DWO_HEAD_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bD"));
                DWO_BODY_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bE"));
                DWO_LEFT_ARM_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bF"));
                DWO_RIGHT_ARM_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bG"));
                DWO_LEFT_LEG_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bH"));
                DWO_RIGHT_LEG_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bI"));
            } else if (ReflectionUtils.supports(19, 4)) {
                DWO_ENTITY_DATA = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY, "an"));
                DWO_CUSTOM_NAME = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY, "aR"));
                DWO_CUSTOM_NAME_VISIBLE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY, "aS"));
                DWO_ARMOR_STAND_DATA = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bB"));

                DWO_HEAD_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bC"));
                DWO_BODY_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bD"));
                DWO_LEFT_ARM_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bE"));
                DWO_RIGHT_ARM_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bF"));
                DWO_LEFT_LEG_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bG"));
                DWO_RIGHT_LEG_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bH"));
            } else if (ReflectionUtils.supports(18, 2)) {
                DWO_ENTITY_DATA = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY, "Z"));
                DWO_CUSTOM_NAME = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY, "aM"));
                DWO_CUSTOM_NAME_VISIBLE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY, "aN"));
                DWO_ARMOR_STAND_DATA = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bG"));

                DWO_HEAD_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bH"));
                DWO_BODY_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bI"));
                DWO_LEFT_ARM_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bJ"));
                DWO_RIGHT_ARM_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bK"));
                DWO_LEFT_LEG_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bL"));
                DWO_RIGHT_LEG_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bM"));
            } else {
                DWO_ENTITY_DATA = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY, "aa"));
                DWO_CUSTOM_NAME = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY, "aL"));
                DWO_CUSTOM_NAME_VISIBLE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY, "aM"));
                DWO_ARMOR_STAND_DATA = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bH"));

                DWO_HEAD_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bI"));
                DWO_BODY_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bJ"));
                DWO_LEFT_ARM_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bK"));
                DWO_RIGHT_ARM_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bL"));
                DWO_LEFT_LEG_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bM"));
                DWO_RIGHT_LEG_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bN"));
            }
        } else {
            DWO_ENTITY_DATA = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY, "Z"));
            DWO_CUSTOM_NAME = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY, "aJ"));
            DWO_CUSTOM_NAME_VISIBLE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY, "aK"));
            DWO_ARMOR_STAND_DATA = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bG"));

            DWO_HEAD_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bH"));
            DWO_BODY_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bI"));
            DWO_LEFT_ARM_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bJ"));
            DWO_RIGHT_ARM_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bK"));
            DWO_LEFT_LEG_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bL"));
            DWO_RIGHT_LEG_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bM"));
        }

        ZERO = Reflection.getFieldValue(Reflection.getFieldGetter(VEC_3D, VERSION > 18 ? "b" : "a"));
        ARMOR_STAND = getArmorStandType();
        ENTITY_TYPE_ID = getEntityTypeId();

        if (ReflectionUtils.supports(19, 4)) {
            ENTITY_COUNTER = (AtomicInteger) Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY, "d"));
        } else if (ReflectionUtils.supports(18, 2)) {
            ENTITY_COUNTER = (AtomicInteger) Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY, "c"));
        } else {
            ENTITY_COUNTER = (AtomicInteger) Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY, "b"));
        }
    }

    public PacketStand(@NotNull Location location, StandSettings settings, boolean showEveryone, int renderDistance) {
        Validate.notNull(location.getWorld(), "World can't be null.");

        setLocation(location);
        this.renderDistance = Math.min(renderDistance * renderDistance, BUKKIT_VIEW_DISTANCE);
        this.entityId = ENTITY_COUNTER.incrementAndGet();
        this.entityUniqueId = UUID.randomUUID();
        this.settings = settings;

        if (showEveryone) spawn();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isInRange(@NotNull Location location) {
        if (!this.location.getWorld().equals(location.getWorld())) return false;
        return this.location.distanceSquared(location) <= renderDistance;
    }

    public void spawn() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            spawn(player);
        }
    }

    public void spawn(@NotNull Player player) {
        if (!isInRange(player.getLocation())) return;
        if (!plugin.isEnabled()) return;

        ignored.remove(player.getUniqueId());

        Object spawnPacket = VERSION > 18 ? after18SpawnPacket() : before18SpawnPacket(yaw, pitch);
        if (spawnPacket == null) return;

        sendPacket(player, spawnPacket);
        updateLocation();
        updateMetadata();
        updateEquipment();
    }

    public void setLocation(@NotNull Location location) {
        this.location = location;
        this.previousYaw = yaw;
        this.yaw = (byte) ((int) (location.getYaw() * 256.0f / 360.0f));
        this.pitch = (byte) ((int) (location.getPitch() * 256.0f / 360.0f));
    }

    private @Nullable Object after18SpawnPacket() {
        double x = clampXZ(location.getX());
        double y = location.getY();
        double z = clampXZ(location.getZ());
        float yaw = location.getYaw() % 360.0f;
        float pitch = clampPitch(location.getPitch()) % 360.0f;
        double yHeadRot = location.getYaw();

        try {
            return packetSpawnEntityLiving.invoke(
                    entityId,
                    entityUniqueId,
                    x,
                    y,
                    z,
                    !Float.isFinite(pitch) ? 0.0f : pitch,
                    !Float.isFinite(yaw) ? 0.0f : yaw,
                    ARMOR_STAND,
                    0,
                    ZERO,
                    yHeadRot);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            return null;
        }
    }

    private static double clampXZ(double value) {
        return value < -3.0E7 ? -3.0e7 : Math.min(value, 3.0e7);
    }

    private static float clampPitch(float value) {
        return value < -90.0f ? -90.0f : Math.min(value, 90.0f);
    }

    private @Nullable Object before18SpawnPacket(byte yaw, byte pitch) {
        try {
            Object packetDataSerializer = PacketStand.packetDataSerializer.invoke(Unpooled.buffer());
            writeInt.invoke(packetDataSerializer, entityId);
            writeUUID.invoke(packetDataSerializer, entityUniqueId);
            writeInt.invoke(packetDataSerializer, ENTITY_TYPE_ID);
            writeDouble.invoke(packetDataSerializer, location.getX());
            writeDouble.invoke(packetDataSerializer, location.getY());
            writeDouble.invoke(packetDataSerializer, location.getZ());
            writeByte.invoke(packetDataSerializer, yaw);
            writeByte.invoke(packetDataSerializer, pitch);
            writeByte.invoke(packetDataSerializer, yaw);
            writeShort.invoke(packetDataSerializer, 0);
            writeShort.invoke(packetDataSerializer, 0);
            writeShort.invoke(packetDataSerializer, 0);
            return packetSpawnEntityLiving.invoke(packetDataSerializer);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            return null;
        }
    }

    public void teleport(Location location) {
        setLocation(location);
        updateLocation();
    }

    private void updateLocation() {
        sendTeleport();
        sendRotation();
    }

    private void sendTeleport() {
        try {
            Object packetDataSerializer = PacketStand.packetDataSerializer.invoke(Unpooled.buffer());
            writeInt.invoke(packetDataSerializer, entityId);
            writeDouble.invoke(packetDataSerializer, location.getX());
            writeDouble.invoke(packetDataSerializer, location.getY());
            writeDouble.invoke(packetDataSerializer, location.getZ());
            writeByte.invoke(packetDataSerializer, yaw);
            writeByte.invoke(packetDataSerializer, pitch);
            writeBoolean.invoke(packetDataSerializer, false);
            sendPacket(packetEntityTeleport.invoke(packetDataSerializer));
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    private void sendRotation() {
        Object headRotation = createEntityHeadRotation();
        if (headRotation != null) sendPacket(headRotation);
    }

    private @Nullable Object createEntityHeadRotation() {
        if (previousYaw == yaw) return null;
        try {
            Object packetDataSerializer = PacketStand.packetDataSerializer.invoke(Unpooled.buffer());
            writeInt.invoke(packetDataSerializer, entityId);
            writeByte.invoke(packetDataSerializer, yaw);
            return packetEntityHeadRotation.invoke(packetDataSerializer);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            return null;
        }
    }

    public void setEquipment(ItemStack item, ItemSlot slot) {
        settings.getEquipment().put(slot, item);

        try {
            Object itemStack = asNMSCopy.invoke(item);
            Object packetEquipment;
            if (VERSION > 15) {
                packetEquipment = packetEntityEquipment.invoke(entityId, List.of(of.invoke(slot.getNmsObject(), itemStack)));
            } else {
                packetEquipment = packetEntityEquipment.invoke(entityId, slot.getNmsObject(), itemStack);
            }
            sendPacket(packetEquipment);
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    public void updateEquipment() {
        if (!settings.hasEquipment()) return;

        for (ItemSlot slot : ItemSlot.values()) {
            ItemStack item = settings.getEquipment().get(slot);
            if (item != null) setEquipment(item, slot);
        }
    }

    public void updateMetadata() {
        try {
            sendPacket(createEntityMetadata(getCorrectDataWatcherItems()));
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    private List<Object> getCorrectDataWatcherItems() {
        return !cacheMetadata ? getDataWatcherItems() : cachedMetadata != null ? cachedMetadata : (cachedMetadata = Collections.unmodifiableList(getDataWatcherItems()));
    }

    private @NotNull List<Object> getDataWatcherItems() {
        try {
            List<Object> dataWatcherItems = new ArrayList<>();

            dataWatcherItems.add(dataWatcherItem.invoke(DWO_ENTITY_DATA, (byte)
                    ((settings.isFire() ? 0x01 : 0)
                            | (settings.isInvisible() ? 0x20 : 0)
                            | (settings.isGlow() ? 0x40 : 0))));

            dataWatcherItems.add(dataWatcherItem.invoke(DWO_ARMOR_STAND_DATA, (byte)
                    ((settings.isSmall() ? 0x01 : 0)
                            | (settings.isArms() ? 0x04 : 0)
                            | (settings.isBasePlate() ? 0 : 0x08)
                            | (settings.isMarker() ? 0x10 : 0))));

            addPoses(dataWatcherItems, DWO_HEAD_POSE, settings.getHeadPose());
            addPoses(dataWatcherItems, DWO_BODY_POSE, settings.getBodyPose());
            addPoses(dataWatcherItems, DWO_LEFT_ARM_POSE, settings.getLeftArmPose());
            addPoses(dataWatcherItems, DWO_RIGHT_ARM_POSE, settings.getRightArmPose());
            addPoses(dataWatcherItems, DWO_LEFT_LEG_POSE, settings.getLeftLegPose());
            addPoses(dataWatcherItems, DWO_RIGHT_LEG_POSE, settings.getRightLegPose());

            String name = settings.getCustomName();
            Optional<Object> optionalName = Optional.ofNullable(name != null && !name.isEmpty() ? fromStringOrNull.invoke(name) : null);
            dataWatcherItems.add(dataWatcherItem.invoke(DWO_CUSTOM_NAME, optionalName));

            if (settings.isCustomNameVisible()) {
                dataWatcherItems.add(dataWatcherItem.invoke(DWO_CUSTOM_NAME_VISIBLE, true));
            }

            return dataWatcherItems;
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            return Collections.emptyList();
        }
    }

    private void addPoses(@NotNull List<Object> dataWatcherItems, Object dwoObject, @NotNull EulerAngle angle) throws Throwable {
        dataWatcherItems.add(dataWatcherItem.invoke(dwoObject, vector3f.invoke(
                (float) Math.toDegrees(angle.getX()),
                (float) Math.toDegrees(angle.getY()),
                (float) Math.toDegrees(angle.getZ()))));
    }

    private Object createEntityMetadata(List<Object> items) throws Throwable {
        Objects.requireNonNull(items);

        Object packetDataSerializer = PacketStand.packetDataSerializer.invoke(Unpooled.buffer());
        writeInt.invoke(packetDataSerializer, entityId);

        for (Object item : items) {
            if (!item.getClass().isAssignableFrom(DATA_WATCHER_ITEM)) continue;

            Object object = getObject.invoke(item);
            Object value = getValue.invoke(item);
            Object serializer = getSerializer.invoke(object);
            int serializerIndex = (int) getIndex.invoke(object);
            int serializerTypeId = (int) getTypeId.invoke(serializer);

            writeByte.invoke(packetDataSerializer, (byte) serializerIndex);
            writeInt.invoke(packetDataSerializer, serializerTypeId);
            serialize.invoke(serializer, packetDataSerializer, value);
        }

        writeByte.invoke(packetDataSerializer, 0xff);
        return packetEntityMetadata.invoke(packetDataSerializer);
    }

    public void destroy() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            destroy(player);
        }
        ignored.clear();
    }

    public void destroy(Player player) {
        destroy(player, IgnoreReason.NONE);
    }

    public void destroy(Player player, IgnoreReason reason) {
        try {
            Object packetDestroy = PROTOCOL == 755 ? packetEntityDestroy.invoke(entityId) : packetEntityDestroy.invoke(new int[]{entityId});
            sendPacket(player, packetDestroy);

            ignored.put(player.getUniqueId(), reason);
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    public boolean isIgnored(@NotNull Player player) {
        return ignored.containsKey(player.getUniqueId());
    }

    private void sendPacket(Player player, Object packet) {
        ReflectionUtils.sendPacketSync(player, packet);
    }

    private void sendPacket(Object packet) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isIgnored(player)) sendPacket(player, packet);
        }
    }

    private static @Nullable Object getArmorStandType() {
        try {
            @SuppressWarnings("deprecation") Optional<?> optional = (Optional<?>) byString.invoke(EntityType.ARMOR_STAND.getName());
            return optional.orElse(null);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            return null;
        }
    }

    private static int getEntityTypeId() {
        Object ENTITY_TYPE;
        if (ReflectionUtils.supports(18)) {
            if (ReflectionUtils.supports(20, 3)) {
                Class<?> REGISTRIES = ReflectionUtils.getNMSClass("core.registries", "BuiltInRegistries");
                ENTITY_TYPE = Reflection.getFieldValue(Reflection.getFieldGetter(REGISTRIES, "g"));
            } else if (ReflectionUtils.supports(19, 3)) {
                Class<?> REGISTRIES = ReflectionUtils.getNMSClass("core.registries", "BuiltInRegistries");
                ENTITY_TYPE = Reflection.getFieldValue(Reflection.getFieldGetter(REGISTRIES, "h"));
            } else if (ReflectionUtils.supports(19)) {
                Class<?> REGISTRY = ReflectionUtils.getNMSClass("core", "IRegistry");
                ENTITY_TYPE = Reflection.getFieldValue(Reflection.getFieldGetter(REGISTRY, "X"));
            } else if (ReflectionUtils.MINOR_NUMBER == 18 && ReflectionUtils.PATCH_NUMBER == 2) {
                Class<?> REGISTRY = ReflectionUtils.getNMSClass("core", "IRegistry");
                ENTITY_TYPE = Reflection.getFieldValue(Reflection.getFieldGetter(REGISTRY, "W"));
            } else {
                Class<?> REGISTRY = ReflectionUtils.getNMSClass("core", "IRegistry");
                ENTITY_TYPE = Reflection.getFieldValue(Reflection.getFieldGetter(REGISTRY, "Z"));
            }
        } else {
            Class<?> REGISTRY = ReflectionUtils.getNMSClass("core", "IRegistry");
            ENTITY_TYPE = Reflection.getFieldValue(Reflection.getFieldGetter(REGISTRY, "Y"));
        }

        MethodHandle getId;
        Class<?> REGISTRY = ReflectionUtils.getNMSClass("core", "Registry");
        if (ReflectionUtils.supports(18)) {
            getId = Reflection.getMethod(REGISTRY, "a", Object.class);
        } else {
            getId = Reflection.getMethod(REGISTRY, "getId", Object.class);
        }

        try {
            return (int) getId.invoke(ENTITY_TYPE, ARMOR_STAND);
        } catch (Throwable throwable) {
            return -1;
        }
    }

    @Getter
    public enum ItemSlot {
        MAINHAND("main-hand", EquipmentSlot.HAND, 1),
        OFFHAND("off-hand", EquipmentSlot.OFF_HAND, 6),
        FEET("boots", EquipmentSlot.FEET, 5),
        LEGS("leggings", EquipmentSlot.LEGS, 4),
        CHEST("chestplate", EquipmentSlot.CHEST, 3),
        HEAD("helmet", EquipmentSlot.HEAD, 2);

        private final String configPathName;
        private final EquipmentSlot bukkitSlot;
        private final int equipmentGUISlot;
        private final Object nmsObject;

        ItemSlot(String configPathName, EquipmentSlot bukkitSlot, int equipmentGUISlot) {
            this.configPathName = configPathName;
            this.bukkitSlot = bukkitSlot;
            this.equipmentGUISlot = equipmentGUISlot;
            this.nmsObject = getNMS(ordinal(), name());
        }

        private static @Nullable Object getNMS(int ordinal, String name) {
            try {
                Field field = ENUM_ITEM_SLOT.getField(VERSION > 16 ? String.valueOf(ALPHABET[ordinal]) : name);
                return field.get(null);
            } catch (Throwable exception) {
                exception.printStackTrace();
                return null;
            }
        }
    }
}