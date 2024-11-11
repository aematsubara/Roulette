package me.matsubara.roulette.model.stand.animator;

import com.github.retrooper.packetevents.util.Vector3f;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

@Getter
@Setter
public class Frame {

    private int id;
    private float x;
    private float y;
    private float z;
    private float rotation;

    private Vector3f head;
    private Vector3f leftArm;
    private Vector3f rightArm;
    private Vector3f leftLeg;
    private Vector3f rightLeg;

    public Frame multiply(float multiplier, int id) {
        Frame frame = new Frame();
        frame.id = id;

        frame.x *= multiplier;
        frame.y *= multiplier;
        frame.z *= multiplier;
        frame.rotation *= multiplier;

        frame.head = head.multiply(multiplier);
        frame.leftArm = leftArm.multiply(multiplier);
        frame.rightArm = rightArm.multiply(multiplier);
        frame.leftLeg = leftLeg.multiply(multiplier);
        frame.rightLeg = rightLeg.multiply(multiplier);

        return frame;
    }

    public Frame add(@NotNull Frame add, int id) {
        Frame frame = new Frame();
        frame.id = id;

        frame.x += add.x;
        frame.y += add.y;
        frame.z += add.z;
        frame.rotation += add.rotation;

        frame.head = head.add(add.head);
        frame.leftArm = leftArm.add(add.leftArm);
        frame.rightArm = rightArm.add(add.rightArm);
        frame.leftLeg = leftLeg.add(add.leftLeg);
        frame.rightLeg = rightLeg.add(add.rightLeg);

        return frame;
    }
}
