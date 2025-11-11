package me.matsubara.roulette.listener;

import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.data.Bet;
import me.matsubara.roulette.gui.BetsGUI;
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

        if (inventory.getHolder() instanceof GameGUI gui) {
            int taskId = gui.getTaskId();
            if (taskId != -1) plugin.getServer().getScheduler().cancelTask(taskId);
            return;
        }

        Game game = plugin.getGameManager().getGameByPlayer(player);
        if (game == null) return;

        Bet bet = game.getSelectedBet(player);
        if (bet == null) return;

        if (inventory.getHolder() instanceof ChipGUI chip) {
            // The player already selected a chip.
            if (bet.hasChip()) return;

            // We only want to force the first bet.
            if (chip.isNewBet()) return;

            // The time for selecting a chip is over.
            if (game.getState().isSpinning()) return;

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                // Ignore bet-all inventory.
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof ConfirmGUI confirm) {
                    if (confirm.getType() == ConfirmGUI.ConfirmType.BET_ALL) return;
                }

                // The player closed the chip menu but didn't select a chip, re-open menu.
                player.openInventory(chip.getInventory());
                chip.updateInventory();
            }, 2L);
            return;
        }

        if (!(inventory.getHolder() instanceof ConfirmGUI gui)) return;

        ConfirmGUI.ConfirmType type = gui.getType();
        if (type.isLeave()) return;

        // The time for selecting a chip is over.
        if (game.getState().isSpinning()) return;

        if (type.isDone()) {
            // The player is done, no need to re-open the bet GUI.
            if (game.isDone(player)) return;

            runTask(() -> new BetsGUI(game, player, gui.getPreviousPage()));
            return;
        }

        // The player selected a chip, no need to re-open the chip GUI.
        if (bet.hasChip()) return;

        // The player closed the confirm bet-all menu but didn't confirmed, re-open the chip menu.
        runTask(() -> {
            ChipGUI chip = gui.getSourceGUI();
            player.openInventory(chip.getInventory());
            chip.updateInventory();
        });
    }

    private void runTask(Runnable runnable) {
        plugin.getServer().getScheduler().runTask(plugin, runnable);
    }
}