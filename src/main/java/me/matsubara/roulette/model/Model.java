package me.matsubara.roulette.model;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.util.Vector3f;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import lombok.Getter;
import lombok.Setter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.data.CustomizationGroup;
import me.matsubara.roulette.model.stand.PacketStand;
import me.matsubara.roulette.model.stand.StandSettings;
import me.matsubara.roulette.util.PluginUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.IntStream;

@Getter
@Setter
public final class Model {

    // Instance of the plugin.
    private final RoulettePlugin plugin;

    // The UUID of this model.
    private final UUID modelUniqueId;

    // Name of the model.
    private final String name;

    // Center point of the model.
    private final Location location;

    // List with all stands associated with a name.
    private final List<PacketStand> stands = new ArrayList<>();

    // Set with the unique id of the players who aren't seeing the entity due to the distance.
    private final Set<UUID> outOfRange = new HashSet<>();

    // Type of the carpets.
    private Material carpetsType;

    // Customization group.
    private CustomizationGroup texture;

    // The index of the pattern used.
    private int patternIndex;

    // Configuration from a model file.
    private FileConfiguration configuration;

    // All patterns.
    public static final String[][] PATTERNS = {
            // Default.
            {"###", "###", "###"},
            // Ascendent.
            {"#  ", "## ", "###"},
            // Descendent.
            {"###", "## ", "#  "},
            // Variant #1
            {"## ", "###", "## "},
            // Variant #2
            {"###", "## ", "###"},
            // Variant #3
            {"## ", "###", "#  "},
            // Variant #4
            {"#  ", "###", "## "}};

    public static final int[] CHAIR_FIRST_LAYER = IntStream.range(0, 10).map(x -> (x * 3) + 1).toArray();
    public static final int[] CHAIR_SECOND_LAYER = IntStream.range(0, 10).map(x -> (x * 3) + 2).toArray();

    @SuppressWarnings("deprecation")
    public Model(
            RoulettePlugin plugin,
            String name,
            UUID modelId,
            Location location,
            @Nullable Material carpet,
            @Nullable Material customization,
            @Nullable Integer patternIndex) {
        this.plugin = plugin;
        this.modelUniqueId = modelId;
        this.name = name;
        this.location = location;
        this.carpetsType = carpet != null && Tag.CARPETS.isTagged(carpet) ? carpet : Material.RED_CARPET;
        this.texture = customization != null ? CustomizationGroup.getByBlock(customization) : CustomizationGroup.getDefaultCustomization();
        this.patternIndex = patternIndex != null && patternIndex < PATTERNS.length ?
                patternIndex :
                PluginUtils.RANDOM.nextInt(PATTERNS.length);

        loadFile();
        loadModel();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void loadFile() {
        File file = new File(plugin.getDataFolder() + File.separator + "models", name + ".yml");

        // Create if it doesn't exist.
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }

        // Load configuration.
        configuration = new YamlConfiguration();
        try {
            configuration.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            exception.printStackTrace();
        }
    }

    public boolean isInvalidName(String name) {
        return getByName(name) != null || name.contains(".") || name.contains(" ");
    }

    public void addNew(String name, StandSettings settings, @Nullable Location copyLocation, @Nullable Float yaw) {
        if (isInvalidName(name)) return;

        Location finalLocation = copyLocation != null ? copyLocation : location;
        if (yaw != null) finalLocation.setYaw(finalLocation.getYaw() + yaw);

        if (name.startsWith("SPINNER")) {
            finalLocation.subtract(0.0d, 0.002d, 0.0d);
        }

        // Save the name of the current part.
        settings.setPartName(name);

        PacketStand stand = new PacketStand(plugin, finalLocation, settings);
        stand.spawn();

        stands.add(stand);
    }

    private @Nullable String getDecoURL(@NotNull String name) {
        String[] pattern = PATTERNS[patternIndex];

        String url = null;
        int current = (name.charAt(name.length() - 1) - '0') - 1;
        if (current < 3) {
            // DECO_1 / DECO_2 / DECO_3
            if (pattern[0].charAt(current) == '#') {
                url = "a7de569743d1e7c080ad0f590d539aa573a0af4ba7be23ec8d793269fe927088";
            }
        } else if (current < 6) {
            // DECO_4 / DECO_5 / DECO_6
            if (pattern[1].charAt(current - 3) == '#') {
                url = "13444e6349cb549e2e800c23ca206d2360f129e45ca3130d587ff97507a46462";
            }
        } else {
            // DECO_7 / DECO_8 / DECO_9
            if (pattern[2].charAt(current - 6) == '#') {
                url = "b0458d58c030cfabd8b19e4944bbe2860f6617a77ec6c9488593e2a473db6758";
            }
        }

        return url;
    }

    public void kill() {
        stands.forEach(PacketStand::destroy);
        stands.clear();
    }

