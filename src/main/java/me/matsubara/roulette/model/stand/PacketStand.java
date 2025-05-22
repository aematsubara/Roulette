package me.matsubara.roulette.model.stand;

import com.cryptomorin.xseries.reflection.XReflection;
import com.google.common.base.Strings;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.Getter;
import lombok.Setter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.model.stand.data.ItemSlot;
import me.matsubara.roulette.model.stand.data.Pose;
import me.matsubara.roulette.util.Reflection;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.*;

@Getter
public final class PacketStand {

    private final RoulettePlugin plugin;
    private final int id;
    private final UUID uniqueId;
    private final StandSettings settings;
    private final boolean isStand;
    private @Setter Location location;
    private byte yaw, pitch;
    private World world;
    private boolean destroyed;

    private Object spawn;
    private Object metadata;
    private Object teleport;
    private Object rotation;
    private Object destroyEntities;
    private List<Object> equipments;

    private static int PROTOCOL = -1;
    private static final int MINOR_NUMBER = XReflection.MINOR_NUMBER;
    public static final char[] ALPHABET = "abcdefghijklmnopqrstuvwxyz".toCharArray();

    // Classes.
    private static final Class<?> CRAFT_CHAT_MESSAGE;
    private static final Class<?> CRAFT_ITEM_STACK;
    private static final Class<?> ENTITY;
    private static final Class<?> ENTITY_ARMOR_STAND;
    private static final Class<?> ENTITY_DISPLAY;
    private static final Class<?> ENTITY_TEXT_DISPLAY;
    private static final Class<?> PACKET_SPAWN_ENTITY_LIVING;
    private static final Class<?> PACKET_ENTITY_TELEPORT;
    private static final Class<?> PACKET_ENTITY_METADATA;
    private static final Class<?> PACKET_ENTITY_EQUIPMENT;
    public static final Class<?> ENUM_ITEM_SLOT;
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
    private static final Class<?> POSITION_MOVE_ROTATION;
    private static final Class<?> ENTITY_PLAYER;
    private static final Class<?> CRAFT_PLAYER;
    private static final Class<?> SERVER_PLAYER_CONNECTION;
    private static final Class<?> GAME_PACKET_LISTENER;
    private static final Class<?> PACKET;

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
    private static final MethodHandle getHandle;
    private static final MethodHandle sendPacket;

    // Constructors.
    private static final MethodHandle packetSpawnEntityLiving;
    private static final MethodHandle packetEntityTeleport;
    private static final MethodHandle packetEntityEquipment;
    private static final MethodHandle packetEntityDestroy;
    private static final MethodHandle vector3f;
    private static final MethodHandle packetDataSerializer;
    private static final MethodHandle packetEntityMetadata;
    private static final MethodHandle dataWatcherItem;
    private static final MethodHandle registryFriendlyByteBuf;
    private static final MethodHandle immutableRegistryAccess;
    private static final MethodHandle positionMoveRotation;
    private static final MethodHandle vec3d;

    // Fields.
    private static final MethodHandle CONNECTION;
    private static final Object DWO_ENTITY_DATA;
    private static final Object DWO_ARMOR_STAND_DATA;
    private static final Object DWO_CUSTOM_NAME;
    private static final Object DWO_CUSTOM_NAME_VISIBLE;
    private static final Object DWO_SCALE_ID;
    private static final Object DWO_TEXT_ID;
    private static final Object DWO_BACKGROUND_COLOR_ID;

    public static final Object DWO_HEAD_POSE;
    public static final Object DWO_BODY_POSE;
    public static final Object DWO_LEFT_ARM_POSE;
    public static final Object DWO_RIGHT_ARM_POSE;
    public static final Object DWO_LEFT_LEG_POSE;
    public static final Object DWO_RIGHT_LEG_POSE;

    private static final Object ZERO;
    private static final Object ARMOR_STAND;
    private static final int ARMOR_STAND_TYPE_ID;
    private static final Object TEXT_DISPLAY;
    private static final int TEXT_DISPLAY_TYPE_ID;
    private static final Object ITEM;
    private static final Object DATA_COMPONENT_TYPE;

