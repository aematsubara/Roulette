package me.matsubara.roulette.listener;

import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.gui.ChipGUI;
import me.matsubara.roulette.gui.ConfirmGUI;
import me.matsubara.roulette.gui.GameGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

public final class InventoryClose implements Listener {

    private final RoulettePlugin plugin;

    public InventoryClose(RoulettePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClose(@NotNull InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        Inventory inventory = event.getInventory();

        if (inventory.getHolder() instanceof GameGUI) {
            int taskId = ((GameGUI) inventory.getHolder()).getTaskId();
            if (taskId != -1) {
                plugin.getServer().getScheduler().cancelTask(taskId);
            }
            return;
        }

        Game game = plugin.getGameManager().getGameByPlayer(player);
        if (game == null) return;

        if (inventory.getHolder() instanceof ChipGUI) {
            if (game.getPlayers().get(player).hasChip()) return;

            // The time for selecting a chip is over.
            if (game.getState().isSpinning()) return;

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof ConfirmGUI) return;

                int page = ((ChipGUI) inventory.getHolder()).getCurrentPage();
                runTask(() -> new ChipGUI(game, player, page));
            }, 2L);
            return;
        }

        if (!(inventory.getHolder() instanceof ConfirmGUI gui)) return;

        if (gui.getType().isLeave()) return;

        // Is bet all confirm gui.
        if (game.getPlayers().get(player).hasChip()) return;

        // The time for selecting a chip is over.
        if (game.getState().isSpinning()) return;

        runTask(() -> new ChipGUI(game, player, gui.getPreviousPage()));
    }

    private void runTask(Runnable runnable) {
        plugin.getServer().getScheduler().runTask(plugin, runnable);
    }
}