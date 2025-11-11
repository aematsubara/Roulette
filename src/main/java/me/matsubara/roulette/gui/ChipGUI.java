package me.matsubara.roulette.gui;

import lombok.Getter;
import me.matsubara.roulette.file.Config;
import me.matsubara.roulette.file.Messages;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.data.Chip;
import me.matsubara.roulette.manager.ChipManager;
import me.matsubara.roulette.util.InventoryUpdate;
import me.matsubara.roulette.util.ItemBuilder;
import me.matsubara.roulette.util.PluginUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
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
public final class ChipGUI extends RouletteGUI {

    // The instance of the game.
    private final Game game;

    // The player viewing this inventory.
    private final Player player;

    // The inventory being used.
    private final Inventory inventory;

    // Whether the chip being selected is for a new bet.
    private final boolean isNewBet;

    // The slots to show the content.
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16};

    // The slot to put page navigator items and other stuff.
    private static final int[] HOTBAR = {19, 20, 21, 22, 23, 24, 25};

    public ChipGUI(@NotNull Game game, @NotNull Player player, boolean isNewBet) {
        super(game.getPlugin(), "chip-menu", true);
        this.game = game;
        this.player = player;
        this.isNewBet = isNewBet;
        this.inventory = plugin.getServer().createInventory(this, 36);

        player.openInventory(inventory);
        updateInventory();
    }

    @Override
    public void updateInventory() {
        inventory.clear();

        // Get the list of chips.
        ChipManager chipManager = plugin.getChipManager();
        List<Chip> chips = chipManager.getChipsByGame(game);

        // Page formula.
        pages = (int) (Math.ceil((double) chips.size() / SLOTS.length));

        ItemStack background = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .setDisplayName("&7")
                .build();

        // Set background items.
        for (int i = 0; i < 36; i++) {
            if (ArrayUtils.contains(SLOTS, i) || ArrayUtils.contains(HOTBAR, i)) continue;
            // Set background item in the current slot from the loop.
            inventory.setItem(i, background);
        }

        // If the current page isn't 0 (first page), show the previous page item.
        if (currentPage > 0) inventory.setItem(19, getItem("previous").build());

        // Set money item.
        inventory.setItem(22, getItem("money")
                .replace("%money%", PluginUtils.format(plugin.getEconomyExtension().getBalance(player)))
                .build());

        // Set bet all item.
        if (game.isBetAllEnabled()) inventory.setItem(23, getItem("bet-all").build());

        // If the current page isn't the last one, show the next page item.
        if (currentPage < pages - 1) inventory.setItem(25, getItem("next").build());

        // Set quit game item, only if it's the first bet.
        if (!isNewBet) inventory.setItem(35, getItem("exit").build());

        // Assigning slots.
        Map<Integer, Integer> slotIndex = new HashMap<>();
        for (int i : SLOTS) {
            slotIndex.put(ArrayUtils.indexOf(SLOTS, i), i);
        }

        // Where to start.
        int startFrom = currentPage * SLOTS.length;

        boolean isLastPage = currentPage == pages - 1;

        for (int index = 0, aux = startFrom; isLastPage ? (index < chips.size() - startFrom) : (index < SLOTS.length); index++, aux++) {
            inventory.setItem(slotIndex.get(index), chipManager.createChipItem(chips.get(aux), name, true));
        }

        // Update inventory title to show the current page.
        InventoryUpdate.updateInventory(player, Config.CHIP_MENU_TITLE.asStringTranslated()
                .replace("%page%", String.valueOf(currentPage + 1))
                .replace("%max%", String.valueOf(pages)));
    }

    @Override
    public void handle(@NotNull InventoryClickEvent event) {
        super.handle(event);

        Player player = (Player) event.getWhoClicked();
        Messages messages = plugin.getMessages();

        ItemStack current = event.getCurrentItem();
        if (current == null) return;

        if (isCustomItem(current, "bet-all")) {
            // Open confirm gui.
            runTask(() -> {
                ConfirmGUI gui = new ConfirmGUI(game, player, ConfirmGUI.ConfirmType.BET_ALL);
                gui.setSourceGUI(this);
                gui.setPreviousPage(currentPage);
            });
            return;
        }

        if (isCustomItem(current, "exit")) {
            // Remove player from game.
            messages.send(player, Messages.Message.LEAVE_PLAYER);
            game.removeCompletely(player);
            closeInventory(player);
            return;
        }

        ItemMeta meta = current.getItemMeta();
        if (meta == null) return;

        String chipName = meta.getPersistentDataContainer().get(plugin.getChipNameKey(), PersistentDataType.STRING);
        if (chipName == null) return;

        Chip chip = plugin.getChipManager().getByName(chipName);
        if (chip == null) return;

        if (!game.newBet(player, chip, isNewBet)) {
            // Not enough money.
            event.setCurrentItem(plugin.getItem("not-enough-money").build());
            return;
        }

        closeInventory(player);
    }
}