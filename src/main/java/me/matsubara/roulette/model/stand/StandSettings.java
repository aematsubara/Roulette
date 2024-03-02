package me.matsubara.roulette.model.stand;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@Getter
@Setter
public final class StandSettings implements Cloneable {

    // Model data.
    private String partName;
    private double xOffset;
    private double yOffset;
    private double zOffset;
    private float extraYaw;
    private final List<String> tags = new ArrayList<>();

    private double externalX;
    private double externalZ;

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

    // Entity poses.
    private EulerAngle headPose;
    private EulerAngle bodyPose;
    private EulerAngle leftArmPose;
    private EulerAngle rightArmPose;
    private EulerAngle leftLegPose;
    private EulerAngle rightLegPose;

    // Entity equipment.
    private final Map<PacketStand.ItemSlot, ItemStack> equipment = new HashMap<>();

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

        // Default poses.
        this.headPose = EulerAngle.ZERO;
        this.bodyPose = EulerAngle.ZERO;
        this.leftArmPose = EulerAngle.ZERO;
        this.rightArmPose = EulerAngle.ZERO;
        this.leftLegPose = EulerAngle.ZERO;
        this.rightLegPose = EulerAngle.ZERO;
    }

    public boolean hasEquipment() {
        return equipment.values().stream().anyMatch(Objects::nonNull);
    }

    @NotNull
    public StandSettings clone() {
        try {
            StandSettings copy = (StandSettings) super.clone();
            copy.setCustomName(null);
            return copy;
        } catch (CloneNotSupportedException exception) {
            throw new Error(exception);
        }
    }
}