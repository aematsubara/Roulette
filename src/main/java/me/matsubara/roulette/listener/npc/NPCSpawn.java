package me.matsubara.roulette.listener.npc;

import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityAnimation;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.GameState;
import me.matsubara.roulette.npc.NPC;
import me.matsubara.roulette.npc.SpawnCustomizer;
import me.matsubara.roulette.npc.modifier.MetadataModifier;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class NPCSpawn implements SpawnCustomizer {

    private final Game game;

    public NPCSpawn(Game game) {
        this.game = game;
    }

    @Override
    public void handleSpawn(@NotNull NPC npc, @NotNull Player player) {
        npc.lookAtDefaultLocation(player);

        // Set item (ball) in the main hand.
        GameState state = game.getState();
        if (!state.isSpinning() && !state.isEnding()) {
            npc.equipment().queue(EquipmentSlot.MAIN_HAND, game.getPlugin().getBall()).send(player);
        }

        // Swing the main hand to rotate the body with the head rotation.
        npc.animation().queue(WrapperPlayServerEntityAnimation.EntityAnimationType.SWING_MAIN_ARM).send(player);

        MetadataModifier metadata = npc.metadata();

        // Show skin layers.
        metadata.queue(MetadataModifier.EntityMetadata.SKIN_LAYERS, true);

        // Toggle parrot visibility.
        npc.toggleParrotVisibility(metadata);

        // Send metadata after creating the data.
        metadata.send(player);
    }
}