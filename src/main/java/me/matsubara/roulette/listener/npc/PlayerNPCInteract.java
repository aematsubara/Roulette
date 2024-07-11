package me.matsubara.roulette.listener.npc;

import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.InteractionHand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.gui.GameGUI;
import me.matsubara.roulette.npc.NPC;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public final class PlayerNPCInteract extends SimplePacketListenerAbstract {

    private final RoulettePlugin plugin;

    public PlayerNPCInteract(RoulettePlugin plugin) {
        super(PacketListenerPriority.HIGHEST);
        this.plugin = plugin;
    }

    @Override
    public void onPacketPlayReceive(@NotNull PacketPlayReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);

        InteractionHand hand = wrapper.getHand();
        if (hand != InteractionHand.MAIN_HAND) return;

        WrapperPlayClientInteractEntity.InteractAction action = wrapper.getAction();
        if (action != WrapperPlayClientInteractEntity.InteractAction.INTERACT) return;

        // We only want to open the game editor IF the player right-clicks the NPC.

        int entityId = wrapper.getEntityId();

        Optional<NPC> npc = plugin.getNpcPool().getNPC(entityId);
        if (npc.isEmpty()) return;

        // If, for some reason, the game is null, or the player is playing, return.
        Game game = plugin.getGameManager().getGameByNPC(npc.get());
        if (game == null || game.isPlaying(player)) return;

        // Check if player has permission to edit this game.
        if (!player.hasPermission("roulette.edit")) return;
        if (!game.getOwner().equals(player.getUniqueId()) && !player.hasPermission("roulette.edit.others")) {
            return;
        }

        // For some reason, the event get called 4 times when right cliking an NPC.
        if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof GameGUI)) {
            plugin.getServer().getScheduler().runTask(plugin, () -> new GameGUI(plugin, game, player));
        }
    }
}