    private void loadModel() {
        ConfigurationSection section = configuration.getConfigurationSection("parts");
        if (section == null) return;

        List<String> keys = new ArrayList<>(section.getKeys(false));
        for (String path : keys) {
            String defaultPath = "parts." + path + ".";

            // Load offsets.
            double xOffset = configuration.getDouble(defaultPath + "offset.x");
            double yOffset = configuration.getDouble(defaultPath + "offset.y");
            double zOffset = configuration.getDouble(defaultPath + "offset.z");

            Vector offset = new Vector(xOffset, yOffset, zOffset);

            // Pitch isn't necessary.
            float yaw = (float) configuration.getDouble(defaultPath + "offset.yaw");

            Location location = this.location.clone().add(PluginUtils.offsetVector(offset, this.location.getYaw(), this.location.getPitch()));

            StandSettings settings = new StandSettings();
            settings.setOffset(offset);
            settings.setExtraYaw(yaw);

            String settingPath = defaultPath + "settings.";

            // Set settings.
            settings.setInvisible(configuration.getBoolean(settingPath + "invisible"));
            settings.setSmall(configuration.getBoolean(settingPath + "small"));
            settings.setBasePlate(configuration.getBoolean(settingPath + "baseplate", true)); // Armor stands have baseplate by default.
            settings.setArms(configuration.getBoolean(settingPath + "arms"));
            settings.setFire(configuration.getBoolean(settingPath + "fire"));
            settings.setMarker(configuration.getBoolean(settingPath + "marker"));

            // Set poses.
            settings.setHeadPose(loadAngle(path, "head"));
            settings.setBodyPose(loadAngle(path, "body"));
            settings.setLeftArmPose(loadAngle(path, "left-arm"));
            settings.setRightArmPose(loadAngle(path, "right-arm"));
            settings.setLeftLegPose(loadAngle(path, "left-leg"));
            settings.setRightLegPose(loadAngle(path, "right-leg"));

            // Set equipment.
            for (PacketStand.ItemSlot slot : PacketStand.ItemSlot.values()) {
                settings.getEquipment().put(slot.getSlot(), loadEquipment(path, slot.getPath()));
            }

            settings.getTags().addAll(configuration.getStringList("parts." + path + ".tags"));

            addNew(path, settings, location, yaw);
        }

        updateModel();
    }

    public void updateModel() {
        for (PacketStand stand : stands) {
            StandSettings settings = stand.getSettings();
            String name = settings.getPartName();

            Material helmet = null;

            if (name.startsWith("SIDE")) {
                helmet = texture.slab();
            }

            if (name.startsWith("FEET")) {
                helmet = texture.block();
            }

            if (name.startsWith("CHAIR")) {
                int current = Integer.parseInt(name.split("_")[1]);
                if (ArrayUtils.contains(CHAIR_FIRST_LAYER, current)) {
                    helmet = texture.block();
                } else if (ArrayUtils.contains(CHAIR_SECOND_LAYER, current)) {
                    helmet = texture.slab();
                } else {
                    helmet = carpetsType;
                    settings.setMarker(true);
                }
            }

            if (helmet != null) {
                ItemStack temp = SpigotConversionUtil.fromBukkitItemStack(new org.bukkit.inventory.ItemStack(helmet));
                settings.getEquipment().put(EquipmentSlot.HELMET, temp);
            }

            // Spawn random decoration.
            if (name.startsWith("DECO")) {
                String url = getDecoURL(name);

                ItemStack temp = url != null ?
                        SpigotConversionUtil.fromBukkitItemStack(PluginUtils.createHead(url)) :
                        ItemStack.EMPTY;

                settings.getEquipment().put(EquipmentSlot.MAIN_HAND, temp);
            }

            stand.sendMetadata();
            stand.sendEquipment();
        }
    }

    private @Nullable ItemStack loadEquipment(String path, String equipment) {
        String defaultPath = "parts." + path + ".equipment." + equipment;
        if (configuration.get(defaultPath) == null) return null;

        ItemStack item = null;
        if (configuration.get(defaultPath + ".material") != null) {
            Material material = PluginUtils.getOrDefault(Material.class, configuration.getString(defaultPath + ".material", "STONE"), Material.STONE);
            item = SpigotConversionUtil.fromBukkitItemStack(new org.bukkit.inventory.ItemStack(material));
        }

        if (item != null && item.getType() == ItemTypes.PLAYER_HEAD && configuration.get(defaultPath + ".url") != null) {
            item = SpigotConversionUtil.fromBukkitItemStack(PluginUtils.createHead(configuration.getString(defaultPath + ".url"), true));
        }

        return item;
    }

    private @NotNull Vector3f loadAngle(String path, String pose) {
        String defaultPath = "parts." + path + ".pose." + pose;

        if (configuration.get(defaultPath) != null) {
            double x = configuration.getDouble(defaultPath + ".x");
            double y = configuration.getDouble(defaultPath + ".y");
            double z = configuration.getDouble(defaultPath + ".z");
            return new Vector3f(
                    (float) Math.toRadians(x),
                    (float) Math.toRadians(y),
                    (float) Math.toRadians(z));
        }

        return Vector3f.zero();
    }

    public @Nullable PacketStand getByName(String name) {
        for (PacketStand stand : stands) {
            String partName = stand.getSettings().getPartName();
            if (partName != null && partName.equals(name)) return stand;
        }
        return null;
    }
}