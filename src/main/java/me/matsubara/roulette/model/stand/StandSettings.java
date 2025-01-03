package me.matsubara.roulette.model.stand;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.matsubara.roulette.model.stand.data.ItemSlot;
import me.matsubara.roulette.model.stand.data.Pose;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Accessors(chain = true)
public final class StandSettings implements Cloneable {

    // Model data.
    private String partName;
    private Vector offset;
    private float extraYaw;
    private List<String> tags = new ArrayList<>();

    // Entity settings.
    private boolean invisible;
    private boolean small;
    private boolean basePlate;
    private boolean arms;
    private boolean fire;
    private boolean marker;
    private boolean glow;
    private String customName;
    private boolean customNameVisible;
    private Vector scale;
    private int backgroundColor;

    // Entity poses.
    private EulerAngle headPose;
    private EulerAngle bodyPose;
    private EulerAngle leftArmPose;
    private EulerAngle rightArmPose;
    private EulerAngle leftLegPose;
    private EulerAngle rightLegPose;

    // Entity equipment.
    private Map<ItemSlot, ItemStack> equipment = new HashMap<>();

    public StandSettings() {
        // Default settings.
        this.invisible = false;
        this.small = false;
        this.basePlate = true;
        this.arms = false;
        this.fire = false;
        this.marker = false;
        this.glow = false;
        this.partName = null;
        this.customName = null;
        this.customNameVisible = false;
        this.scale = new Vector(1.0f, 1.0f, 1.0f);
        this.backgroundColor = 1073741824;

        // Default poses.
        for (Pose pose : Pose.values()) {
            pose.set(this, EulerAngle.ZERO);
        }
    }

    @NotNull
    public StandSettings clone() {
        try {
            StandSettings copy = (StandSettings) super.clone();

            // Clone tags list.
            copy.setTags(new ArrayList<>(tags));

            // Clone equipment map.
            Map<ItemSlot, ItemStack> equipment = new HashMap<>();
            for (Map.Entry<ItemSlot, ItemStack> entry : this.equipment.entrySet()) {
                if (entry == null) continue;

                ItemSlot slot = entry.getKey();
                if (slot == null) continue;

                ItemStack item = entry.getValue();
                if (item == null) continue;

                equipment.put(slot, item.clone());
            }
            copy.setEquipment(equipment);

            // Clone angles.
            for (Pose pose : Pose.values()) {
                pose.set(copy, clonePose(pose.get(this)));
            }

            return copy;
        } catch (CloneNotSupportedException exception) {
            throw new Error(exception);
        }
    }

    private @NotNull EulerAngle clonePose(@NotNull EulerAngle pose) {
        return new EulerAngle(pose.getX(), pose.getY(), pose.getZ());
    }
}