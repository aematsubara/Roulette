package me.matsubara.roulette.listener;

import com.cryptomorin.xseries.XSound;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.file.Config;
import me.matsubara.roulette.gui.RouletteGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public final class InventoryClick implements Listener {

    private final RoulettePlugin plugin;

    public InventoryClick(RoulettePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof RouletteGUI)) return;

        if (event.getRawSlots().stream().noneMatch(integer -> integer < holder.getInventory().getSize())) return;

        if (event.getRawSlots().size() == 1) {
            InventoryClickEvent clickEvent = new InventoryClickEvent(
                    event.getView(),
                    InventoryType.SlotType.CONTAINER,
                    event.getRawSlots().iterator().next(),
                    ClickType.LEFT,
                    InventoryAction.PICKUP_ONE);
            plugin.getServer().getPluginManager().callEvent(clickEvent);
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory inventory = event.getClickedInventory();
        if (inventory == null) return;

        // Prevent moving items from player inventory to custom inventories by shift-clicking.
        InventoryHolder tempHolder = event.getView().getTopInventory().getHolder();
        if (inventory.getType() == InventoryType.PLAYER
                && event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                && tempHolder instanceof RouletteGUI) {
            event.setCancelled(true);
            return;
        }

        InventoryHolder holder = inventory.getHolder();
        if (!(holder instanceof RouletteGUI gui)) return;

        event.setCancelled(true);

        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        // Handle.
        XSound.play(Config.SOUND_CLICK.asString(), temp -> temp.forPlayers(player).play());
        gui.handle(event);
    }
}