package me.matsubara.roulette.model.stand;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings({"unused", "UnusedReturnValue"})
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

    public boolean isInvisible() {
        return invisible;
    }

    public StandSettings setInvisible(boolean invisible) {
        this.invisible = invisible;
        return this;
    }

    public boolean isSmall() {
        return small;
    }

    public StandSettings setSmall(boolean small) {
        this.small = small;
        return this;
    }

    public boolean hasBasePlate() {
        return basePlate;
    }

    public StandSettings setBasePlate(boolean hasBasePlate) {
        this.basePlate = hasBasePlate;
        return this;
    }

    public boolean hasArms() {
        return arms;
    }

    public StandSettings setArms(boolean hasArms) {
        this.arms = hasArms;
        return this;
    }

    public boolean isOnFire() {
        return fire;
    }

    public StandSettings setOnFire(boolean fire) {
        this.fire = fire;
        return this;
    }

    public boolean isMarker() {
        return marker;
    }

    public StandSettings setMarker(boolean marker) {
        this.marker = marker;
        return this;
    }

    public boolean isGlowing() {
        return glow;
    }

    public StandSettings setGlowing(boolean glow) {
        this.glow = glow;
        return this;
    }

    public String getCustomName() {
        return customName;
    }

    public StandSettings setCustomName(String customName) {
        this.customName = customName;
        return this;
    }

    public boolean isCustomNameVisible() {
        return customNameVisible;
    }

    public StandSettings setCustomNameVisible(boolean customNameVisible) {
        this.customNameVisible = customNameVisible;
        return this;
    }

    public EulerAngle getHeadPose() {
        return headPose;
    }

    public StandSettings setHeadPose(EulerAngle headPose) {
        this.headPose = headPose;
        return this;
    }

    public EulerAngle getBodyPose() {
        return bodyPose;
    }

    public StandSettings setBodyPose(EulerAngle bodyPose) {
        this.bodyPose = bodyPose;
        return this;
    }

    public EulerAngle getLeftArmPose() {
        return leftArmPose;
    }

    public StandSettings setLeftArmPose(EulerAngle leftArmPose) {
        this.leftArmPose = leftArmPose;
        return this;
    }

    public EulerAngle getRightArmPose() {
        return rightArmPose;
    }

    public StandSettings setRightArmPose(EulerAngle rightArmPose) {
        this.rightArmPose = rightArmPose;
        return this;
    }

    public EulerAngle getLeftLegPose() {
        return leftLegPose;
    }

    public StandSettings setLeftLegPose(EulerAngle leftLegPose) {
        this.leftLegPose = leftLegPose;
        return this;
    }

    public EulerAngle getRightLegPose() {
        return rightLegPose;
    }

    public StandSettings setRightLegPose(EulerAngle rightLegPose) {
        this.rightLegPose = rightLegPose;
        return this;
    }

    public ItemStack getHelmet() {
        return helmet;
    }

    public StandSettings setHelmet(ItemStack helmet) {
        this.helmet = helmet;
        return this;
    }

    public ItemStack getChestplate() {
        return chestplate;
    }

    public StandSettings setChestplate(ItemStack chestplate) {
        this.chestplate = chestplate;
        return this;
    }

    public ItemStack getLeggings() {
        return leggings;
    }

    public StandSettings setLeggings(ItemStack leggings) {
        this.leggings = leggings;
        return this;
    }

    public ItemStack getBoots() {
        return boots;
    }

    public StandSettings setBoots(ItemStack boots) {
        this.boots = boots;
        return this;
    }

    public ItemStack getMainHand() {
        return mainHand;
    }

    public StandSettings setMainHand(ItemStack mainHand) {
        this.mainHand = mainHand;
        return this;
    }

    public ItemStack getOffHand() {
        return offHand;
    }

    public StandSettings setOffHand(ItemStack offHand) {
        this.offHand = offHand;
        return this;
    }

    public boolean hasEquipment() {
        return !isArrayNull(helmet, chestplate, leggings, boots, mainHand, offHand);
    }

    private boolean isArrayNull(Object... objects) {
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