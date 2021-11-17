package me.matsubara.roulette.listener.npc;

import com.github.juliarn.npc.event.PlayerNPCInteractEvent;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.gui.GameGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class PlayerNPCInteract implements Listener {

    private final RoulettePlugin plugin;

    public PlayerNPCInteract(RoulettePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerNPCInteract(PlayerNPCInteractEvent event) {
        Player player = event.getPlayer();

        // If for some reason the game is null or othe player is playing, return.
        Game game = plugin.getGameManager().getGameByNPC(event.getNPC());
        if (game == null || game.isPlaying(player)) return;

        // Check if player has permission to edit this game.
        if (!player.hasPermission("roulette.edit")) return;
        if (!game.getOwner().equals(player.getUniqueId()) && !player.hasPermission("roulette.edit.others")) {
            return;
        }

        // For some reason, the event get called 4 times when right cliking an NPC.
        if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof GameGUI)) {
            new GameGUI(plugin, game, player);
        }
    }
}