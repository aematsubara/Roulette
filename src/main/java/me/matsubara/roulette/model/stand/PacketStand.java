package me.matsubara.roulette.model.stand;

import com.cryptomorin.xseries.reflection.XReflection;
import com.cryptomorin.xseries.reflection.minecraft.MinecraftConnection;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.Getter;
import lombok.Setter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.util.PluginUtils;
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
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings({"ConstantConditions", "deprecation"})
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
    private static final int VERSION = XReflection.MINOR_NUMBER;

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
    private static final Class<?> STREAM_ENCODER;
    private static final Class<?> REGISTRY_FRIENDLY_BYTE_BUF;
    private static final Class<?> REGISTRY_ACCESS;
    private static final Class<?> IMMUTABLE_REGISTRY_ACCESS;
    private static final Class<?> BUILT_IN_REGISTRIES;
    private static final Class<?> DEFAULTED_REGISTRY;

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
    private static final MethodHandle codec;
    private static final MethodHandle encode;

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
    private static final MethodHandle registryFriendlyByteBuf;
    private static final MethodHandle immutableRegistryAccess;

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
    private static final Object ITEM;
    private static final Object DATA_COMPONENT_TYPE;
    private static final AtomicInteger ENTITY_COUNTER;

    static {
        // Initialize classes.
        CRAFT_CHAT_MESSAGE = (VERSION > 12) ? XReflection.getCraftClass("util.CraftChatMessage") : null;
        CRAFT_ITEM_STACK = XReflection.getCraftClass("inventory.CraftItemStack");
        ENTITY = XReflection.getNMSClass("world.entity", "Entity");
        ENTITY_ARMOR_STAND = Reflection.getNMSClass("world.entity.decoration", "ArmorStand", "EntityArmorStand");
        PACKET_SPAWN_ENTITY_LIVING = Reflection.getNMSClass("network.protocol.game",
                VERSION > 18 ? "ClientboundAddEntityPacket" : "ClientboundAddMobPacket",
                VERSION > 18 ? "PacketPlayOutSpawnEntity" : "PacketPlayOutSpawnEntityLiving"); // SpawnEntityLiving is removed since 1.19.
        PACKET_ENTITY_HEAD_ROTATION = Reflection.getNMSClass("network.protocol.game", "ClientboundRotateHeadPacket", "PacketPlayOutEntityHeadRotation");
        PACKET_ENTITY_TELEPORT = Reflection.getNMSClass("network.protocol.game", "ClientboundTeleportEntityPacket", "PacketPlayOutEntityTeleport");
        PACKET_ENTITY_METADATA = Reflection.getNMSClass("network.protocol.game", "ClientboundSetEntityDataPacket", "PacketPlayOutEntityMetadata");
        PACKET_ENTITY_EQUIPMENT = Reflection.getNMSClass("network.protocol.game", "ClientboundSetEquipmentPacket", "PacketPlayOutEntityEquipment");
        ENUM_ITEM_SLOT = Reflection.getNMSClass("world.entity", "EquipmentSlot", "EnumItemSlot");
        ITEM_STACK = XReflection.getNMSClass("world.item", "ItemStack");
        PACKET_ENTITY_DESTROY = Reflection.getNMSClass("network.protocol.game", "ClientboundRemoveEntitiesPacket", "PacketPlayOutEntityDestroy");
        PAIR = (VERSION > 15) ? Reflection.getUnversionedClass("com.mojang.datafixers.util.Pair") : null;
        SHARED_CONSTANTS = (VERSION == 17) ? XReflection.getNMSClass("SharedConstants") : null;
        GAME_VERSION = (VERSION == 17) ? Reflection.getUnversionedClass("com.mojang.bridge.game.GameVersion") : null;
        VECTOR3F = Reflection.getNMSClass("core", "Rotations", "Vector3f");
        I_CHAT_BASE_COMPONENT = Reflection.getNMSClass("network.chat", "Component", "IChatBaseComponent");
        PACKET_DATA_SERIALIZER = Reflection.getNMSClass("network", "FriendlyByteBuf", "PacketDataSerializer");
        DATA_WATCHER_OBJECT = Reflection.getNMSClass("network.syncher", "EntityDataAccessor", "DataWatcherObject");
        DATA_WATCHER_SERIALIZER = Reflection.getNMSClass("network.syncher", "EntityDataSerializer", "DataWatcherSerializer");
        DATA_WATCHER_ITEM = Reflection.getNMSClass("network.syncher", "SynchedEntityData$DataItem", "DataWatcher$Item");
        DATA_WATCHER_REGISTRY = Reflection.getNMSClass("network.syncher", "EntityDataSerializers", "DataWatcherRegistry");
        ENTITY_TYPES = Reflection.getNMSClass("world.entity", "EntityType", "EntityTypes");
        VEC_3D = Reflection.getNMSClass("world.phys", "Vec3", "Vec3D");
        STREAM_ENCODER = XReflection.supports(20, 6) ? XReflection.getNMSClass("network.codec", "StreamEncoder") : null;
        REGISTRY_FRIENDLY_BYTE_BUF = XReflection.supports(20, 6) ? XReflection.getNMSClass("network", "RegistryFriendlyByteBuf") : null;
        REGISTRY_ACCESS = XReflection.supports(20, 6) ? Reflection.getNMSClass("core", "RegistryAccess", "IRegistryCustom") : null;
        IMMUTABLE_REGISTRY_ACCESS = XReflection.supports(20, 6) ? Reflection.getNMSClass("core", "RegistryAccess$ImmutableRegistryAccess", "IRegistryCustom$c") : null;
        BUILT_IN_REGISTRIES = XReflection.supports(20, 6) ? XReflection.getNMSClass("core.registries", "BuiltInRegistries") : null;
        DEFAULTED_REGISTRY = XReflection.supports(20, 6) ? Reflection.getNMSClass("core", "DefaultedRegistry", "RegistryBlocks") : null;

        // Initialize methods.
        asNMSCopy = Reflection.getMethod(CRAFT_ITEM_STACK, "asNMSCopy", MethodType.methodType(ITEM_STACK, ItemStack.class), true, true);
        of = (PAIR == null) ? null : Reflection.getMethod(PAIR, "of", MethodType.methodType(PAIR, Object.class, Object.class), true, true);
        fromStringOrNull = (CRAFT_CHAT_MESSAGE == null) ? null : Reflection.getMethod(CRAFT_CHAT_MESSAGE, "fromStringOrNull", MethodType.methodType(I_CHAT_BASE_COMPONENT, String.class), true, true);

        if (XReflection.supports(20, 6)) {
            writeInt = Reflection.getMethod(PACKET_DATA_SERIALIZER, "c", MethodType.methodType(PACKET_DATA_SERIALIZER, int.class), "writeVarInt");
        } else if (XReflection.supports(20, 2)) {
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
        serialize = XReflection.supports(20, 6) ? null : Reflection.getMethod(DATA_WATCHER_SERIALIZER, "a", PACKET_DATA_SERIALIZER, Object.class);
        byString = Reflection.getMethod(ENTITY_TYPES, "a", MethodType.methodType(Optional.class, String.class), true, true);
        codec = XReflection.supports(20, 6) ? Reflection.getMethod(DATA_WATCHER_SERIALIZER, "codec") : null;
        encode = XReflection.supports(20, 6) ? Reflection.getMethod(STREAM_ENCODER, "encode", Object.class, Object.class) : null;

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
            packetSpawnEntityLiving = Reflection.getPrivateConstructor(PACKET_SPAWN_ENTITY_LIVING, PACKET_DATA_SERIALIZER);
        }
        packetEntityHeadRotation = Reflection.getPrivateConstructor(PACKET_ENTITY_HEAD_ROTATION, PACKET_DATA_SERIALIZER);
        packetEntityTeleport = Reflection.getPrivateConstructor(PACKET_ENTITY_TELEPORT, PACKET_DATA_SERIALIZER);
        packetEntityEquipment = (VERSION > 15) ?
                Reflection.getConstructor(PACKET_ENTITY_EQUIPMENT, int.class, List.class) :
                Reflection.getConstructor(PACKET_ENTITY_EQUIPMENT, int.class, ENUM_ITEM_SLOT, ITEM_STACK);
        packetEntityDestroy = Reflection.getConstructor(PACKET_ENTITY_DESTROY, PROTOCOL == 755 ? int.class : int[].class);
        vector3f = Reflection.getConstructor(VECTOR3F, float.class, float.class, float.class);
        packetDataSerializer = Reflection.getPrivateConstructor(PACKET_DATA_SERIALIZER, ByteBuf.class);
        packetEntityMetadata = Reflection.getPrivateConstructor(PACKET_ENTITY_METADATA, XReflection.supports(20, 6) ? REGISTRY_FRIENDLY_BYTE_BUF : PACKET_DATA_SERIALIZER);
        dataWatcherItem = Reflection.getConstructor(DATA_WATCHER_ITEM, DATA_WATCHER_OBJECT, Object.class);
        registryFriendlyByteBuf = XReflection.supports(20, 6) ? Reflection.getConstructor(REGISTRY_FRIENDLY_BYTE_BUF, ByteBuf.class, REGISTRY_ACCESS) : null;
        immutableRegistryAccess = XReflection.supports(20, 6) ? Reflection.getConstructor(IMMUTABLE_REGISTRY_ACCESS, List.class) : null;

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
        if (XReflection.supports(18)) {
            if (XReflection.supports(21)) {
                DWO_ENTITY_DATA = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY, "ap"));
                DWO_CUSTOM_NAME = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY, "aQ"));
                DWO_CUSTOM_NAME_VISIBLE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY, "aR"));
                DWO_ARMOR_STAND_DATA = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bH"));

                DWO_HEAD_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bI"));
                DWO_BODY_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bJ"));
                DWO_LEFT_ARM_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bK"));
                DWO_RIGHT_ARM_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bL"));
                DWO_LEFT_LEG_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bM"));
                DWO_RIGHT_LEG_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bN"));
            } else if (XReflection.supports(20, 6)) {
                DWO_ENTITY_DATA = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY, "ap"));
                DWO_CUSTOM_NAME = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY, "aS"));
                DWO_CUSTOM_NAME_VISIBLE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY, "aT"));
                DWO_ARMOR_STAND_DATA = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bG"));

                DWO_HEAD_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bH"));
                DWO_BODY_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bI"));
                DWO_LEFT_ARM_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bJ"));
                DWO_RIGHT_ARM_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bK"));
                DWO_LEFT_LEG_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bL"));
                DWO_RIGHT_LEG_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bM"));
            } else if (XReflection.supports(20, 2)) {
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
            } else if (XReflection.supports(20)) {
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
            } else if (XReflection.supports(19, 4)) {
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
            } else if (XReflection.supports(18, 2)) {
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
        ITEM = XReflection.supports(20, 6) ? Reflection.getFieldValue(Reflection.getField(BUILT_IN_REGISTRIES, DEFAULTED_REGISTRY, "h", true, "ITEM")) : null;

        if (XReflection.supports(21)) {
            DATA_COMPONENT_TYPE = Reflection.getFieldValue(Reflection.getField(BUILT_IN_REGISTRIES, DEFAULTED_REGISTRY, "aq", true, "DATA_COMPONENT_TYPE"));
        } else if (XReflection.supports(20, 6)) {
            DATA_COMPONENT_TYPE = Reflection.getFieldValue(Reflection.getField(BUILT_IN_REGISTRIES, DEFAULTED_REGISTRY, "as", true, "DATA_COMPONENT_TYPE"));
        } else {
            DATA_COMPONENT_TYPE = null;
        }

        if (XReflection.supports(20, 6)) {
            ENTITY_COUNTER = (AtomicInteger) Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY, "c"));
        } else if (XReflection.supports(19, 4)) {
            ENTITY_COUNTER = (AtomicInteger) Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY, "d"));
        } else if (XReflection.supports(18, 2)) {
            ENTITY_COUNTER = (AtomicInteger) Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY, "c"));
        } else {
            ENTITY_COUNTER = (AtomicInteger) Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY, "b"));
        }
    }

    public PacketStand(@NotNull Location location, StandSettings settings, boolean showEveryone) {
        Validate.notNull(location.getWorld(), "World can't be null.");

        setLocation(location);
        this.entityId = ENTITY_COUNTER.incrementAndGet();
        this.entityUniqueId = UUID.randomUUID();
        this.settings = settings;

        if (showEveryone) spawn();
    }

    public void spawn() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            spawn(player);
        }
    }

    public void spawn(@NotNull Player player) {
        spawn(player, false);
    }

    public void spawn(@NotNull Player player, boolean ignoreRangeCheck) {
        if (!ignoreRangeCheck && !PluginUtils.isInRange(location, player.getLocation())) return;
        if (!plugin.isEnabled()) return;

        ignored.remove(player.getUniqueId());

        Object spawnPacket = VERSION > 18 ? after18SpawnPacket() : before18SpawnPacket(yaw, pitch);
        if (spawnPacket == null) return;

        // Send spawn.
        sendPacket(player, spawnPacket);

        // Send teleport.
        Object teleport = createTeleport();
        if (teleport != null) sendPacket(player, teleport);

        // Send rotation.
        Object rotation = createEntityHeadRotation();
        if (rotation != null) sendPacket(player, rotation);

        // Send metadata.
        updateMetadata(player);

        // Send equipment.
        updateEquipment(player);
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
        Object teleport = createTeleport();
        if (teleport != null) sendPacket(teleport);

        Object rotation = createEntityHeadRotation();
        if (rotation != null) sendPacket(rotation);
    }

    private @Nullable Object createTeleport() {
        try {
            Object packetDataSerializer = PacketStand.packetDataSerializer.invoke(Unpooled.buffer());
            writeInt.invoke(packetDataSerializer, entityId);
            writeDouble.invoke(packetDataSerializer, location.getX());
            writeDouble.invoke(packetDataSerializer, location.getY());
            writeDouble.invoke(packetDataSerializer, location.getZ());
            writeByte.invoke(packetDataSerializer, yaw);
            writeByte.invoke(packetDataSerializer, pitch);
            writeBoolean.invoke(packetDataSerializer, false);
            return packetEntityTeleport.invoke(packetDataSerializer);
        } catch (Throwable exception) {
            exception.printStackTrace();
            return null;
        }
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
        setEquipment(null, item, slot);
    }

    public void setEquipment(@Nullable Player player, ItemStack item, ItemSlot slot) {
        settings.getEquipment().put(slot, item);

        try {
            Object itemStack = asNMSCopy.invoke(item);
            Object packetEquipment;
            if (VERSION > 15) {
                packetEquipment = packetEntityEquipment.invoke(entityId, List.of(of.invoke(slot.getNmsObject(), itemStack)));
            } else {
                packetEquipment = packetEntityEquipment.invoke(entityId, slot.getNmsObject(), itemStack);
            }

            if (player != null) sendPacket(player, packetEquipment);
            else sendPacket(packetEquipment);
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    public void updateEquipment(@Nullable Player player) {
        if (!settings.hasEquipment()) return;

        for (ItemSlot slot : ItemSlot.values()) {
            ItemStack item = settings.getEquipment().get(slot);
            if (item != null) setEquipment(player, item, slot);
        }
    }

    public void updateMetadata(Player player) {
        try {
            sendPacket(player, createEntityMetadata(getCorrectDataWatcherItems()));
        } catch (Throwable throwable) {
            throwable.printStackTrace();
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

        Object packetDataSerializer;
        if (XReflection.supports(20, 6)) {
            Object registry = immutableRegistryAccess.invoke(Arrays.asList(ITEM, DATA_COMPONENT_TYPE));
            packetDataSerializer = registryFriendlyByteBuf.invoke(Unpooled.buffer(), registry);
        } else {
            packetDataSerializer = PacketStand.packetDataSerializer.invoke(Unpooled.buffer());
        }

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

            if (XReflection.supports(20, 6)) {
                Object codecObject = codec.invoke(serializer);
                encode.invoke(codecObject, packetDataSerializer, value);
            } else {
                serialize.invoke(serializer, packetDataSerializer, value);
            }
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
        MinecraftConnection.sendPacket(player, packet);
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
        if (XReflection.supports(18)) {
            if (XReflection.supports(21)) {
                Class<?> REGISTRIES = XReflection.getNMSClass("core.registries", "BuiltInRegistries");
                ENTITY_TYPE = Reflection.getFieldValue(Reflection.getFieldGetter(REGISTRIES, "f"));
            } else if (XReflection.supports(20, 3)) {
                Class<?> REGISTRIES = XReflection.getNMSClass("core.registries", "BuiltInRegistries");
                ENTITY_TYPE = Reflection.getFieldValue(Reflection.getFieldGetter(REGISTRIES, "g"));
            } else if (XReflection.supports(19, 3)) {
                Class<?> REGISTRIES = XReflection.getNMSClass("core.registries", "BuiltInRegistries");
                ENTITY_TYPE = Reflection.getFieldValue(Reflection.getFieldGetter(REGISTRIES, "h"));
            } else if (XReflection.supports(19)) {
                Class<?> REGISTRY = XReflection.getNMSClass("core", "IRegistry");
                ENTITY_TYPE = Reflection.getFieldValue(Reflection.getFieldGetter(REGISTRY, "X"));
            } else if (XReflection.MINOR_NUMBER == 18 && XReflection.PATCH_NUMBER == 2) {
                Class<?> REGISTRY = XReflection.getNMSClass("core", "IRegistry");
                ENTITY_TYPE = Reflection.getFieldValue(Reflection.getFieldGetter(REGISTRY, "W"));
            } else {
                Class<?> REGISTRY = XReflection.getNMSClass("core", "IRegistry");
                ENTITY_TYPE = Reflection.getFieldValue(Reflection.getFieldGetter(REGISTRY, "Z"));
            }
        } else {
            Class<?> REGISTRY = XReflection.getNMSClass("core", "IRegistry");
            ENTITY_TYPE = Reflection.getFieldValue(Reflection.getFieldGetter(REGISTRY, "Y"));
        }

        MethodHandle getId;
        Class<?> REGISTRY = XReflection.getNMSClass("core", "Registry");
        if (XReflection.supports(18)) {
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
                return Reflection.getField(ENUM_ITEM_SLOT, ENUM_ITEM_SLOT, String.valueOf(ALPHABET[ordinal]), true, name).invoke();
            } catch (Throwable exception) {
                exception.printStackTrace();
                return null;
            }
        }
    }
}