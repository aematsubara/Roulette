package me.matsubara.roulette.model;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import lombok.Getter;
import lombok.Setter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.GameType;
import me.matsubara.roulette.game.data.CustomizationGroup;
import me.matsubara.roulette.game.data.Slot;
import me.matsubara.roulette.model.stand.ModelLocation;
import me.matsubara.roulette.model.stand.PacketStand;
import me.matsubara.roulette.model.stand.StandSettings;
import me.matsubara.roulette.model.stand.data.ItemSlot;
import me.matsubara.roulette.util.PluginUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Getter
@Setter
public final class Model {

    // Instance of the plugin.
    private final RoulettePlugin plugin;

    // The UUID of this model.
    private final UUID modelUniqueId;

    // Center point of the model.
    private final Location location;

    // List with all stands associated with a name.
    private final List<PacketStand> stands = new ArrayList<>();

    // Set with all locations associated with a name, mostly used to spawn particles.
    private final List<ModelLocation> locations = new ArrayList<>();

    // Set with the unique id of the players who aren't seeing the entity due to the distance.
    private final Set<UUID> out = new HashSet<>();

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

    // Cached model parts.
    private static final Multimap<GameType, StandSettings> MODEL_CACHE = MultimapBuilder
            .hashKeys()
            .arrayListValues()
            .build();

    public static final int[] CHAIR_FIRST_LAYER = IntStream.range(0, 10).map(x -> (x * 3) + 1).toArray();
    public static final int[] CHAIR_SECOND_LAYER = IntStream.range(0, 10).map(x -> (x * 3) + 2).toArray();

    private static final List<String> TABLE_SLOTS = Arrays.stream(Slot.values())
            .map(Enum::name)
            .toList();

    @SuppressWarnings("deprecation")
    public Model(
            RoulettePlugin plugin,
            GameType type,
            UUID modelId,
            Location location,
            @Nullable Material carpet,
            @Nullable Material customization,
            @Nullable Integer patternIndex) {
        this.plugin = plugin;
        this.modelUniqueId = modelId;
        this.location = location;
        this.carpetsType = carpet != null && Tag.CARPETS.isTagged(carpet) ? carpet : Material.RED_CARPET;
        this.texture = customization != null ? CustomizationGroup.getByBlock(customization) : CustomizationGroup.getDefaultCustomization();
        this.patternIndex = patternIndex != null && patternIndex < PATTERNS.length ?
                patternIndex :
                PluginUtils.RANDOM.nextInt(PATTERNS.length);
        handleModel(type);
    }

    private void handleModel(GameType type) {
        Collection<StandSettings> settings = MODEL_CACHE.get(type);
        if (settings.isEmpty()) {
            loadFile(type);
            loadModel();
            MODEL_CACHE.putAll(type, Stream.concat(
                            stands.stream().map(PacketStand::getSettings),
                            locations.stream().map(ModelLocation::getSettings))
                    .map(StandSettings::clone)
                    .toList());
            return;
        }

        for (StandSettings setting : settings) {
            Location copy = location.clone().add(PluginUtils.offsetVector(setting.getOffset(), location.getYaw(), location.getPitch()));
            addNew(setting.getPartName(), setting.clone(), copy, setting.getExtraYaw());
        }
    }

    private void loadFile(@NotNull GameType type) {
        configuration = plugin.getGameManager().getModels().computeIfAbsent(type.getFileName(), name -> {
            File file = new File(plugin.getModelFolder(), name);
            return YamlConfiguration.loadConfiguration(file);
        });
    }

    public boolean isInvalidName(String name) {
        return getStandByName(name) != null || name.contains(".") || name.contains(" ");
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

        // These armor stands are only necessary for the location, so it is not necessary to use an armor stand.
        if (settings.getTags().contains("LOCATION") || TABLE_SLOTS.contains(name)) {
            locations.add(new ModelLocation(settings, finalLocation));
            return;
        }

        // Spawn model but don't show it to anyone, we want to apply customizations first.
        stands.add(new PacketStand(plugin, finalLocation, settings));
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
            Vector offset = new Vector(
                    configuration.getDouble(defaultPath + "offset.x"),
                    configuration.getDouble(defaultPath + "offset.y"),
                    configuration.getDouble(defaultPath + "offset.z"));

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
            for (ItemSlot slot : ItemSlot.values()) {
                settings.getEquipment().put(slot, loadEquipment(path, slot.getPath()));
            }

            settings.getTags().addAll(configuration.getStringList("parts." + path + ".tags"));

            addNew(path, settings, location, yaw);
        }
    }

    public enum CustomizationChange {
        TABLE,
        CHAIR_CARPET,
        DECO
    }

    public void updateModel(@Nullable Collection<Player> to, @Nullable CustomizationChange changed) {
        for (PacketStand stand : stands) {
            StandSettings settings = stand.getSettings();
            String name = settings.getPartName();

            Material helmet = null;

            CustomizationChange current = null;
            if (name.startsWith("SIDE")) {
                current = CustomizationChange.TABLE;
                helmet = texture.slab();
            }

            if (name.startsWith("FEET")) {
                current = CustomizationChange.TABLE;
                helmet = texture.block();
            }

            if (name.startsWith("CHAIR")) {
                int index = Integer.parseInt(name.split("_")[1]);
                if (ArrayUtils.contains(CHAIR_FIRST_LAYER, index)) {
                    current = CustomizationChange.TABLE;
                    helmet = texture.block();
                } else if (ArrayUtils.contains(CHAIR_SECOND_LAYER, index)) {
                    current = CustomizationChange.TABLE;
                    helmet = texture.slab();
                } else {
                    current = CustomizationChange.CHAIR_CARPET;
                    helmet = carpetsType;
                    settings.setMarker(true);
                }
            }

            if (helmet != null) {
                settings.getEquipment().put(ItemSlot.HEAD, new ItemStack(helmet));
            }

            // Spawn random decoration.
            if (name.startsWith("DECO")) {
                current = CustomizationChange.DECO;
                String url = getDecoURL(name);

                ItemStack temp = url != null ?
                        PluginUtils.createHead(url) :
                        RoulettePlugin.EMPTY_ITEM;

                settings.getEquipment().put(ItemSlot.MAINHAND, temp);
            }

            if (current == null) continue;

            if (to != null && current == changed) {
                stand.sendMetadata(to);
                stand.sendEquipment(to);
            }
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

    private @NotNull EulerAngle loadAngle(String path, String pose) {
        String defaultPath = "parts." + path + ".pose." + pose;

        if (configuration.get(defaultPath) != null) {
            double x = configuration.getDouble(defaultPath + ".x");
            double y = configuration.getDouble(defaultPath + ".y");
            double z = configuration.getDouble(defaultPath + ".z");
            return new EulerAngle(Math.toRadians(x), Math.toRadians(y), Math.toRadians(z));
        }

        return EulerAngle.ZERO;
    }

    public @Nullable PacketStand getStandByName(String name) {
        for (PacketStand stand : stands) {
            String partName = stand.getSettings().getPartName();
            if (partName != null && partName.equals(name)) return stand;
        }
        return null;
    }

    public @Nullable ModelLocation getLocationByName(String name) {
        for (ModelLocation location : locations) {
            String partName = location.getSettings().getPartName();
            if (partName != null && partName.equals(name)) return location;
        }
        return null;
    }
}