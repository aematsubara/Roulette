package me.matsubara.roulette.gui;

import lombok.Getter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.file.Config;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.data.Bet;
import me.matsubara.roulette.game.data.Chip;
import me.matsubara.roulette.game.data.Slot;
import me.matsubara.roulette.game.data.WinData;
import me.matsubara.roulette.util.ColorUtils;
import me.matsubara.roulette.util.InventoryUpdate;
import me.matsubara.roulette.util.ItemBuilder;
import me.matsubara.roulette.util.PluginUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Getter
public final class BetsGUI extends RouletteGUI {

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

    public BetsGUI(Game game, Player player) {
        this(game, player, 0);
    }

    public BetsGUI(@NotNull Game game, @NotNull Player player, int currentPage) {
        super("bets-menu");
        this.plugin = game.getPlugin();
        this.game = game;
        this.player = player;
        this.inventory = plugin.getServer().createInventory(this, 36);
        this.currentPage = currentPage;

        player.openInventory(inventory);
        updateInventory();
    }

    public void updateInventory() {
        inventory.clear();

        // Get the list of chips.
        List<Bet> bets = game.getBets(player);

        // Page formula.
        pages = (int) (Math.ceil((double) bets.size() / SLOTS.length));

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

        // Set glow item (21).
        setGlowColorItem();

        // Set new bet item.
        inventory.setItem(22, getItem("new-bet").build());

        // Set money item.
        inventory.setItem(23, getItem("money")
                .replace("%money%", PluginUtils.format(plugin.getEconomyExtension().getBalance(player)))
                .build());

        // If the current page isn't the last one, show the next page item.
        if (currentPage < pages - 1) inventory.setItem(25, getItem("next").build());

        // Set done item.
        inventory.setItem(35, getItem("done").build());

        // Assigning slots.
        Map<Integer, Integer> slotIndex = new HashMap<>();
        for (int i : SLOTS) {
            slotIndex.put(ArrayUtils.indexOf(SLOTS, i), i);
        }

        // Where to start.
        int startFrom = currentPage * SLOTS.length;

        boolean isLastPage = currentPage == pages - 1;

        for (int index = 0, aux = startFrom; isLastPage ? (index < bets.size() - startFrom) : (index < SLOTS.length); index++, aux++) {
            Bet bet = bets.get(aux);

            Chip chip = bet.getChip();
            if (chip == null) continue;

            Slot slot = bet.getSlot();
            if (slot == null) continue;

            double price = chip.price();
            WinData.WinType winType = bet.isEnPrison() ? WinData.WinType.EN_PRISON : WinData.WinType.NORMAL;

            inventory.setItem(slotIndex.get(index), getItem("bet")
                    .setHead(chip.url(), true)
                    .replace("%bet%", aux + 1)
                    .replace("%slot%", PluginUtils.getSlotName(slot))
                    .replace("%money%", PluginUtils.format(price))
                    .replace("%chance%", slot.getChance(game.getType().isEuropean()))
                    .replace("%multiplier%", String.valueOf(slot.getMultiplier(plugin)))
                    .replace("%win-money%", PluginUtils.format(plugin.getExpectedMoney(price, slot, winType)))
                    .setData(plugin.getChipNameKey(), PersistentDataType.STRING, chip.name())
                    .setData(plugin.getBetIndexKey(), PersistentDataType.INTEGER, aux)
                    .build());
        }

        // Update inventory title to show the current page.
        InventoryUpdate.updateInventory(player, Config.BETS_MENU_TITLE.asStringTranslated()
                .replace("%page%", String.valueOf(currentPage + 1))
                .replace("%max%", String.valueOf(pages)));
    }

    public void setGlowColorItem() {
        ChatColor color = game.getGlowColor(player);

        String tempName = color.name().toLowerCase(Locale.ROOT).replace("_", "-");
        String colorName = plugin.getConfig().getString("variable-text.glow-colors." + tempName, StringUtils.capitalize(tempName.replace("-", " ")));

        inventory.setItem(21, getItem("glow-color")
                .setHead(ColorUtils.GLOW_COLOR_URL.get(color), true)
                .replace("%color%", color + colorName)
                .build());
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