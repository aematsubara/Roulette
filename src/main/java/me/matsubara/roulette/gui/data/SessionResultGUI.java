package me.matsubara.roulette.gui.data;

import lombok.Getter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.file.Config;
import me.matsubara.roulette.file.Messages;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.data.Slot;
import me.matsubara.roulette.game.data.WinData;
import me.matsubara.roulette.gui.RouletteGUI;
import me.matsubara.roulette.hook.economy.EconomyExtension;
import me.matsubara.roulette.manager.data.DataManager;
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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@Getter
public final class SessionResultGUI extends RouletteGUI {

    // The player viewing this inventory.
    private final Player player;

    // The current session.
    private final RouletteSession session;

    // The inventory being used.
    private final Inventory inventory;

    // The slots to show the content.
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16};

    // The slot to put page navigator items and other stuff.
    private static final int[] HOTBAR = {19, 20, 21, 22, 23, 24, 25};

    public SessionResultGUI(RoulettePlugin plugin, Player player, RouletteSession session) {
        this(plugin, player, session, 0);
    }

    public SessionResultGUI(@NotNull RoulettePlugin plugin, @NotNull Player player, RouletteSession session, int currentPage) {
        super(plugin, "session-result-menu", true);
        this.player = player;
        this.session = session;
        this.inventory = plugin.getServer().createInventory(this, 36);
        this.currentPage = currentPage;

        player.openInventory(inventory);
        updateInventory();
    }

    @Override
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
            double expectedMoney = plugin.getExpectedMoney(session.type(), originalMoney, slot, win);

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

    @Contract(pure = true)
    @Override
    public @Nullable Game getGame() {
        return null;
    }

    @Override
    public void handle(@NotNull InventoryClickEvent event) {
        super.handle(event);

        Player player = (Player) event.getWhoClicked();

        ItemStack current = event.getCurrentItem();
        if (current == null) return;

        ItemMeta meta = current.getItemMeta();
        if (meta == null) return;

        Integer resultIndex = meta.getPersistentDataContainer().get(plugin.getPlayerResultIndexKey(), PersistentDataType.INTEGER);
        if (resultIndex == null) return;

        PlayerResult result = session.results().get(resultIndex);
        if (result == null) return;

        ClickType click = event.getClick();
        boolean left = click.isLeftClick(), right = click.isRightClick();
        if (!left && !right) return;

        DataManager dataManager = plugin.getDataManager();
        Messages messages = plugin.getMessages();

        if (right) {
            dataManager.remove(result);
            messages.send(player, Messages.Message.SESSION_RESULT_REMOVED);
            closeInventory(player);
            return;
        }

        // If there is no economy provider, then we won't be able to deposit/withdraw money.
        EconomyExtension<?> economyExtension = plugin.getEconomyExtension();
        if (!economyExtension.isEnabled()) {
            messages.send(player, Messages.Message.NO_ECONOMY_PROVIDER);
            closeInventory(player);
            return;
        }

        WinData.WinType win = result.win();

        OfflinePlayer winner = Bukkit.getOfflinePlayer(result.playerUUID());

        double originalMoney = result.money();
        double expectedMoney = plugin.getExpectedMoney(session.type(), originalMoney, result.slot(), win);

        // Player lost. We want to refund the original money.
        if (win == null) {
            if (!economyExtension.deposit(winner, originalMoney)) return;

            Optional.ofNullable(winner.getPlayer())
                    .ifPresent(temp -> messages.send(temp,
                            Messages.Message.SESSION_LOST_RECOVERED,
                            line -> line.replace("%money%", PluginUtils.format(originalMoney))));

            messages.send(player, Messages.Message.SESSION_TRANSACTION_COMPLETED);
            dataManager.remove(result);
            closeInventory(player);
            return;
        }

        // Player won in prison, the player already recovered his original money.
        if (win.isEnPrisonWin()) {
            messages.send(player, Messages.Message.SESSION_BET_IN_PRISON);
            dataManager.remove(result);
            closeInventory(player);
            return;
        }

        if (economyExtension.has(winner, expectedMoney)) {
            // Remove the money that the player won.
            if (!economyExtension.withdraw(winner, expectedMoney)) return;

            // Deposit the original money.
            if (!economyExtension.deposit(winner, originalMoney)) return;

            Optional.ofNullable(winner.getPlayer())
                    .ifPresent(temp -> messages.send(temp,
                            Messages.Message.SESSION_BET_REVERTED,
                            line -> line
                                    .replace("%win-money%", PluginUtils.format(expectedMoney))
                                    .replace("%money%", PluginUtils.format(originalMoney))));

            dataManager.remove(result);
            messages.send(player, Messages.Message.SESSION_TRANSACTION_COMPLETED);
        } else {
            messages.send(player, Messages.Message.SESSION_TRANSACTION_FAILED);
        }

        closeInventory(player);
    }
}