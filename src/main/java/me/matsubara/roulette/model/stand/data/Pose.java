package me.matsubara.roulette.model.stand.data;

import lombok.Getter;
import me.matsubara.roulette.model.stand.PacketStand;
import me.matsubara.roulette.model.stand.StandSettings;
import org.bukkit.util.EulerAngle;

import java.util.function.BiConsumer;
import java.util.function.Function;

public enum Pose {
    HEAD(16, PacketStand.DWO_HEAD_POSE, StandSettings::getHeadPose, StandSettings::setHeadPose),
    BODY(17, PacketStand.DWO_BODY_POSE, StandSettings::getBodyPose, StandSettings::setBodyPose),
    LEFT_ARM(18, PacketStand.DWO_LEFT_ARM_POSE, StandSettings::getLeftArmPose, StandSettings::setLeftArmPose),
    RIGHT_ARM(19, PacketStand.DWO_RIGHT_ARM_POSE, StandSettings::getRightArmPose, StandSettings::setRightArmPose),
    LEFT_LEG(20, PacketStand.DWO_LEFT_LEG_POSE, StandSettings::getLeftLegPose, StandSettings::setLeftLegPose),
    RIGHT_LEG(21, PacketStand.DWO_RIGHT_LEG_POSE, StandSettings::getRightLegPose, StandSettings::setRightLegPose);

    private final @Getter int index;
    private final @Getter Object dwo;
    private final Function<StandSettings, EulerAngle> getter;
    private final BiConsumer<StandSettings, EulerAngle> setter;

    Pose(int index, Object dwo, Function<StandSettings, EulerAngle> getter, BiConsumer<StandSettings, EulerAngle> setter) {
        this.index = index;
        this.dwo = dwo;
        this.getter = getter;
        this.setter = setter;
    }

    public EulerAngle get(StandSettings settings) {
        return getter.apply(settings);
    }

    public void set(StandSettings settings, EulerAngle angle) {
        setter.accept(settings, angle);
    }
}