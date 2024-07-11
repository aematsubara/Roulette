package me.matsubara.roulette.gui;

import lombok.Getter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.data.Chip;
import me.matsubara.roulette.manager.ConfigManager;
import me.matsubara.roulette.util.InventoryUpdate;
import me.matsubara.roulette.util.ItemBuilder;
import me.matsubara.roulette.util.PluginUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public final class ChipGUI implements InventoryHolder, RouletteGUI {

    // The instance of the plugin.
    private final RoulettePlugin plugin;

    // The instance of the game.
    private final Game game;

    // The player viewing this inventory.
    private final Player player;

    // The inventory being used.
    private final Inventory inventory;

    // The current page.
    private int currentPage;

    // The max number of pages.
    private int pages;

    // The slots to show the content.
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16};

    // The slot to put page navigator items and other stuff.
    private static final int[] HOTBAR = {19, 20, 21, 22, 23, 24, 25};

    public ChipGUI(Game game, Player player) {
        this(game, player, 0);
    }

    public ChipGUI(@NotNull Game game, @NotNull Player player, int currentPage) {
        this.plugin = game.getPlugin();
        this.game = game;
        this.player = player;
        this.inventory = plugin.getServer().createInventory(this, 36);
        this.currentPage = currentPage;

        player.openInventory(inventory);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::updateInventory);
    }

    public void updateInventory() {
        inventory.clear();

        // Get the list of chips.
        List<Chip> chips = plugin.getChipManager().getChips();

        // Page formula.
        pages = (int) (Math.ceil((double) chips.size() / SLOTS.length));

        ItemStack background = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .setDisplayName("&7")
                .build();

        // Set background items, except the last, since we're putting the close item there.
        for (int i = 0; i < 35; i++) {
            if (ArrayUtils.contains(SLOTS, i) || ArrayUtils.contains(HOTBAR, i)) continue;
            // Set background item in the current slot from the loop.
            inventory.setItem(i, background);
        }

        // If the current page isn't 0 (first page), show the previous page item.
        if (currentPage > 0) inventory.setItem(19, plugin.getItem("shop.previous").build());

        // Set money item.
        inventory.setItem(22, plugin.getItem("shop.money").replace("%money%", PluginUtils.format(plugin.getEconomy().getBalance(player))).build());

        // Set bet all item.
        if (game.isBetAll()) inventory.setItem(23, plugin.getItem("shop.bet-all").build());

        // If the current page isn't the last one, show the next page item.
        if (currentPage < pages - 1) inventory.setItem(25, plugin.getItem("shop.next").build());

        // Set close inventory item.
        inventory.setItem(35, plugin.getItem("shop.exit").build());

        // Assigning slots.
        Map<Integer, Integer> slotIndex = new HashMap<>();
        for (int i : SLOTS) {
            slotIndex.put(ArrayUtils.indexOf(SLOTS, i), i);
        }

        // Where to start.
        int startFrom = currentPage * SLOTS.length;

        boolean isLastPage = currentPage == pages - 1;

        for (int index = 0, aux = startFrom; isLastPage ? (index < chips.size() - startFrom) : (index < SLOTS.length); index++, aux++) {
            Chip chip = chips.get(aux);

            ItemBuilder item = plugin.getItem("shop.chip");

            String displayName = chip.getDisplayName();
            if (displayName != null) item.setDisplayName(displayName);

            List<String> lore = chip.getLore();
            if (lore != null) item.setLore(lore);

            inventory.setItem(slotIndex.get(index), item
                    .setHead(chip.getUrl(), true)
                    .replace("%money%", PluginUtils.format(chip.getPrice()))
                    .setData(plugin.getChipNameKey(), PersistentDataType.STRING, chip.getName())
                    .build());
        }

        // Update inventory title to show the current page.
        InventoryUpdate.updateInventory(player, ConfigManager.Config.SHOP_TITLE.asString()
                .replace("%page%", String.valueOf(currentPage + 1))
                .replace("%max%", String.valueOf(pages)));
    }

    public void previousPage(boolean isShiftClick) {
        currentPage = isShiftClick ? 0 : currentPage - 1;
        updateInventory();
    }

    public void nextPage(boolean isShiftClick) {
        currentPage = isShiftClick ? pages - 1 : currentPage + 1;
        updateInventory();
    }
}