package me.matsubara.roulette.gui;

import lombok.Getter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.data.Chip;
import me.matsubara.roulette.manager.ChipManager;
import me.matsubara.roulette.manager.ConfigManager;
import me.matsubara.roulette.util.InventoryUpdate;
import me.matsubara.roulette.util.ItemBuilder;
import org.apache.commons.lang3.ArrayUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public final class GameChipGUI extends RouletteGUI {

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

    // The max number of pages.
    private int pages;

    // We'll keep these here, so we can swap them when clicking on them.
    private final ItemStack enabled;
    private final ItemStack disabled;

    // The slots to show the content.
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16};

    // The slots to show the status of the content.
    private static final int[] STATUS_SLOTS = {19, 20, 21, 22, 23, 24, 25};

    // The slot to put page navigator items and other stuff.
    private static final int[] HOTBAR = {28, 29, 30, 31, 32, 33, 34};

    public GameChipGUI(@NotNull Game game, @NotNull Player player) {
        super("game-chip-menu");
        this.plugin = game.getPlugin();
        this.game = game;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, 45);

        enabled = getItem("enabled").build();
        disabled = getItem("disabled").build();

        player.openInventory(inventory);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::updateInventory);
    }

    public void updateInventory() {
        inventory.clear();

        // Get the list of chips.
        ChipManager chipManager = plugin.getChipManager();
        List<Chip> chips = chipManager.getChips();

        // Page formula.
        pages = (int) (Math.ceil((double) chips.size() / SLOTS.length));

        ItemStack background = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .setDisplayName("&7")
                .build();

        // Set background items.
        for (int i = 0; i < 45; i++) {
            if (ArrayUtils.contains(SLOTS, i)
                    || ArrayUtils.contains(STATUS_SLOTS, i)
                    || ArrayUtils.contains(HOTBAR, i)) continue;
            // Set background item in the current slot from the loop.
            inventory.setItem(i, background);
        }

        if (current > 0) inventory.setItem(28, getItem("previous").build());
        if (current < pages - 1) inventory.setItem(34, getItem("next").build());

        Map<Integer, Integer> slotIndex = new HashMap<>();
        for (int i : SLOTS) {
            slotIndex.put(ArrayUtils.indexOf(SLOTS, i), i);
        }

        int startFrom = current * SLOTS.length;
        boolean isLastPage = current == pages - 1;

        for (int index = 0, aux = startFrom; isLastPage ? (index < chips.size() - startFrom) : (index < SLOTS.length); index++, aux++) {
            Chip chip = chips.get(aux);

            ItemStack chipItem = chipManager.createChipItem(chip, name, false);
            Integer targetIndex = slotIndex.get(index);

            inventory.setItem(targetIndex, chipItem);
            setChipStatusItem(targetIndex + 9, chip);
        }

        // Update inventory title to show the current page.
        InventoryUpdate.updateInventory(player, ConfigManager.Config.GAME_CHIP_MENU_TITLE.asString()
                .replace("%page%", String.valueOf(current + 1))
                .replace("%max%", String.valueOf(pages)));
    }

    public void setChipStatusItem(int slot, Chip chip) {
        inventory.setItem(slot, new ItemBuilder(game.isChipDisabled(chip) ? disabled : enabled)
                .setData(plugin.getChipNameKey(), PersistentDataType.STRING, chip.name())
                .build());
    }

    public void previousPage(boolean isShiftClick) {
        // If shift clicking, go to the first page; otherwise, go to the previous page.
        current = isShiftClick ? 0 : current - 1;
        updateInventory();
    }

    public void nextPage(boolean isShiftClick) {
        // If shift clicking, go to the last page; otherwise, go to the next page.
        current = isShiftClick ? pages - 1 : current + 1;
        updateInventory();
    }
}