    static {
        // Initialize classes.
        CRAFT_CHAT_MESSAGE = (MINOR_NUMBER > 12) ? Reflection.getCraftClass("util", "CraftChatMessage") : null;
        CRAFT_ITEM_STACK = Reflection.getCraftClass("inventory", "CraftItemStack");
        ENTITY = Reflection.getNMSClass("world.entity", "Entity");
        ENTITY_ARMOR_STAND = Reflection.getNMSClass("world.entity.decoration", "ArmorStand", "EntityArmorStand");
        boolean display = XReflection.supports(19, 4);
        ENTITY_DISPLAY = display ? Reflection.getNMSClass("world.entity", "Display") : null;
        ENTITY_TEXT_DISPLAY = display ? Reflection.getNMSClass("world.entity", "Display$TextDisplay") : null;
        PACKET_SPAWN_ENTITY_LIVING = Reflection.getNMSClass("network.protocol.game",
                MINOR_NUMBER > 18 ? "ClientboundAddEntityPacket" : "ClientboundAddMobPacket",
                MINOR_NUMBER > 18 ? "PacketPlayOutSpawnEntity" : "PacketPlayOutSpawnEntityLiving"); // SpawnEntityLiving is removed since 1.19.
        PACKET_ENTITY_TELEPORT = Reflection.getNMSClass("network.protocol.game", "ClientboundTeleportEntityPacket", "PacketPlayOutEntityTeleport");
        PACKET_ENTITY_METADATA = Reflection.getNMSClass("network.protocol.game", "ClientboundSetEntityDataPacket", "PacketPlayOutEntityMetadata");
        PACKET_ENTITY_EQUIPMENT = Reflection.getNMSClass("network.protocol.game", "ClientboundSetEquipmentPacket", "PacketPlayOutEntityEquipment");
        ENUM_ITEM_SLOT = Reflection.getNMSClass("world.entity", "EquipmentSlot", "EnumItemSlot");
        ITEM_STACK = Reflection.getNMSClass("world.item", "ItemStack");
        PACKET_ENTITY_DESTROY = Reflection.getNMSClass("network.protocol.game", "ClientboundRemoveEntitiesPacket", "PacketPlayOutEntityDestroy");
        PAIR = (MINOR_NUMBER > 15) ? Reflection.getUnversionedClass("com.mojang.datafixers.util.Pair") : null;
        SHARED_CONSTANTS = (MINOR_NUMBER == 17) ? Reflection.getNMSClass(null, "SharedConstants") : null;
        GAME_VERSION = (MINOR_NUMBER == 17) ? Reflection.getUnversionedClass("com.mojang.bridge.game.GameVersion") : null;
        VECTOR3F = Reflection.getNMSClass("core", "Rotations", "Vector3f");
        I_CHAT_BASE_COMPONENT = Reflection.getNMSClass("network.chat", "Component", "IChatBaseComponent");
        PACKET_DATA_SERIALIZER = Reflection.getNMSClass("network", "FriendlyByteBuf", "PacketDataSerializer");
        DATA_WATCHER_OBJECT = Reflection.getNMSClass("network.syncher", "EntityDataAccessor", "DataWatcherObject");
        DATA_WATCHER_SERIALIZER = Reflection.getNMSClass("network.syncher", "EntityDataSerializer", "DataWatcherSerializer");
        DATA_WATCHER_ITEM = Reflection.getNMSClass("network.syncher", "SynchedEntityData$DataItem", "DataWatcher$Item");
        DATA_WATCHER_REGISTRY = Reflection.getNMSClass("network.syncher", "EntityDataSerializers", "DataWatcherRegistry");
        ENTITY_TYPES = Reflection.getNMSClass("world.entity", "EntityType", "EntityTypes");
        VEC_3D = Reflection.getNMSClass("world.phys", "Vec3", "Vec3D");
        STREAM_ENCODER = XReflection.supports(20, 6) ? Reflection.getNMSClass("network.codec", "StreamEncoder") : null;
        REGISTRY_FRIENDLY_BYTE_BUF = XReflection.supports(20, 6) ? Reflection.getNMSClass("network", "RegistryFriendlyByteBuf") : null;
        REGISTRY_ACCESS = XReflection.supports(20, 6) ? Reflection.getNMSClass("core", "RegistryAccess", "IRegistryCustom") : null;
        IMMUTABLE_REGISTRY_ACCESS = XReflection.supports(20, 6) ? Reflection.getNMSClass("core", "RegistryAccess$ImmutableRegistryAccess", "IRegistryCustom$c") : null;
        BUILT_IN_REGISTRIES = XReflection.supports(20, 6) ? Reflection.getNMSClass("core.registries", "BuiltInRegistries") : null;
        DEFAULTED_REGISTRY = XReflection.supports(20, 6) ? Reflection.getNMSClass("core", "DefaultedRegistry", "RegistryBlocks") : null;
        POSITION_MOVE_ROTATION = XReflection.supports(21, 2) ? Reflection.getNMSClass("world.entity", "PositionMoveRotation", "PositionMoveRotation") : null;
        ENTITY_PLAYER = Reflection.getNMSClass("server.level", "ServerPlayer", "EntityPlayer");
        CRAFT_PLAYER = Reflection.getCraftClass("entity", "CraftPlayer");
        SERVER_PLAYER_CONNECTION = Reflection.getNMSClass("server.network", "ServerPlayerConnection", "PlayerConnection");
        GAME_PACKET_LISTENER = Reflection.getNMSClass("server.network", "ServerGamePacketListenerImpl", "PlayerConnection");
        PACKET = Reflection.getNMSClass("network.protocol", "Packet");

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
        encode = STREAM_ENCODER != null ? Reflection.getMethod(STREAM_ENCODER, "encode", Object.class, Object.class) : null;
        getHandle = Reflection.getMethod(CRAFT_PLAYER, "getHandle");
        sendPacket = Reflection.getMethod(SERVER_PLAYER_CONNECTION, "send", MethodType.methodType(void.class, PACKET), "b", "a", "sendPacket");

        try {
            // Get the protocol version, only needed for 1.17.
            if (MINOR_NUMBER == 17) {
                MethodHandle getVersion = Reflection.getMethod(SHARED_CONSTANTS, "getGameVersion", MethodType.methodType(GAME_VERSION), true, true);
                MethodHandle getProtocol = Reflection.getMethod(GAME_VERSION, "getProtocolVersion", MethodType.methodType(int.class));

                if (getVersion != null) {
                    Object gameVersion = getVersion.invoke();
                    PROTOCOL = (int) getProtocol.invoke(gameVersion);
                }
            }
        } catch (Throwable exception) {
            exception.printStackTrace();
        }

        // Initialize constructors.
        if (MINOR_NUMBER > 18) {
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
        packetEntityTeleport = XReflection.supports(21, 2) ?
                Reflection.getConstructor(PACKET_ENTITY_TELEPORT, int.class, POSITION_MOVE_ROTATION, Set.class, boolean.class)
                : Reflection.getPrivateConstructor(PACKET_ENTITY_TELEPORT, PACKET_DATA_SERIALIZER);
        packetEntityEquipment = (MINOR_NUMBER > 15) ?
                Reflection.getConstructor(PACKET_ENTITY_EQUIPMENT, int.class, List.class) :
                Reflection.getConstructor(PACKET_ENTITY_EQUIPMENT, int.class, ENUM_ITEM_SLOT, ITEM_STACK);
        packetEntityDestroy = Reflection.getConstructor(PACKET_ENTITY_DESTROY, PROTOCOL == 755 ? int.class : int[].class);
        vector3f = Reflection.getConstructor(VECTOR3F, float.class, float.class, float.class);
        packetDataSerializer = Reflection.getPrivateConstructor(PACKET_DATA_SERIALIZER, ByteBuf.class);
        packetEntityMetadata = Reflection.getPrivateConstructor(PACKET_ENTITY_METADATA, XReflection.supports(20, 6) ? REGISTRY_FRIENDLY_BYTE_BUF : PACKET_DATA_SERIALIZER);
        dataWatcherItem = Reflection.getConstructor(DATA_WATCHER_ITEM, DATA_WATCHER_OBJECT, Object.class);
        registryFriendlyByteBuf = REGISTRY_FRIENDLY_BYTE_BUF != null ? Reflection.getConstructor(REGISTRY_FRIENDLY_BYTE_BUF, ByteBuf.class, REGISTRY_ACCESS) : null;
        immutableRegistryAccess = IMMUTABLE_REGISTRY_ACCESS != null ? Reflection.getConstructor(IMMUTABLE_REGISTRY_ACCESS, List.class) : null;
        positionMoveRotation = POSITION_MOVE_ROTATION != null ? Reflection.getConstructor(POSITION_MOVE_ROTATION, VEC_3D, VEC_3D, float.class, float.class) : null;
        vec3d = Reflection.getConstructor(VEC_3D, double.class, double.class, double.class);

        // Initialize fields.
        CONNECTION = Reflection.getField(ENTITY_PLAYER, GAME_PACKET_LISTENER, "connection", true, "f", "c", "b", "playerConnection");
        if (XReflection.supports(18)) {
            // DATA_SHARED_FLAGS_ID, DATA_CUSTOM_NAME, DATA_CUSTOM_NAME_VISIBLE | DATA_CLIENT_FLAGS, DATA_X_POSE
            if (XReflection.supports(21, 5)) {
                DWO_ENTITY_DATA = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY, "am"));
                DWO_CUSTOM_NAME = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY, "aR"));
                DWO_CUSTOM_NAME_VISIBLE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY, "aS"));
                DWO_ARMOR_STAND_DATA = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bw"));
                DWO_HEAD_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bx"));
                DWO_BODY_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "by"));
                DWO_LEFT_ARM_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bz"));
                DWO_RIGHT_ARM_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bA"));
                DWO_LEFT_LEG_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bB"));
                DWO_RIGHT_LEG_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bC"));
                DWO_SCALE_ID = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_DISPLAY, "t"));
                DWO_TEXT_ID = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_TEXT_DISPLAY, "aH"));
                DWO_BACKGROUND_COLOR_ID = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_TEXT_DISPLAY, "aJ"));
            } else if (XReflection.supports(21, 4)) {
                DWO_ENTITY_DATA = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY, "am"));
                DWO_CUSTOM_NAME = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY, "aO"));
                DWO_CUSTOM_NAME_VISIBLE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY, "aP"));
                DWO_ARMOR_STAND_DATA = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bI"));
                DWO_HEAD_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bJ"));
                DWO_BODY_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bK"));
                DWO_LEFT_ARM_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bL"));
                DWO_RIGHT_ARM_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bM"));
                DWO_LEFT_LEG_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bN"));
                DWO_RIGHT_LEG_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bO"));
                DWO_SCALE_ID = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_DISPLAY, "t"));
                DWO_TEXT_ID = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_TEXT_DISPLAY, "aG"));
                DWO_BACKGROUND_COLOR_ID = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_TEXT_DISPLAY, "aI"));
            } else if (XReflection.supports(21, 2)) {
                DWO_ENTITY_DATA = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY, "am"));
                DWO_CUSTOM_NAME = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY, "aO"));
                DWO_CUSTOM_NAME_VISIBLE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY, "aP"));
                DWO_ARMOR_STAND_DATA = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bJ"));
                DWO_HEAD_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bK"));
                DWO_BODY_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bL"));
                DWO_LEFT_ARM_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bM"));
                DWO_RIGHT_ARM_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bN"));
                DWO_LEFT_LEG_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bO"));
                DWO_RIGHT_LEG_POSE = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_ARMOR_STAND, "bP"));
                DWO_SCALE_ID = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_DISPLAY, "t"));
                DWO_TEXT_ID = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_TEXT_DISPLAY, "aG"));
                DWO_BACKGROUND_COLOR_ID = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_TEXT_DISPLAY, "aI"));
            } else if (XReflection.supports(21)) {
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
                DWO_SCALE_ID = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_DISPLAY, "u"));
                DWO_TEXT_ID = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_TEXT_DISPLAY, "aL"));
                DWO_BACKGROUND_COLOR_ID = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_TEXT_DISPLAY, "aN"));
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
                DWO_SCALE_ID = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_DISPLAY, "u"));
                DWO_TEXT_ID = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_TEXT_DISPLAY, "aN"));
                DWO_BACKGROUND_COLOR_ID = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_TEXT_DISPLAY, "aP"));
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
                DWO_SCALE_ID = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_DISPLAY, "u"));
                DWO_TEXT_ID = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_TEXT_DISPLAY, "aM"));
                DWO_BACKGROUND_COLOR_ID = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_TEXT_DISPLAY, "aO"));
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
                DWO_SCALE_ID = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_DISPLAY, "s"));
                DWO_TEXT_ID = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_TEXT_DISPLAY, "aL"));
                DWO_BACKGROUND_COLOR_ID = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_TEXT_DISPLAY, "aN"));
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
                DWO_SCALE_ID = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_DISPLAY, "t"));
                DWO_TEXT_ID = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_TEXT_DISPLAY, "aK"));
                DWO_BACKGROUND_COLOR_ID = Reflection.getFieldValue(Reflection.getFieldGetter(ENTITY_TEXT_DISPLAY, "aM"));
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
                DWO_SCALE_ID = null;
                DWO_TEXT_ID = null;
                DWO_BACKGROUND_COLOR_ID = null;
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
                DWO_SCALE_ID = null;
                DWO_TEXT_ID = null;
                DWO_BACKGROUND_COLOR_ID = null;
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
            DWO_SCALE_ID = null;
            DWO_TEXT_ID = null;
            DWO_BACKGROUND_COLOR_ID = null;
        }

        ZERO = Reflection.getFieldValue(Reflection.getField(VEC_3D, VEC_3D, "a", true, "b", "c", "ZERO"));
        ARMOR_STAND = getType(EntityType.ARMOR_STAND);
        ARMOR_STAND_TYPE_ID = getTypeId(ARMOR_STAND);
        TEXT_DISPLAY = display ? getType(EntityType.TEXT_DISPLAY) : null;
        TEXT_DISPLAY_TYPE_ID = TEXT_DISPLAY != null ? getTypeId(TEXT_DISPLAY) : -1;
        ITEM = XReflection.supports(20, 6) ? Reflection.getFieldValue(Reflection.getField(BUILT_IN_REGISTRIES, DEFAULTED_REGISTRY, "h", true, "ITEM")) : null;

        if (XReflection.supports(20, 6)) {
            DATA_COMPONENT_TYPE = Reflection.getFieldValue(Reflection.getField(
                    BUILT_IN_REGISTRIES,
                    DEFAULTED_REGISTRY,
                    XReflection.supports(21) ? "aq" : "as",
                    true,
                    "DATA_COMPONENT_TYPE"));
        } else {
            DATA_COMPONENT_TYPE = null;
        }
    }

    public PacketStand(RoulettePlugin plugin, @NotNull Location location, StandSettings settings) {
        this(plugin, location, settings, true);
    }

    public PacketStand(RoulettePlugin plugin, @NotNull Location location, StandSettings settings, boolean isStand) {
        this.plugin = plugin;
        this.id = SpigotReflectionUtil.generateEntityId();
        this.uniqueId = UUID.randomUUID();
        this.settings = settings;
        this.isStand = isStand;
        invalidTeleport(location);
    }

    public void spawn(@NotNull Player player) {
        if (destroyed) return;

        // There's no need to send a teleport packet when spawning.
        Object connection = getPlayerConnection(player);
        sendPacket(connection, createSpawnPacket(), createMetadata());
        sendPacket(connection, createEquipment().toArray());
    }

    private Object createSpawnPacket() {
        return spawn != null ? spawn : (spawn = MINOR_NUMBER > 18 ? after18SpawnPacket() : before18SpawnPacket());
    }

    private @Nullable Object after18SpawnPacket() {
        float yaw = location.getYaw() % 360.0f;
        float pitch = clampPitch(location.getPitch()) % 360.0f;

        try {
            return packetSpawnEntityLiving.invoke(
                    id,
                    uniqueId,
                    clampXZ(location.getX()),
                    location.getY(),
                    clampXZ(location.getZ()),
                    !Float.isFinite(pitch) ? 0.0f : pitch,
                    !Float.isFinite(yaw) ? 0.0f : yaw,
                    isStand ? ARMOR_STAND : TEXT_DISPLAY,
                    0,
                    ZERO,
                    location.getYaw());
        } catch (Throwable throwable) {
            throw new RuntimeException("Failed to create a packet!", throwable);
        }
    }

    private static double clampXZ(double value) {
        return value < -3.0e7 ? -3.0e7 : Math.min(value, 3.0e7);
    }

    private static float clampPitch(float value) {
        return value < -90.0f ? -90.0f : Math.min(value, 90.0f);
    }

    private @Nullable Object before18SpawnPacket() {
        try {
            Object packetSerializer = packetDataSerializer.invoke(Unpooled.buffer());
            writeInt.invoke(packetSerializer, id);
            writeUUID.invoke(packetSerializer, uniqueId);
            writeInt.invoke(packetSerializer, isStand ? ARMOR_STAND_TYPE_ID : TEXT_DISPLAY_TYPE_ID); // Text display won't work here.
            writeDouble.invoke(packetSerializer, location.getX());
            writeDouble.invoke(packetSerializer, location.getY());
            writeDouble.invoke(packetSerializer, location.getZ());
            writeByte.invoke(packetSerializer, yaw);
            writeByte.invoke(packetSerializer, pitch);
            writeByte.invoke(packetSerializer, yaw);
            writeShort.invoke(packetSerializer, 0);
            writeShort.invoke(packetSerializer, 0);
            writeShort.invoke(packetSerializer, 0);
            return packetSpawnEntityLiving.invoke(packetSerializer);
        } catch (Throwable throwable) {
            throw new RuntimeException("Failed to create a packet!", throwable);
        }
    }

    private Object createTeleport() {
        if (teleport != null) return teleport;

        try {
            if (XReflection.supports(21, 2)) {
                Object position = vec3d.invoke(location.getX(), location.getY(), location.getZ());
                Object movement = positionMoveRotation.invoke(position, ZERO, location.getYaw(), location.getPitch());
                return (teleport = packetEntityTeleport.invoke(id, movement, Collections.emptySet(), false));
            }

            Object packetSerializer = packetDataSerializer.invoke(Unpooled.buffer());
            writeInt.invoke(packetSerializer, id);
            writeDouble.invoke(packetSerializer, location.getX());
            writeDouble.invoke(packetSerializer, location.getY());
            writeDouble.invoke(packetSerializer, location.getZ());
            writeByte.invoke(packetSerializer, yaw);
            writeByte.invoke(packetSerializer, pitch);
            writeBoolean.invoke(packetSerializer, false);
            return (teleport = packetEntityTeleport.invoke(packetSerializer));
        } catch (Throwable throwable) {
            throw new RuntimeException("Failed to create a packet!", throwable);
        }
    }

    private void sendLocation(Player player) {
        // No need to send a head rotation packet (RotateHeadPacket/EntityHeadRotation).
        sendPacket(player, createTeleport());
    }

    public void teleport(Collection<Player> players, Location location) {
        if (invalidTeleport(location)) return;
        if (players.isEmpty()) return;

        // No need to send a head rotation packet (RotateHeadPacket/EntityHeadRotation).
        sendPacket(players, createTeleport());
    }

    public void teleport(Player player, Location location) {
        if (invalidTeleport(location)) return;

        sendLocation(player);
    }

    private boolean invalidTeleport(@NotNull Location location) {
        World world = location.getWorld();
        if (this.world != null && (world == null || !Objects.equals(world, this.world))) return true;

        this.location = location;
        this.yaw = (byte) (location.getYaw() * 256.0f / 360.0f);
        this.pitch = (byte) (location.getPitch() * 256.0f / 360.0f);
        this.world = world;

        spawn = null;
        teleport = null;
        rotation = null;

        return false;
    }

    private boolean invalid() {
        // Prevent errors when trying to send packets when the plugin is being disabled.
        return destroyed || !plugin.isEnabled();
    }

    private @NotNull List<Object> createEquipment() {
        if (!isStand) return Collections.emptyList();
        if (equipments != null) return equipments;

        List<Object> packets = new ArrayList<>();

        for (ItemSlot slot : ItemSlot.values()) {
            try {
                ItemStack temp = settings.getEquipment().get(slot);
                Object item = asNMSCopy.invoke(temp != null ? temp : RoulettePlugin.EMPTY_ITEM);

                Object packetEquipment;
                if (MINOR_NUMBER > 15) {
                    packetEquipment = packetEntityEquipment.invoke(id, List.of(of.invoke(slot.getNmsObject(), item)));
                } else {
                    packetEquipment = packetEntityEquipment.invoke(id, slot.getNmsObject(), item);
                }
                packets.add(packetEquipment);
            } catch (Throwable throwable) {
                throw new RuntimeException("Failed to create a packet!", throwable);
            }
        }

        return (equipments = packets);
    }

    public void sendEquipment(@NotNull Collection<Player> players) {
        equipments = null;
        if (players.isEmpty()) return;

        // Create the packets once and send them to the players.
        sendPacket(players, createEquipment().toArray());
    }

    public @Nullable Object createMetadata() {
        if (metadata != null) return metadata;

        try {
            Object packetSerializer;
            if (XReflection.supports(20, 6)) {
                Object registry = immutableRegistryAccess.invoke(Arrays.asList(ITEM, DATA_COMPONENT_TYPE));
                packetSerializer = registryFriendlyByteBuf.invoke(Unpooled.buffer(), registry);
            } else {
                packetSerializer = packetDataSerializer.invoke(Unpooled.buffer());
            }

            writeInt.invoke(packetSerializer, id);

            for (Object item : getDataWatcherItems()) {
                if (!item.getClass().isAssignableFrom(DATA_WATCHER_ITEM)) continue;

                Object object = getObject.invoke(item);
                Object value = getValue.invoke(item);
                Object serializer = getSerializer.invoke(object);
                int serializerIndex = (int) getIndex.invoke(object);
                int serializerTypeId = (int) getTypeId.invoke(serializer);

                writeByte.invoke(packetSerializer, (byte) serializerIndex);
                writeInt.invoke(packetSerializer, serializerTypeId);

                if (XReflection.supports(20, 6)) {
                    Object codecObject = codec.invoke(serializer);
                    encode.invoke(codecObject, packetSerializer, value);
                } else {
                    serialize.invoke(serializer, packetSerializer, value);
                }
            }

            writeByte.invoke(packetSerializer, 0xff);
            return (metadata = packetEntityMetadata.invoke(packetSerializer));
        } catch (Throwable throwable) {
            throw new RuntimeException("Failed to create a packet!", throwable);
        }
    }

    private @NotNull List<Object> getDataWatcherItems() {
        try {
            List<Object> dataWatcherItems = new ArrayList<>();
            String name = settings.getCustomName();

            if (isStand) {
                dataWatcherItems.add(dataWatcherItem.invoke(DWO_ENTITY_DATA, (byte)
                        ((settings.isFire() ? 0x01 : 0)
                                | (settings.isInvisible() ? 0x20 : 0)
                                | (settings.isGlow() ? 0x40 : 0))));

                dataWatcherItems.add(dataWatcherItem.invoke(DWO_ARMOR_STAND_DATA, (byte)
                        ((settings.isSmall() ? 0x01 : 0)
                                | (settings.isArms() ? 0x04 : 0)
                                | (settings.isBasePlate() ? 0 : 0x08)
                                | (settings.isMarker() ? 0x10 : 0))));

                for (Pose pose : Pose.values()) {
                    addPoses(dataWatcherItems, pose.getDwo(), pose.get(settings));
                }

                Optional<Object> optionalName = Optional.ofNullable(name != null && !name.isEmpty() ? fromStringOrNull.invoke(name) : null);
                dataWatcherItems.add(dataWatcherItem.invoke(DWO_CUSTOM_NAME, optionalName));
                dataWatcherItems.add(dataWatcherItem.invoke(DWO_CUSTOM_NAME_VISIBLE, settings.isCustomNameVisible()));
            } else {
                Vector scale = settings.getScale();
                dataWatcherItems.add(dataWatcherItem.invoke(DWO_SCALE_ID, new Vector3f(
                        (float) scale.getX(),
                        (float) scale.getY(),
                        (float) scale.getZ())));
                dataWatcherItems.add(dataWatcherItem.invoke(DWO_TEXT_ID, fromStringOrNull.invoke(Strings.nullToEmpty(name))));
                dataWatcherItems.add(dataWatcherItem.invoke(DWO_BACKGROUND_COLOR_ID, settings.getBackgroundColor()));
            }

            return dataWatcherItems;
        } catch (Throwable throwable) {
            throw new RuntimeException("Failed to create a packet!", throwable);
        }
    }

    private void addPoses(@NotNull List<Object> dataWatcherItems, Object dwoObject, @NotNull EulerAngle angle) throws Throwable {
        dataWatcherItems.add(dataWatcherItem.invoke(dwoObject, vector3f.invoke(
                (float) Math.toDegrees(angle.getX()),
                (float) Math.toDegrees(angle.getY()),
                (float) Math.toDegrees(angle.getZ()))));
    }

    public void sendMetadata(@NotNull Collection<Player> players) {
        metadata = null;
        if (players.isEmpty()) return;

        // Create the packet once and send it to the players.
        sendPacket(players, createMetadata());
    }

    public void sendMetadata(Player player) {
        sendPacket(player, createMetadata());
    }

    private @Nullable Object createDestroyEntitiesPacket() {
        if (destroyEntities != null) return destroyEntities;

        try {
            return (destroyEntities = PROTOCOL == 755 ? packetEntityDestroy.invoke(id) : packetEntityDestroy.invoke(new int[]{id}));
        } catch (Throwable throwable) {
            throw new RuntimeException("Failed to create a packet!", throwable);
        }
    }

    // This method should only be called when removing the vehicle.
    public void destroy() {
        sendPacket(world.getPlayers(), createDestroyEntitiesPacket());
        destroyed = true;
    }

    public void destroy(Player player) {
        sendPacket(player, createDestroyEntitiesPacket());
    }

    private void sendPacket(@NotNull Collection<Player> players, Object... packets) {
        for (Player player : players) {
            sendPacket(player, packets);
        }
    }

    public void sendPacket(Player player, Object... packets) {
        if (packets == null || packets.length == 0) return;

        sendPacket(getPlayerConnection(player), packets);
    }

    public void sendPacket(Object connection, Object... packets) {
        if (packets == null || packets.length == 0 || invalid()) return;

        try {
            for (Object packet : packets) {
                if (packet == null) continue;
                sendPacket.invoke(connection, packet);
            }
        } catch (Throwable throwable) {
            throw new RuntimeException("Failed to send a packet!", throwable);
        }
    }

    private @Nullable Object getPlayerConnection(Player player) {
        try {
            Object handle = getHandle.invoke(player);
            return CONNECTION.invoke(handle);
        } catch (Throwable throwable) {
            return null;
        }
    }

    private static @Nullable Object getType(EntityType type) {
        try {
            @SuppressWarnings("deprecation") Optional<?> optional = (Optional<?>) byString.invoke(type.getName());
            return optional.orElse(null);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            return null;
        }
    }

    private static int getTypeId(Object type) {
        Object ENTITY_TYPE;
        if (XReflection.supports(18)) {
            if (XReflection.supports(21)) {
                Class<?> REGISTRIES = Reflection.getNMSClass("core.registries", "BuiltInRegistries");
                ENTITY_TYPE = Reflection.getFieldValue(Reflection.getFieldGetter(REGISTRIES, "f"));
            } else if (XReflection.supports(20, 3)) {
                Class<?> REGISTRIES = Reflection.getNMSClass("core.registries", "BuiltInRegistries");
                ENTITY_TYPE = Reflection.getFieldValue(Reflection.getFieldGetter(REGISTRIES, "g"));
            } else if (XReflection.supports(19, 3)) {
                Class<?> REGISTRIES = Reflection.getNMSClass("core.registries", "BuiltInRegistries");
                ENTITY_TYPE = Reflection.getFieldValue(Reflection.getFieldGetter(REGISTRIES, "h"));
            } else if (XReflection.supports(19)) {
                Class<?> REGISTRY = Reflection.getNMSClass("core", "IRegistry");
                ENTITY_TYPE = Reflection.getFieldValue(Reflection.getFieldGetter(REGISTRY, "X"));
            } else if (MINOR_NUMBER == 18 && XReflection.PATCH_NUMBER == 2) {
                Class<?> REGISTRY = Reflection.getNMSClass("core", "IRegistry");
                ENTITY_TYPE = Reflection.getFieldValue(Reflection.getFieldGetter(REGISTRY, "W"));
            } else {
                Class<?> REGISTRY = Reflection.getNMSClass("core", "IRegistry");
                ENTITY_TYPE = Reflection.getFieldValue(Reflection.getFieldGetter(REGISTRY, "Z"));
            }
        } else {
            Class<?> REGISTRY = Reflection.getNMSClass("core", "IRegistry");
            ENTITY_TYPE = Reflection.getFieldValue(Reflection.getFieldGetter(REGISTRY, "Y"));
        }

        MethodHandle getId;
        Class<?> REGISTRY = Reflection.getNMSClass("core", "Registry");
        if (XReflection.supports(18)) {
            getId = Reflection.getMethod(REGISTRY, "a", Object.class);
        } else {
            getId = Reflection.getMethod(REGISTRY, "getId", Object.class);
        }
        if (getId == null) return -1;

        try {
            return (int) getId.invoke(ENTITY_TYPE, type);
        } catch (Throwable throwable) {
            return -1;
        }
    }
}