package me.matsubara.roulette.model.stand;

import com.cryptomorin.xseries.ReflectionUtils;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.hook.ViaExtension;
import me.matsubara.roulette.util.PluginUtils;
import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.EulerAngle;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.*;

@SuppressWarnings({"ConstantConditions", "BooleanMethodIsAlwaysInverted"})
public final class PacketStand {

    // Instance of the plugin.
    private final static RoulettePlugin PLUGIN = JavaPlugin.getPlugin(RoulettePlugin.class);

    // Instance of the entity.
    private Object stand;

    // Current location of the entity.
    private Location location;

    // Set with the unique id of the players who aren't seeing the entity due to the distance.
    private Set<UUID> ignored;

    // Entity attributes.
    private int entityId;
    private int[] passengersId = {};
    private StandSettings settings;

    // Most changes are made since 1.13 version.
    private final static boolean isMoreThan12 = ReflectionUtils.VER > 12;

    // Craft classes.
    private final static Class<?> CRAFT_WORLD;
    private final static Class<?> CRAFT_CHAT_MESSAGE;

    // NMS classes.
    private final static Class<?> WORLD;
    private final static Class<?> WORLD_SERVER;
    private final static Class<?> I_CHAT_BASE_COMPONENT;
    private final static Class<?> ENTITY;
    private final static Class<?> ENTITY_LIVING;
    private final static Class<?> ENTITY_ARMOR_STAND;
    private final static Class<?> PACKET_SPAWN_ENTITY_LIVING;
    private final static Class<?> PACKET_ENTITY_HEAD_ROTATION;
    private final static Class<?> PACKET_ENTITY_TELEPORT;
    private final static Class<?> PACKET_ENTITY_LOOK;
    private final static Class<?> VECTOR3F;
    private final static Class<?> DATA_WATCHER;
    private final static Class<?> PACKET_ENTITY_METADATA;
    private final static Class<?> PACKET_MOUNT;
    private final static Class<?> PACKET_ENTITY_EQUIPMENT;
    private final static Class<?> ENUM_ITEM_SLOT;
    private final static Class<?> ITEM_STACK;
    private final static Class<?> CRAFT_ITEM_STACK;
    private final static Class<?> PACKET_ENTITY_DESTROY;
    private final static Class<?> PAIR;

    // Methods.
    private final static MethodHandle getHandle;
    private final static MethodHandle getDataWatcher;
    private final static MethodHandle fromStringOrNull;
    private final static MethodHandle getId;
    private final static MethodHandle setLocation;
    private final static MethodHandle setInvisible;
    private final static MethodHandle setArms;
    private final static MethodHandle setBasePlate;
    private final static MethodHandle setSmall;
    private final static MethodHandle setMarker;
    private final static MethodHandle setCustomName;
    private final static MethodHandle setCustomNameVisible;
    private final static MethodHandle setHeadPose;
    private final static MethodHandle setBodyPose;
    private final static MethodHandle setLeftArmPose;
    private final static MethodHandle setRightArmPose;
    private final static MethodHandle setLeftLegPose;
    private final static MethodHandle setRightLegPose;
    private final static MethodHandle asNMSCopy;
    private final static MethodHandle setFlag;
    private final static MethodHandle of;

    // Constructors.
    private static Constructor<?> entityArmorStand;
    private static Constructor<?> packetSpawnEntityLiving;
    private static Constructor<?> packetEntityHeadRotation;
    private static Constructor<?> packetEntityTeleport;
    private static Constructor<?> packetEntityLook;
    private static Constructor<?> vector3f;
    private static Constructor<?> packetEntityMetadata;
    private static Constructor<?> packetMount;
    private static Constructor<?> packetEntityEquipment;
    private static Constructor<?> packetEntityDestroy;

