package me.matsubara.roulette.gui;

import lombok.Getter;
import me.matsubara.roulette.file.Config;
import me.matsubara.roulette.file.Messages;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.data.Chip;
import me.matsubara.roulette.manager.ChipManager;
import me.matsubara.roulette.util.InventoryUpdate;
import me.matsubara.roulette.util.ItemBuilder;
import org.apache.commons.lang3.ArrayUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public final class GameChipGUI extends RouletteGUI {

    // The instance of the game.
    private final Game game;

    // The player viewing this inventory.
    private final Player player;

    // The inventory being used.
    private final Inventory inventory;

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
        super(game.getPlugin(), "game-chip-menu", true);
        this.game = game;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, 45);

        enabled = getItem("enabled").build();
        disabled = getItem("disabled").build();

        player.openInventory(inventory);
        updateInventory();
    }

    @Override
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

        if (currentPage > 0) inventory.setItem(28, getItem("previous").build());
        setMaxBetsItem();
        if (currentPage < pages - 1) inventory.setItem(34, getItem("next").build());

        Map<Integer, Integer> slotIndex = new HashMap<>();
        for (int i : SLOTS) {
            slotIndex.put(ArrayUtils.indexOf(SLOTS, i), i);
        }

        int startFrom = currentPage * SLOTS.length;
        boolean isLastPage = currentPage == pages - 1;

        for (int index = 0, aux = startFrom; isLastPage ? (index < chips.size() - startFrom) : (index < SLOTS.length); index++, aux++) {
            Chip chip = chips.get(aux);

            ItemStack chipItem = chipManager.createChipItem(chip, name, false);
            Integer targetIndex = slotIndex.get(index);

            inventory.setItem(targetIndex, chipItem);
            setChipStatusItem(targetIndex + 9, chip);
        }

        // Update inventory title to show the current page.
        InventoryUpdate.updateInventory(player, Config.GAME_CHIP_MENU_TITLE.asStringTranslated()
                .replace("%page%", String.valueOf(currentPage + 1))
                .replace("%max%", String.valueOf(pages)));
    }

    public void setChipStatusItem(int slot, Chip chip) {
        inventory.setItem(slot, new ItemBuilder(game.isChipDisabled(chip) ? disabled : enabled)
                .setData(plugin.getChipNameKey(), PersistentDataType.STRING, chip.name())
                .build());
    }

    public void setMaxBetsItem() {
        inventory.setItem(31, getItem("max-bets")
                .replace("%max-bets%", game.getMaxBets())
                .build());
    }

    @Override
    public void handle(@NotNull InventoryClickEvent event) {
        super.handle(event);

        Player player = (Player) event.getWhoClicked();

        ItemStack current = event.getCurrentItem();
        if (current == null) return;

        ItemMeta meta = current.getItemMeta();
        if (meta == null) return;

        if (isCustomItem(current, "max-bets")) {
            ClickType click = event.getClick();
            boolean left = click.isLeftClick(), right = click.isRightClick();
            if (!left && !right) return;

            int step = click.isShiftClick() ? 5 : 1;
            game.setMaxBets(game.getMaxBets() + (left ? -step : step));

            setMaxBetsItem();

            // Save data.
            plugin.getGameManager().save(game);
            return;
        }

        String chipName = meta.getPersistentDataContainer().get(plugin.getChipNameKey(), PersistentDataType.STRING);
        if (chipName == null) return;

        Chip chip = plugin.getChipManager().getByName(chipName);
        if (chip == null) return;

        if (game.isChipDisabled(chip)) {
            game.enableChip(chip);
        } else {
            if (plugin.getChipManager().getChipsByGame(game).size() == 1) {
                plugin.getMessages().send(player, Messages.Message.AT_LEAST_ONE_CHIP_REQUIRED);
                closeInventory(player);
                return;
            }
            game.disableChip(chip);
        }

        int slot = event.getRawSlot() + (isCustomItem(current, "chip") ? 9 : 0);
        setChipStatusItem(slot, chip);

        // Update join hologram and save data.
        game.updateJoinHologram(false);
        plugin.getGameManager().save(game);
    }
}