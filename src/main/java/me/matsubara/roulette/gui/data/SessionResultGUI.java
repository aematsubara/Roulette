package me.matsubara.roulette.gui.data;

import lombok.Getter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.file.Config;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.data.Slot;
import me.matsubara.roulette.game.data.WinData;
import me.matsubara.roulette.gui.RouletteGUI;
import me.matsubara.roulette.manager.data.PlayerResult;
import me.matsubara.roulette.manager.data.RouletteSession;
import me.matsubara.roulette.util.InventoryUpdate;
import me.matsubara.roulette.util.ItemBuilder;
import me.matsubara.roulette.util.PluginUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Getter
public final class SessionResultGUI extends RouletteGUI {

    // The instance of the plugin.
    private final RoulettePlugin plugin;

    // The player viewing this inventory.
    private final Player player;

    // The current session.
    private final RouletteSession session;

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

    public SessionResultGUI(RoulettePlugin plugin, Player player, RouletteSession session) {
        this(plugin, player, session, 0);
    }

    public SessionResultGUI(@NotNull RoulettePlugin plugin, @NotNull Player player, RouletteSession session, int currentPage) {
        super("session-result-menu");
        this.plugin = plugin;
        this.player = player;
        this.session = session;
        this.inventory = plugin.getServer().createInventory(this, 36);
        this.currentPage = currentPage;

        player.openInventory(inventory);
        updateInventory();
    }

    public void updateInventory() {
        inventory.clear();

        // Get the list of sessions.
        List<PlayerResult> results = session.results();

        // Page formula.
        pages = (int) (Math.ceil((double) results.size() / SLOTS.length));

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

        // If the current page isn't the last one, show the next page item.
        if (currentPage < pages - 1) inventory.setItem(25, getItem("next").build());

        // Assigning slots.
        Map<Integer, Integer> slotIndex = new HashMap<>();
        for (int i : SLOTS) {
            slotIndex.put(ArrayUtils.indexOf(SLOTS, i), i);
        }

        // Where to start.
        int startFrom = currentPage * SLOTS.length;

        boolean isLastPage = currentPage == pages - 1;

        for (int index = 0, aux = startFrom; isLastPage ? (index < results.size() - startFrom) : (index < SLOTS.length); index++, aux++) {
            PlayerResult result = results.get(aux);

            WinData.WinType win = result.win();
            OfflinePlayer offline = Bukkit.getOfflinePlayer(result.playerUUID());
            Slot slot = result.slot();

            double originalMoney = result.money();
            double expectedMoney = plugin.getExpectedMoney(originalMoney, slot, win);

            String originalFormat = PluginUtils.format(originalMoney);
            String expectedFormat = PluginUtils.format(expectedMoney);

            inventory.setItem(slotIndex.get(index), getItem(win != null ? "victory-result" : "defeat-result")
                    .setOwningPlayer(offline)
                    .replace("%player-name%", Objects.requireNonNullElse(offline.getName(), "???"))
                    .replace("%slot%", PluginUtils.getSlotName(slot))
                    .replace("%rule%", plugin.formatWinType(win != null ? win : WinData.WinType.NORMAL))
                    .replace("%money%", originalFormat)
                    .replace("%win-money%", expectedFormat)
                    .setData(plugin.getPlayerResultIndexKey(), PersistentDataType.INTEGER, aux)
                    .build());
        }

        // Update inventory title to show the current page.
        InventoryUpdate.updateInventory(player, Config.SESSION_RESULT_MENU_TITLE.asStringTranslated()
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

    @Contract(pure = true)
    @Override
    public @Nullable Game getGame() {
        return null;
    }
}