    static {
        // Initialize classes.
        CRAFT_WORLD = ReflectionUtils.getCraftClass("CraftWorld");
        CRAFT_CHAT_MESSAGE = !isMoreThan12 ? null : ReflectionUtils.getCraftClass("util.CraftChatMessage");
        CRAFT_ITEM_STACK = ReflectionUtils.getCraftClass("inventory.CraftItemStack");
        WORLD = ReflectionUtils.getNMSClass("world.level", "World");
        WORLD_SERVER = ReflectionUtils.getNMSClass("server.level", "WorldServer");
        I_CHAT_BASE_COMPONENT = ReflectionUtils.getNMSClass("network.chat", "IChatBaseComponent");
        ENTITY = ReflectionUtils.getNMSClass("world.entity", "Entity");
        ENTITY_LIVING = ReflectionUtils.getNMSClass("world.entity", "EntityLiving");
        ENTITY_ARMOR_STAND = ReflectionUtils.getNMSClass("world.entity.decoration", "EntityArmorStand");
        PACKET_SPAWN_ENTITY_LIVING = ReflectionUtils.getNMSClass("network.protocol.game", "PacketPlayOutSpawnEntityLiving");
        PACKET_ENTITY_HEAD_ROTATION = ReflectionUtils.getNMSClass("network.protocol.game", "PacketPlayOutEntityHeadRotation");
        PACKET_ENTITY_TELEPORT = ReflectionUtils.getNMSClass("network.protocol.game", "PacketPlayOutEntityTeleport");
        PACKET_ENTITY_LOOK = ReflectionUtils.getNMSClass("network.protocol.game", "PacketPlayOutEntity$PacketPlayOutEntityLook");
        VECTOR3F = ReflectionUtils.getNMSClass("core", "Vector3f");
        DATA_WATCHER = ReflectionUtils.getNMSClass("network.syncher", "DataWatcher");
        PACKET_ENTITY_METADATA = ReflectionUtils.getNMSClass("network.protocol.game", "PacketPlayOutEntityMetadata");
        PACKET_MOUNT = ReflectionUtils.getNMSClass("network.protocol.game", "PacketPlayOutMount");
        PACKET_ENTITY_EQUIPMENT = ReflectionUtils.getNMSClass("network.protocol.game", "PacketPlayOutEntityEquipment");
        ENUM_ITEM_SLOT = ReflectionUtils.getNMSClass("world.entity", "EnumItemSlot");
        ITEM_STACK = ReflectionUtils.getNMSClass("world.item", "ItemStack");
        PACKET_ENTITY_DESTROY = ReflectionUtils.getNMSClass("network.protocol.game", "PacketPlayOutEntityDestroy");

        Class<?> pair;
        try {
            pair = Class.forName("com.mojang.datafixers.util.Pair");
        } catch (ClassNotFoundException exception) {
            pair = null;
        }
        PAIR = pair;

        MethodHandle handle = null;
        MethodHandle watcher = null;
        MethodHandle fromString = null;
        MethodHandle id = null;
        MethodHandle location = null;
        MethodHandle invisible = null;
        MethodHandle arms = null;
        MethodHandle basePlate = null;
        MethodHandle small = null;
        MethodHandle marker = null;
        MethodHandle name = null;
        MethodHandle nameVisible = null;
        MethodHandle head = null;
        MethodHandle body = null;
        MethodHandle leftArm = null;
        MethodHandle rightArm = null;
        MethodHandle leftLeg = null;
        MethodHandle rightLeg = null;
        MethodHandle nmsCopy = null;
        MethodHandle flag = null;
        MethodHandle ofPair = null;

        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            // Initialize methods.
            handle = lookup.findVirtual(CRAFT_WORLD, "getHandle", MethodType.methodType(WORLD_SERVER));
            watcher = lookup.findVirtual(ENTITY_ARMOR_STAND, "getDataWatcher", MethodType.methodType(DATA_WATCHER));
            fromString = !isMoreThan12 ?
                    null :
                    lookup.findStatic(CRAFT_CHAT_MESSAGE, "fromStringOrNull", MethodType.methodType(I_CHAT_BASE_COMPONENT, String.class));
            id = lookup.findVirtual(ENTITY_ARMOR_STAND, "getId", MethodType.methodType(int.class));
            location = lookup.findVirtual(ENTITY_ARMOR_STAND, "setLocation", MethodType.methodType(void.class, double.class, double.class, double.class, float.class, float.class));
            invisible = lookup.findVirtual(ENTITY_ARMOR_STAND, "setInvisible", MethodType.methodType(void.class, boolean.class));
            arms = lookup.findVirtual(ENTITY_ARMOR_STAND, "setArms", MethodType.methodType(void.class, boolean.class));
            basePlate = lookup.findVirtual(ENTITY_ARMOR_STAND, "setBasePlate", MethodType.methodType(void.class, boolean.class));
            small = lookup.findVirtual(ENTITY_ARMOR_STAND, "setSmall", MethodType.methodType(void.class, boolean.class));
            marker = lookup.findVirtual(ENTITY_ARMOR_STAND, ReflectionUtils.VER == 8 ? "n" : "setMarker", MethodType.methodType(void.class, boolean.class));
            name = lookup.findVirtual(ENTITY_ARMOR_STAND, "setCustomName", MethodType.methodType(void.class, isMoreThan12 ? I_CHAT_BASE_COMPONENT : String.class));
            nameVisible = lookup.findVirtual(ENTITY_ARMOR_STAND, "setCustomNameVisible", MethodType.methodType(void.class, boolean.class));
            head = lookup.findVirtual(ENTITY_ARMOR_STAND, "setHeadPose", MethodType.methodType(void.class, VECTOR3F));
            body = lookup.findVirtual(ENTITY_ARMOR_STAND, "setBodyPose", MethodType.methodType(void.class, VECTOR3F));
            leftArm = lookup.findVirtual(ENTITY_ARMOR_STAND, "setLeftArmPose", MethodType.methodType(void.class, VECTOR3F));
            rightArm = lookup.findVirtual(ENTITY_ARMOR_STAND, "setRightArmPose", MethodType.methodType(void.class, VECTOR3F));
            leftLeg = lookup.findVirtual(ENTITY_ARMOR_STAND, "setLeftLegPose", MethodType.methodType(void.class, VECTOR3F));
            rightLeg = lookup.findVirtual(ENTITY_ARMOR_STAND, "setRightLegPose", MethodType.methodType(void.class, VECTOR3F));
            nmsCopy = lookup.findStatic(CRAFT_ITEM_STACK, "asNMSCopy", MethodType.methodType(ITEM_STACK, ItemStack.class));
            flag = lookup.findVirtual(ENTITY_ARMOR_STAND, "setFlag", MethodType.methodType(void.class, int.class, boolean.class));
            ofPair = PAIR == null ? null : lookup.findStatic(PAIR, "of", MethodType.methodType(PAIR, Object.class, Object.class));

            // Initialize constructors.
            entityArmorStand = ENTITY_ARMOR_STAND.getConstructor(WORLD, double.class, double.class, double.class);
            packetSpawnEntityLiving = PACKET_SPAWN_ENTITY_LIVING.getConstructor(ENTITY_LIVING);
            packetEntityHeadRotation = PACKET_ENTITY_HEAD_ROTATION.getConstructor(ENTITY, byte.class);
            packetEntityTeleport = PACKET_ENTITY_TELEPORT.getConstructor(ENTITY);
            packetEntityLook = PACKET_ENTITY_LOOK.getConstructor(int.class, byte.class, byte.class, boolean.class);
            vector3f = VECTOR3F.getConstructor(float.class, float.class, float.class);
            packetEntityMetadata = PACKET_ENTITY_METADATA.getConstructor(int.class, DATA_WATCHER, boolean.class);
            packetMount = ReflectionUtils.VER > 16 ? PACKET_MOUNT.getConstructor(ENTITY) : PACKET_MOUNT.getConstructor();
            packetEntityEquipment = ReflectionUtils.VER > 15 ?
                    PACKET_ENTITY_EQUIPMENT.getConstructor(int.class, List.class) :
                    PACKET_ENTITY_EQUIPMENT.getConstructor(int.class, ENUM_ITEM_SLOT, ITEM_STACK);
            packetEntityDestroy = PACKET_ENTITY_DESTROY.getConstructor(int[].class);
        } catch (Throwable exception) {
            exception.printStackTrace();
        }

