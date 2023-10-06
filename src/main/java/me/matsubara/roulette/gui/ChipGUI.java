package me.matsubara.roulette.gui;

import com.cryptomorin.xseries.XMaterial;
import lombok.Getter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.data.Chip;
import me.matsubara.roulette.manager.ConfigManager;
import me.matsubara.roulette.util.InventoryUpdate;
import me.matsubara.roulette.util.ItemBuilder;
import me.matsubara.roulette.util.PluginUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
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
    private int current;

    // The max amount of pages.
    private int pages;

    // The slots to show the content.
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16};

    // The slot to put page navigator items and other stuff.
    private static final int[] HOTBAR = {19, 20, 21, 22, 23, 24, 25};

    // Inventory content.
    private final ItemStack background, previous, money, betAll, exit, next;

    public ChipGUI(Game game, Player player) {
        this(game, player, 0);
    }

    public ChipGUI(@NotNull Game game, Player player, int current) {
        this.plugin = game.getPlugin();
        this.game = game;
        this.player = player;
        this.inventory = plugin.getServer().createInventory(this, 36);
        this.current = current;

        ConfigManager configManager = plugin.getConfigManager();

        background = new ItemBuilder(XMaterial.GRAY_STAINED_GLASS_PANE.parseItem()).setDisplayName("&7").build();
        previous = configManager.getItem("shop", "previous", null);

        money = configManager.getItem("shop", "money", PluginUtils.format(plugin.getEconomy().getBalance(player)));
        betAll = configManager.getItem("shop", "bet-all", null);

        exit = configManager.getItem("shop", "exit", null);
        next = configManager.getItem("shop", "next", null);

        player.openInventory(inventory);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::updateInventory);
    }

    public void updateInventory() {
        inventory.clear();

        // Get list of chips.
        List<Chip> chips = plugin.getChipManager().getChips();

        // Page formula.
        pages = (int) (Math.ceil((double) chips.size() / SLOTS.length));

        // Set background items, except the last, since we're putting the close item there.
        for (int i = 0; i < 35; i++) {
            if (ArrayUtils.contains(SLOTS, i) || ArrayUtils.contains(HOTBAR, i)) continue;
            // Set background item in the current slot from the loop.
            inventory.setItem(i, background);
        }

        // If the current page isn't 0 (first page), show the previous page item.
        if (current > 0) inventory.setItem(19, previous);

        // Set money item.
        inventory.setItem(22, money);

        // Set bet all item.
        if (game.isBetAll()) inventory.setItem(23, betAll);

        // If the current page isn't the last one, show the next page item.
        if (current < pages - 1) inventory.setItem(25, next);

        // Set close inventory item.
        inventory.setItem(35, exit);

        // Assigning slots.
        Map<Integer, Integer> slotIndex = new HashMap<>();
        for (int i : SLOTS) {
            slotIndex.put(ArrayUtils.indexOf(SLOTS, i), i);
        }

        // Where to start.
        int startFrom = current * SLOTS.length;

        boolean isLastPage = current == pages - 1;

        for (int index = 0, aux = startFrom; isLastPage ? (index < chips.size() - startFrom) : (index < SLOTS.length); index++, aux++) {
            Chip chip = chips.get(aux);

            double price = chip.getPrice();
            String displayName = chip.getDisplayName() != null ? chip.getDisplayName() : plugin.getConfigManager().getChipDisplayName(price);
            List<String> lore = chip.getLore() != null ? chip.getLore() : plugin.getConfigManager().getChipLore();

            inventory.setItem(slotIndex.get(index), new ItemBuilder(chip.getUrl(), true)
                    .setDisplayName(displayName)
                    .setLore(lore)
                    .modifyNBT("chipName", chip.getName())
                    .build());
        }

        // Update inventory title to show the current page.
        InventoryUpdate.updateInventory(player, ConfigManager.Config.SHOP_TITLE.asString()
                .replace("%page%", String.valueOf(current + 1))
                .replace("%max%", String.valueOf(pages)));
    }

    public void previousPage(boolean isShiftClick) {
        // If is shift click, go to the first page.
        if (isShiftClick) {
            current = 0;
            updateInventory();
            return;
        }

        // Go to the previous page.
        current--;
        updateInventory();
    }

    public void nextPage(boolean isShiftClick) {
        // If is shift click, go to the last page.
        if (isShiftClick) {
            current = pages - 1;
            updateInventory();
            return;
        }

        // Go to the next page.
        current++;
        updateInventory();
    }
}