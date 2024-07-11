package me.matsubara.roulette.npc.modifier;

import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import me.matsubara.roulette.npc.NPC;

public class RotationModifier extends NPCModifier {

    public RotationModifier(NPC npc) {
        super(npc);
    }

    public RotationModifier queueHeadRotation(float yaw) {
        queueInstantly((npc, player) -> new WrapperPlayServerEntityHeadLook(npc.getEntityId(), yaw));
        return this;
    }
}