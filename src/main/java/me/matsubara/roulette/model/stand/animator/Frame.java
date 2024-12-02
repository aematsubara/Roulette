package me.matsubara.roulette.model.stand.animator;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.util.EulerAngle;
import org.jetbrains.annotations.NotNull;

@Getter
@Setter
public class Frame {

    private int id;
    private float x;
    private float y;
    private float z;
    private float rotation;

    private EulerAngle head;
    private EulerAngle leftArm;
    private EulerAngle rightArm;
    private EulerAngle leftLeg;
    private EulerAngle rightLeg;

    public Frame multiply(float multiplier, int id) {
        Frame frame = new Frame();
        frame.id = id;

        frame.x *= multiplier;
        frame.y *= multiplier;
        frame.z *= multiplier;
        frame.rotation *= multiplier;

        frame.head = multiply(head, multiplier);
        frame.leftArm = multiply(leftArm, multiplier);
        frame.rightArm = multiply(rightArm, multiplier);
        frame.leftLeg = multiply(leftLeg, multiplier);
        frame.rightLeg = multiply(rightLeg, multiplier);

        return frame;
    }

    private @NotNull EulerAngle multiply(@NotNull EulerAngle angle, float multiplier) {
        return new EulerAngle(angle.getX() * multiplier, angle.getY() * multiplier, angle.getZ() * multiplier);
    }

    public Frame add(@NotNull Frame add, int id) {
        Frame frame = new Frame();
        frame.id = id;

        frame.x += add.x;
        frame.y += add.y;
        frame.z += add.z;
        frame.rotation += add.rotation;

        frame.head = add(head, add.head);
        frame.leftArm = add(leftArm, add.leftArm);
        frame.rightArm = add(rightArm, add.rightArm);
        frame.leftLeg = add(leftLeg, add.leftLeg);
        frame.rightLeg = add(rightLeg, add.rightLeg);

        return frame;
    }

    private @NotNull EulerAngle add(@NotNull EulerAngle angle, @NotNull EulerAngle add) {
        return new EulerAngle(angle.getX() + add.getX(), angle.getY() + add.getY(), angle.getZ() + add.getZ());
    }
}
