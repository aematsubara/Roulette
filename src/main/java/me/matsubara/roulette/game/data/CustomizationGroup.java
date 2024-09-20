package me.matsubara.roulette.game.data;

import me.matsubara.roulette.util.PluginUtils;
import org.bukkit.Material;
import org.bukkit.block.data.type.Slab;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public record CustomizationGroup(Material block, Material slab) {

    // Items allowed for customization.
    public static final List<CustomizationGroup> GROUPS = new ArrayList<>();

    // These should be ignored.
    private static final Set<Material> IGNORE_SLAB = Set.of(
            Material.PETRIFIED_OAK_SLAB,
            Material.STONE_SLAB,
            Material.GRANITE_SLAB,
            Material.ANDESITE_SLAB);

    // The index of the default customization (spruce).
    private static int DEFAULT_INDEX = 0;

    public static CustomizationGroup getByBlock(Material block) {
        for (CustomizationGroup group : GROUPS) {
            if (group.block() == block) return group;
        }
        return getDefaultCustomization();
    }

    public static CustomizationGroup getDefaultCustomization() {
        return GROUPS.get(DEFAULT_INDEX);
    }

    static {
        GROUPS.add(new CustomizationGroup(Material.PURPUR_BLOCK, Material.PURPUR_SLAB));
        GROUPS.add(new CustomizationGroup(Material.DEEPSLATE_TILES, Material.DEEPSLATE_TILE_SLAB));
        if (PluginUtils.getOrNull(Material.class, "BAMBOO_BLOCK") != null) {
            GROUPS.add(new CustomizationGroup(Material.BAMBOO_BLOCK, Material.BAMBOO_SLAB));
        }
        GROUPS.add(new CustomizationGroup(Material.BRICKS, Material.BRICK_SLAB));
        GROUPS.add(new CustomizationGroup(Material.QUARTZ_BLOCK, Material.QUARTZ_SLAB));

        for (Material slab : Material.values()) {
            // Ignore legacies.
            @SuppressWarnings("deprecation") boolean legacy = slab.isLegacy();
            if (legacy) continue;

            // Ignore old oak slab.
            if (IGNORE_SLAB.contains(slab)) continue;

            // Ignore the already existing.
            if (GROUPS.stream()
                    .map(CustomizationGroup::slab)
                    .anyMatch(material -> material == slab)) continue;

            // Ignore non-slabs.
            try {
                if (!(slab.createBlockData() instanceof Slab)) continue;
            } catch (Exception exception) {
                continue;
            }

            String slabName = slab.name();
            String blockName = slabName.replace("_SLAB", "") + (slabName.contains("_BRICK_") ? "S" : "");

            Material block = PluginUtils.getOrNull(Material.class, blockName);
            Material planks = PluginUtils.getOrNull(Material.class, blockName + "_PLANKS");
            if (block == null && planks == null) continue;

            Material origin = block != null ? block : planks;
            if (origin == Material.SPRUCE_PLANKS) DEFAULT_INDEX = GROUPS.size();

            GROUPS.add(new CustomizationGroup(origin, slab));
        }
    }
}
