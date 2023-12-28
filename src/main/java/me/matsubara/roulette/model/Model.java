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
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.Map;
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

    // Map with all stands associated with a name.
    private final Map<String, PacketStand> stands;

    // Some parts of this model aren't stands.
    private final Map<String, Map.Entry<Location, StandSettings>> locations;

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
        this.stands = new LinkedHashMap<>();
        this.locations = new LinkedHashMap<>();
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
        return stands.size() + locations.size() == section.getKeys(false).size();
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

    public void addNew(String name, StandSettings settings, @Nullable Location copyLocation, @Nullable Float yaw) {
        if (stands.containsKey(name) || name.contains(".") || name.contains(" ")) return;

        Location finalLocation = copyLocation != null ? copyLocation : location;
        if (yaw != null) finalLocation.setYaw(finalLocation.getYaw() + yaw);

        if (name.startsWith("SPINNER")) {
            finalLocation.subtract(0.0d, 0.002d, 0.0d);
        }

        if (name.startsWith("SIDE")) {
            settings.setHelmet(slabsType.parseItem());
        }

        if (name.startsWith("FEET")) {
            settings.setHelmet(planksType.parseItem());
        }

        if (name.startsWith("CHAIR")) {
            int current = Integer.parseInt(name.split("_")[1]);
            if (ArrayUtils.contains(CHAIR_FIRST_LAYER, current)) {
                settings.setHelmet(planksType.parseItem());
            } else if (ArrayUtils.contains(CHAIR_SECOND_LAYER, current)) {
                settings.setHelmet(slabsType.parseItem());
            } else {
                settings.setHelmet(carpetsType.parseItem());
                settings.setMarker(true);
            }
        }

        // Spawn random decoration.
        if (name.startsWith("DECO")) {
            int current = (name.charAt(name.length() - 1) - '0') - 1;
            if (current < 3) {
                // DECO_1 / DECO_2 / DECO_3
                if (decoPattern[0].charAt(current) == '#') {
                    settings.setMainHand(PluginUtils.createHead("a7de569743d1e7c080ad0f590d539aa573a0af4ba7be23ec8d793269fe927088"));
                }
            } else if (current < 6) {
                // DECO_4 / DECO_5 / DECO_6
                if (decoPattern[1].charAt(current - 3) == '#') {
                    settings.setMainHand(PluginUtils.createHead("13444e6349cb549e2e800c23ca206d2360f129e45ca3130d587ff97507a46462"));
                }
            } else {
                // DECO_7 / DECO_8 / DECO_9
                if (decoPattern[2].charAt(current - 6) == '#') {
                    settings.setMainHand(PluginUtils.createHead("b0458d58c030cfabd8b19e4944bbe2860f6617a77ec6c9488593e2a473db6758"));
                }
            }
        }

        if ((name.contains("SLOT") && !name.equalsIgnoreCase("MONEY_SLOT")) || name.contains("COLUMN") || name.contains("DOZEN")) {
            locations.put(name, new AbstractMap.SimpleEntry<>(finalLocation, settings));
        } else {
            stands.put(name, new PacketStand(finalLocation, settings));
        }
    }

    public Map<String, PacketStand> getStands() {
        return stands;
    }

    public Map<String, Map.Entry<Location, StandSettings>> getLocations() {
        return locations;
    }

    public void kill() {
        stands.forEach((name, stand) -> stand.destroy());
        stands.clear();
    }

    public void loadModel() {
        ConfigurationSection section = configuration.getConfigurationSection("parts");
        if (section == null) return;

        for (String path : section.getKeys(false)) {
            String defaultPath = "parts." + path + ".";

            // Load offsets.
            double xOffset = configuration.getDouble(defaultPath + "offset.x");
            double yOffset = configuration.getDouble(defaultPath + "offset.y");
            double zOffset = configuration.getDouble(defaultPath + "offset.z");

            Vector offset = new Vector(xOffset, yOffset, zOffset);

            // Pitch not needed.
            float yaw = (float) configuration.getDouble(defaultPath + "offset.yaw");

            Location location = this.location.clone().add(PluginUtils.offsetVector(offset, this.location.getYaw(), this.location.getPitch()));

            StandSettings settings = new StandSettings();

            // Set settings.
            settings.setInvisible(configuration.getBoolean(defaultPath + "settings.invisible"));
            settings.setSmall(configuration.getBoolean(defaultPath + "settings.small"));
            settings.setBasePlate(configuration.getBoolean(defaultPath + "settings.baseplate"));
            settings.setArms(configuration.getBoolean(defaultPath + "settings.arms"));
            settings.setFire(configuration.getBoolean(defaultPath + "settings.fire"));
            settings.setMarker(configuration.getBoolean(defaultPath + "settings.marker"));

            // Set poses.
            settings.setHeadPose(loadAngle(path, "head"));
            settings.setBodyPose(loadAngle(path, "body"));
            settings.setLeftArmPose(loadAngle(path, "left-arm"));
            settings.setRightArmPose(loadAngle(path, "right-arm"));
            settings.setLeftLegPose(loadAngle(path, "left-leg"));
            settings.setRightLegPose(loadAngle(path, "right-leg"));

            // Set equipment.
            settings.setHelmet(loadEquipment(path, "helmet"));
            settings.setChestplate(loadEquipment(path, "chestplate"));
            settings.setLeggings(loadEquipment(path, "leggings"));
            settings.setBoots(loadEquipment(path, "boots"));
            settings.setMainHand(loadEquipment(path, "main-hand"));
            settings.setOffHand(loadEquipment(path, "off-hand"));

            addNew(path, settings, location, yaw);
        }
    }

    @SuppressWarnings("ConstantConditions")
    private @Nullable ItemStack loadEquipment(String path, String equipment) {
        String defaultPath = "parts." + path + ".equipment." + equipment;
        if (configuration.get(defaultPath) == null) return null;

        ItemStack item = null;
        if (configuration.get(defaultPath + ".material") != null) {
            item = XMaterial.matchXMaterial(configuration.getString(defaultPath + ".material")).map(XMaterial::parseItem).orElse(null);
        }

        if (item != null && item.getItemMeta() != null && configuration.get(defaultPath + ".url") != null) {
            SkullMeta meta = (SkullMeta) item.getItemMeta();
            PluginUtils.applySkin(meta, configuration.getString(defaultPath + ".url"), true);
            item.setItemMeta(meta);
        }

        return item;
    }

    private EulerAngle loadAngle(String path, String pose) {
        String defaultPath = "parts." + path + ".pose.";

        if (configuration.get(defaultPath + pose) != null) {
            double x = configuration.getDouble(defaultPath + pose + ".x");
            double y = configuration.getDouble(defaultPath + pose + ".y");
            double z = configuration.getDouble(defaultPath + pose + ".z");
            return new EulerAngle(Math.toRadians(x), Math.toRadians(y), Math.toRadians(z));
        }

        return EulerAngle.ZERO;
    }

    public @Nullable PacketStand getByName(String name) {
        for (Map.Entry<String, PacketStand> stand : stands.entrySet()) {
            if (stand.getKey().equalsIgnoreCase(name)) return stand.getValue();
        }
        return null;
    }
}