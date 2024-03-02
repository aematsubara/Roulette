package me.matsubara.roulette.model;

import com.cryptomorin.xseries.XMaterial;
import lombok.Getter;
import lombok.Setter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.model.stand.PacketStand;
import me.matsubara.roulette.model.stand.StandSettings;
import me.matsubara.roulette.util.PluginUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
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

    // Type of the carpets.
    private XMaterial carpetsType;

    // Type of the planks.
    private XMaterial planksType;

    // Type of the slabs.
    private XMaterial slabsType;

    // The pattern of the decoration.
    private final String[] decoPattern;

    // Configuration from model file.
    private FileConfiguration configuration;

    // All patterns.
    private static final String[][] PATTERNS = {
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

    public Model(
            RoulettePlugin plugin,
            String name,
            UUID modelId,
            Location location,
            @Nullable XMaterial carpetsType,
            @Nullable XMaterial planksType,
            @Nullable XMaterial slabsType,
            @Nullable String[] decoPattern) {
        this.plugin = plugin;
        this.modelUniqueId = modelId;
        this.name = name;
        this.location = location;
        this.carpetsType = carpetsType != null ? carpetsType : XMaterial.RED_CARPET;
        this.planksType = planksType != null ? planksType : XMaterial.SPRUCE_PLANKS;
        this.slabsType = slabsType != null ? slabsType : XMaterial.SPRUCE_SLAB;
        this.decoPattern = decoPattern != null ? decoPattern : PATTERNS[ThreadLocalRandom.current().nextInt(PATTERNS.length)];

        loadFile();
        loadModel();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isModelSpawned() {
        if (section == null) return false;
        return stands.size() == section.getKeys(false).size();
    }

    private ConfigurationSection section;

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
            section = configuration.getConfigurationSection("parts");
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

        ItemStack helmet = null;

        if (name.startsWith("SIDE")) {
            helmet = slabsType.parseItem();
        }

        if (name.startsWith("FEET")) {
            helmet = planksType.parseItem();
        }

        if (name.startsWith("CHAIR")) {
            int current = Integer.parseInt(name.split("_")[1]);
            if (ArrayUtils.contains(CHAIR_FIRST_LAYER, current)) {
                helmet = planksType.parseItem();
            } else if (ArrayUtils.contains(CHAIR_SECOND_LAYER, current)) {
                helmet = slabsType.parseItem();
            } else {
                helmet = carpetsType.parseItem();
                settings.setMarker(true);
            }
        }

        if (helmet != null) {
            settings.getEquipment().put(PacketStand.ItemSlot.HEAD, helmet);
        }

        // Spawn random decoration.
        if (name.startsWith("DECO")) {
            String url = null;
            int current = (name.charAt(name.length() - 1) - '0') - 1;
            if (current < 3) {
                // DECO_1 / DECO_2 / DECO_3
                if (decoPattern[0].charAt(current) == '#') {
                    url = "a7de569743d1e7c080ad0f590d539aa573a0af4ba7be23ec8d793269fe927088";
                }
            } else if (current < 6) {
                // DECO_4 / DECO_5 / DECO_6
                if (decoPattern[1].charAt(current - 3) == '#') {
                    url = "13444e6349cb549e2e800c23ca206d2360f129e45ca3130d587ff97507a46462";
                }
            } else {
                // DECO_7 / DECO_8 / DECO_9
                if (decoPattern[2].charAt(current - 6) == '#') {
                    url = "b0458d58c030cfabd8b19e4944bbe2860f6617a77ec6c9488593e2a473db6758";
                }
            }

            if (url != null) {
                settings.getEquipment().put(PacketStand.ItemSlot.MAINHAND, PluginUtils.createHead(url));
            }
        }

        // Save the name of the current part.
        settings.setPartName(name);

        stands.add(new PacketStand(finalLocation, settings, true, plugin.getConfigManager().getRenderDistance()));
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

            // Pitch isn't needed.
            float yaw = (float) configuration.getDouble(defaultPath + "offset.yaw");

            Location location = this.location.clone().add(PluginUtils.offsetVector(offset, this.location.getYaw(), this.location.getPitch()));

            StandSettings settings = new StandSettings();
            settings.setXOffset(xOffset);
            settings.setYOffset(yOffset);
            settings.setZOffset(zOffset);
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
                settings.getEquipment().put(slot, loadEquipment(path, slot.getConfigPathName()));
            }

            settings.getTags().addAll(configuration.getStringList("parts." + path + ".tags"));

            addNew(path, settings, location, yaw);
        }
    }

    private @Nullable ItemStack loadEquipment(String path, String equipment) {
        String defaultPath = "parts." + path + ".equipment." + equipment;
        if (configuration.get(defaultPath) == null) return null;

        ItemStack item = null;
        if (configuration.get(defaultPath + ".material") != null) {
            Material material = PluginUtils.getOrDefault(Material.class, configuration.getString(defaultPath + ".material", "STONE"), Material.STONE);
            item = new ItemStack(material);
        }

        if (item != null && item.getType() == Material.PLAYER_HEAD && configuration.get(defaultPath + ".url") != null) {
            item = PluginUtils.createHead(configuration.getString(defaultPath + ".url"), true);
        }

        return item;
    }

    private EulerAngle loadAngle(String path, String pose) {
        String defaultPath = "parts." + path + ".pose." + pose;

        if (configuration.get(defaultPath) != null) {
            double x = configuration.getDouble(defaultPath + ".x");
            double y = configuration.getDouble(defaultPath + ".y");
            double z = configuration.getDouble(defaultPath + ".z");
            return new EulerAngle(Math.toRadians(x), Math.toRadians(y), Math.toRadians(z));
        }

        return EulerAngle.ZERO;
    }

    public @Nullable PacketStand getByName(String name) {
        for (PacketStand stand : stands) {
            String partName = stand.getSettings().getPartName();
            if (partName != null && partName.equals(name)) return stand;
        }
        return null;
    }
}