package me.matsubara.roulette.npc.modifier;

import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityAnimation;
import me.matsubara.roulette.npc.NPC;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * A modifier for various animations a npc can play.
 */
public class AnimationModifier extends NPCModifier {

    /**
     * Creates a new modifier.
     *
     * @param npc The npc this modifier is for.
     * @see NPC#animation()
     */
    @ApiStatus.Internal
    public AnimationModifier(@NotNull NPC npc) {
        super(npc);
    }

    /**
     * Queues the animation to be played.
     *
     * @param animation The animation to play.
     * @return The same instance of this class, for chaining.
     */
    @NotNull
    public AnimationModifier queue(WrapperPlayServerEntityAnimation.EntityAnimationType animation) {
        super.queueInstantly((targetNpc, target) -> new WrapperPlayServerEntityAnimation(targetNpc.getEntityId(), animation));
        return this;
    }
}