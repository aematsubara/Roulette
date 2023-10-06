package me.matsubara.roulette.model.stand;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

@Getter
@Setter
public final class StandSettings implements Cloneable {

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

    // Entity inventory.
    private ItemStack helmet;
    private ItemStack chestplate;
    private ItemStack leggings;
    private ItemStack boots;
    private ItemStack mainHand;
    private ItemStack offHand;

    public StandSettings() {
        // Default settings.
        this.invisible = false;
        this.small = false;
        this.basePlate = true;
        this.arms = false;
        this.fire = false;
        this.marker = false;
        this.glow = false;
        this.customName = null;
        this.customNameVisible = false;

        // Default poses.
        this.headPose = EulerAngle.ZERO;
        this.bodyPose = EulerAngle.ZERO;
        this.leftArmPose = EulerAngle.ZERO;
        this.rightArmPose = EulerAngle.ZERO;
        this.leftLegPose = EulerAngle.ZERO;
        this.rightLegPose = EulerAngle.ZERO;

        // Default equipment.
        this.helmet = null;
        this.chestplate = null;
        this.leggings = null;
        this.boots = null;
        this.mainHand = null;
        this.offHand = null;
    }

    public boolean hasEquipment() {
        return !isArrayNull(helmet, chestplate, leggings, boots, mainHand, offHand);
    }

    @Contract(pure = true)
    private boolean isArrayNull(Object @NotNull ... objects) {
        for (Object object : objects) {
            if (object != null) return false;
        }
        return true;
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