package me.matsubara.roulette.model.stand;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.util.Vector3f;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

@Getter
@Setter
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

    // Entity poses.
    private Vector3f headPose;
    private Vector3f bodyPose;
    private Vector3f leftArmPose;
    private Vector3f rightArmPose;
    private Vector3f leftLegPose;
    private Vector3f rightLegPose;

    // Entity equipment.
    private Map<EquipmentSlot, ItemStack> equipment = new HashMap<>();

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
        for (Pose pose : Pose.values()) {
            pose.setter.accept(this, Vector3f.zero());
        }
    }

    @NotNull
    public StandSettings clone() {
        try {
            StandSettings copy = (StandSettings) super.clone();

            // Clone tags list.
            copy.setTags(new ArrayList<>(tags));

            // Clone equipment map.
            Map<EquipmentSlot, ItemStack> equipment = new HashMap<>();
            for (Map.Entry<EquipmentSlot, ItemStack> entry : this.equipment.entrySet()) {
                if (entry == null) continue;

                EquipmentSlot slot = entry.getKey();
                if (slot == null) continue;

                ItemStack item = entry.getValue();
                if (item == null) continue;

                equipment.put(slot, item.copy());
            }
            copy.setEquipment(equipment);

            // Clone angles.
            for (Pose pose : Pose.values()) {
                pose.setter.accept(copy, clonePose(pose.getter.apply(this)));
            }

            copy.setCustomName(null);
            return copy;
        } catch (CloneNotSupportedException exception) {
            throw new Error(exception);
        }
    }

    private @NotNull Vector3f clonePose(@NotNull Vector3f pose) {
        return new Vector3f(pose.getX(), pose.getY(), pose.getZ());
    }

    public enum Pose {
        HEAD(16, StandSettings::getHeadPose, StandSettings::setHeadPose),
        BODY(17, StandSettings::getBodyPose, StandSettings::setBodyPose),
        LEFT_ARM(18, StandSettings::getLeftArmPose, StandSettings::setLeftArmPose),
        RIGHT_ARM(19, StandSettings::getRightArmPose, StandSettings::setRightArmPose),
        LEFT_LEG(20, StandSettings::getLeftLegPose, StandSettings::setLeftLegPose),
        RIGHT_LEG(21, StandSettings::getRightLegPose, StandSettings::setRightLegPose);

        private final @Getter int index;
        private final Function<StandSettings, Vector3f> getter;
        private final BiConsumer<StandSettings, Vector3f> setter;

        Pose(int index, Function<StandSettings, Vector3f> getter, BiConsumer<StandSettings, Vector3f> setter) {
            this.index = index;
            this.getter = getter;
            this.setter = setter;
        }

        public Vector3f get(StandSettings settings) {
            return getter.apply(settings);
        }
    }
}