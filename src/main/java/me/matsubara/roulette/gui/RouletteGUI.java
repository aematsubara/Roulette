package me.matsubara.roulette.gui;

import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.util.ItemBuilder;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public abstract class RouletteGUI implements InventoryHolder {

    protected final RoulettePlugin plugin;
    protected final String name;
    protected final boolean paginated;
    protected int currentPage;
    protected int pages;

    public RouletteGUI(RoulettePlugin plugin, String name) {
        this(plugin, name, false);
    }

    public RouletteGUI(RoulettePlugin plugin, String name, boolean paginated) {
        this.plugin = plugin;
        this.name = name;
        this.paginated = paginated;
    }

    public ItemBuilder getItem(String path) {
        return plugin.getItem(name + ".items." + path);
    }

    public abstract Game getGame();

    public void handle(InventoryClickEvent event) {
        if (paginated) changePage(event);
    }

    public void updateInventory() {

    }

    public boolean isCustomItem(@NotNull ItemStack item, String name) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && Objects.equals(meta.getPersistentDataContainer().get(plugin.getItemIdKey(), PersistentDataType.STRING), name);
    }

    public int isCustomItem(ItemStack item, String first, String second) {
        return isCustomItem(item, first) ? -1 : isCustomItem(item, second) ? 1 : 0;
    }

    protected void closeInventory(@NotNull Player player) {
        runTask(player::closeInventory);
    }

    protected void runTask(Runnable runnable) {
        plugin.getServer().getScheduler().runTask(plugin, runnable);
    }

    protected void changePage(@NotNull InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();

        int direction = isCustomItem(current, "previous", "next");
        if (direction == 0) return;

        boolean shift = event.getClick().isShiftClick();

        if (direction == -1) {
            currentPage = shift ? 0 : currentPage - 1;
        } else {
            currentPage = shift ? pages - 1 : currentPage + 1;
        }

        updateInventory();
    }
}