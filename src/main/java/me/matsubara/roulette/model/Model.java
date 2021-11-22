package me.matsubara.roulette.model;

import com.cryptomorin.xseries.SkullUtils;
import com.cryptomorin.xseries.XMaterial;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.model.stand.StandSettings;
import me.matsubara.roulette.model.stand.PacketStand;
import me.matsubara.roulette.util.PluginUtils;
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

    // Configuration from model file.
    private FileConfiguration configuration;

    public Model(RoulettePlugin plugin, String name, UUID modelId, Location location) {
        this.plugin = plugin;
        this.modelUniqueId = modelId;
        this.name = name;
        this.location = location;
        this.stands = new LinkedHashMap<>();
        this.locations = new LinkedHashMap<>();

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

        // Shouldn't have an end rod at spawn, only when spinning.
        if (name.equalsIgnoreCase("BALL")) settings.setHelmet(null);

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
            settings.setOnFire(configuration.getBoolean(defaultPath + "settings.fire"));
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
    private ItemStack loadEquipment(String path, String equipment) {
        String defaultPath = "parts." + path + ".equipment." + equipment;
        if (configuration.get(defaultPath) == null) return null;

        ItemStack item = null;
        if (configuration.get(defaultPath + ".material") != null) {
            item = XMaterial.matchXMaterial(configuration.getString(defaultPath + ".material")).map(XMaterial::parseItem).orElse(null);
        }

        if (item != null && item.getItemMeta() != null && configuration.get(defaultPath + ".url") != null) {
            SkullMeta meta = SkullUtils.applySkin(item.getItemMeta(), configuration.getString(defaultPath + ".url"));
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

    public String getName() {
        return name;
    }

    public Location getLocation() {
        return location;
    }

    public UUID getUniqueId() {
        return modelUniqueId;
    }

    public PacketStand getByName(String name) {
        for (Map.Entry<String, PacketStand> stand : stands.entrySet()) {
            if (stand.getKey().equalsIgnoreCase(name)) return stand.getValue();
        }
        return null;
    }
}