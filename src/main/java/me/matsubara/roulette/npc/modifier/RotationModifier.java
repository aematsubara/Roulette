package me.matsubara.roulette.npc.modifier;

import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;
import me.matsubara.roulette.npc.NPC;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

public class RotationModifier extends NPCModifier {

    public RotationModifier(NPC npc) {
        super(npc);
    }

    @NotNull
    public RotationModifier queueLookAt(@NotNull Location location) {
        Location npcLocation = npc.getLocation();
        double xDifference = location.getX() - npcLocation.getX();
        double yDifference = location.getY() - npcLocation.getY();
        double zDifference = location.getZ() - npcLocation.getZ();

        double distance = Math.sqrt(Math.pow(xDifference, 2) + Math.pow(yDifference, 2) + Math.pow(zDifference, 2));

        float yaw = (float) (-Math.atan2(xDifference, zDifference) / Math.PI * 180.0d);
        float pitch = (float) (-Math.asin(yDifference / distance) / Math.PI * 180.0d);
        return queueBodyRotation(yaw < 0 ? yaw + 360 : yaw, pitch);
    }

    public RotationModifier queueBodyRotation(float yaw, float pitch) {
        queueInstantly((npc, player) -> new WrapperPlayServerEntityHeadLook(npc.getEntityId(), yaw));
        queueInstantly((npc, player) -> new WrapperPlayServerEntityRelativeMoveAndRotation(
                npc.getEntityId(),
                0.0d,
                0.0d,
                0.0d,
                yaw,
                pitch,
                true));
        return this;
    }
}