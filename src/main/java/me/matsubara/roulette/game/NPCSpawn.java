package me.matsubara.roulette.game;

import com.comphenix.protocol.wrappers.EnumWrappers;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.npc.NPC;
import me.matsubara.roulette.npc.SpawnCustomizer;
import me.matsubara.roulette.npc.modifier.AnimationModifier;
import me.matsubara.roulette.npc.modifier.MetadataModifier;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class NPCSpawn implements SpawnCustomizer {

    private final RoulettePlugin plugin;
    private final Game game;
    private final Location npcLocation;

    public NPCSpawn(Game game, Location npcLocation) {
        this.plugin = (this.game = game).getPlugin();
        this.npcLocation = npcLocation;
    }

    @Override
    public void handleSpawn(@NotNull NPC npc, @NotNull Player player) {
        npc.rotation().queueRotate(npcLocation.getYaw(), npcLocation.getPitch()).send(player);

        // Set item (ball) in main hand.
        GameState state = game.getState();
        if (!state.isSpinning() && !state.isEnding()) {
            npc.equipment().queue(EnumWrappers.ItemSlot.MAINHAND, plugin.getConfigManager().getBall()).send(player);
        }

        // Show skin layers.
        npc.metadata().queue(MetadataModifier.EntityMetadata.SKIN_LAYERS, true).send(player);

        // Swing main hand to rotate body with the head rotation.
        npc.animation().queue(AnimationModifier.EntityAnimation.SWING_MAIN_ARM).send(player);
    }
}