        getHandle = handle;
        getDataWatcher = watcher;
        fromStringOrNull = fromString;
        getId = id;
        setLocation = location;
        setInvisible = invisible;
        setArms = arms;
        setBasePlate = basePlate;
        setSmall = small;
        setMarker = marker;
        setCustomName = name;
        setCustomNameVisible = nameVisible;
        setHeadPose = head;
        setBodyPose = body;
        setLeftArmPose = leftArm;
        setRightArmPose = rightArm;
        setLeftLegPose = leftLeg;
        setRightLegPose = rightLeg;
        asNMSCopy = nmsCopy;
        setFlag = flag;
        of = ofPair;
    }

    public PacketStand(Location location, StandSettings settings) {
        this(location, settings, true);
    }

    public PacketStand(Location location, StandSettings settings, boolean showEveryone) {
        Validate.notNull(location.getWorld(), "World can't be null.");

        try {
            Object craftWorld = CRAFT_WORLD.cast(location.getWorld());
            Object nmsWorld = getHandle.invoke(craftWorld);

            this.stand = entityArmorStand.newInstance(nmsWorld, location.getX(), location.getY(), location.getZ());
            this.location = location;
            this.ignored = new HashSet<>();

            this.entityId = (int) getId.invoke(stand);

            this.settings = settings;

            // Set the initial location of this entity.
            setLocation(location);

            // Set settings.
            setInvisible(settings.isInvisible());
            setSmall(settings.isSmall());
            setBasePlate(settings.hasBasePlate());
            setArms(settings.hasArms());
            setOnFire(settings.isOnFire());
            setMarker(settings.isMarker());
            if (settings.getCustomName() != null) {
                setCustomName(settings.getCustomName());
            }
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

    public StandSettings getSettings() {
        return settings;
    }

    public boolean isInRange(Location location) {
        int renderDistance = PLUGIN.getConfigManager().getRenderDistance();
        double distance = Math.min(renderDistance * renderDistance, Math.pow(Bukkit.getViewDistance() << 4, 2));

        if (!this.location.getWorld().equals(location.getWorld())) return false;
        return this.location.distanceSquared(location) <= distance;
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

        try {
            Object packetSpawn = packetSpawnEntityLiving.newInstance(stand);
            sendPacket(player, packetSpawn);

            ignored.remove(player.getUniqueId());

            Bukkit.getScheduler().runTaskAsynchronously(PLUGIN, () -> {
                showPassenger(player);
                updateLocation();
                updateRotation();
                updateMetadata();
                updateEquipment();
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
            setLocation.invoke(stand, location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
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
            Object packetTeleport = packetEntityTeleport.newInstance(stand);
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

            Object packetRotation = packetEntityHeadRotation.newInstance(stand, yaw);
            sendPacket(packetRotation, true);

            //if (ReflectionUtils.VER < 17) {
            Object packetLook = packetEntityLook.newInstance(entityId, yaw, pitch, true);
            sendPacket(packetLook, true);
            //}
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
            setInvisible.invoke(stand, invisible);
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    public void setArms(boolean arms) {
        try {
            settings.setArms(arms);
            setArms.invoke(stand, arms);
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    public void setBasePlate(boolean baseplate) {
        try {
            settings.setBasePlate(baseplate);
            // For some reason must be negated.
            setBasePlate.invoke(stand, !baseplate);
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    public void setSmall(boolean small) {
        try {
            settings.setSmall(small);
            setSmall.invoke(stand, small);
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    public void setMarker(boolean marker) {
        try {
            settings.setMarker(marker);
            setMarker.invoke(stand, marker);
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    public void setOnFire(boolean fire) {
        // Only works on 1.9+.
        if (ReflectionUtils.VER < 9) return;

        settings.setOnFire(fire);

        try {
            setFlag.invoke(stand, 0, fire);

            Object watcher = getDataWatcher.invoke(stand);

            Object packetMetadata = packetEntityMetadata.newInstance(entityId, watcher, true);
            sendPacket(packetMetadata);
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    public void setGlowing(boolean glow) {
        // Only works on 1.9+.
        if (ReflectionUtils.VER < 9) return;

        settings.setGlowing(glow);

        try {
            setFlag.invoke(stand, 6, glow);

            Object watcher = getDataWatcher.invoke(stand);

            Object packetMetadata = packetEntityMetadata.newInstance(entityId, watcher, true);
            sendPacket(packetMetadata);
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
            setCustomName.invoke(stand, isMoreThan12 ? fromStringOrNull.invoke(name) : name);
            settings.setCustomName(name);
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    public void setCustomNameVisible(boolean customNameVisible) {
        try {
            settings.setCustomNameVisible(customNameVisible);
            setCustomNameVisible.invoke(stand, customNameVisible);
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
            if (ReflectionUtils.VER > 16) {
                packetMount = PacketStand.packetMount.newInstance(stand);
            } else {
                packetMount = PacketStand.packetMount.newInstance();
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

    public void setHeadPose(EulerAngle headPose) {
        try {
            settings.setHeadPose(headPose);
            setHeadPose.invoke(stand, getVector3f(headPose));
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    public void setBodyPose(EulerAngle bodyPose) {
        try {
            settings.setBodyPose(bodyPose);
            setBodyPose.invoke(stand, getVector3f(bodyPose));
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    public void setLeftArmPose(EulerAngle leftArmPose) {
        try {
            settings.setLeftArmPose(leftArmPose);
            setLeftArmPose.invoke(stand, getVector3f(leftArmPose));
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    public void setRightArmPose(EulerAngle rightArmPose) {
        try {
            settings.setRightArmPose(rightArmPose);
            setRightArmPose.invoke(stand, getVector3f(rightArmPose));
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    public void setLeftLegPose(EulerAngle leftLegPose) {
        try {
            settings.setLeftLegPose(leftLegPose);
            setLeftLegPose.invoke(stand, getVector3f(leftLegPose));
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    public void setRightLegPose(EulerAngle rightLegPose) {
        try {
            settings.setRightLegPose(rightLegPose);
            setRightLegPose.invoke(stand, getVector3f(rightLegPose));
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
                Field field = ENUM_ITEM_SLOT.getField(ReflectionUtils.VER > 16 ? "" + alphabet[ordinal()] : name());
                return field.get(null);
            } catch (Throwable exception) {
                exception.printStackTrace();
            }
            return null;
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
            if (ReflectionUtils.VER > 15) {
                List<Object> list = new ArrayList<>();
                list.add(of.invoke(slot.get(), itemStack));

                packetEquipment = packetEntityEquipment.newInstance(entityId, list);
            } else {
                packetEquipment = packetEntityEquipment.newInstance(entityId, slot.get(), itemStack);
            }
            sendPacket(packetEquipment);
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    public Object getVector3f(EulerAngle angle) {
        try {
            return vector3f.newInstance((float) Math.toDegrees(angle.getX()), (float) Math.toDegrees(angle.getY()), (float) Math.toDegrees(angle.getZ()));
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
        return null;
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
        try {
            Object watcher = getDataWatcher.invoke(stand);
            Object packetMetadata = packetEntityMetadata.newInstance(entityId, watcher, true);
            sendPacket(packetMetadata);
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    public void destroy() {
        for (Player player : location.getWorld().getPlayers()) {
            destroy(player);
        }
        ignored.clear();
    }

    @SuppressWarnings("PrimitiveArrayArgumentToVarargsMethod")
    public void destroy(Player player) {
        int[] ids = new int[]{entityId};

        try {
            Object packetDestroy = packetEntityDestroy.newInstance(ids);

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
        if (sync) ReflectionUtils.sendPacketSync(player, packet);
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

            // 755 = 1.17
            if (isEntityLook && (ReflectionUtils.VER > 16 || (usingVia && ViaExtension.getPlayerVersion(player) > 754))) {
                continue;
            }

            sendPacket(player, packet, sync);
        }
    }
}