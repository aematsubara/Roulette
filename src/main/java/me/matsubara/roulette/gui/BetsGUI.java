package me.matsubara.roulette.gui;

import com.google.common.base.Predicates;
import lombok.Getter;
import me.matsubara.roulette.file.Config;
import me.matsubara.roulette.file.Messages;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.GameType;
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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Getter
public final class BetsGUI extends RouletteGUI {

    // The instance of the game.
    private final Game game;

    // The player viewing this inventory.
    private final Player player;

    // The inventory being used.
    private final Inventory inventory;

    // The slots to show the content.
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16};

    // The slot to put page navigator items and other stuff.
    private static final int[] HOTBAR = {19, 20, 21, 22, 23, 24, 25};

    public BetsGUI(Game game, Player player) {
        this(game, player, 0);
    }

    public BetsGUI(@NotNull Game game, @NotNull Player player, int currentPage) {
        super(game.getPlugin(), "bets-menu", true);
        this.game = game;
        this.player = player;
        this.inventory = plugin.getServer().createInventory(this, 36);
        this.currentPage = currentPage;

        player.openInventory(inventory);
        updateInventory();
    }

    @Override
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

        GameType type = game.getType();

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
                    .replace("%chance%", slot.getChance(type.isEuropean()))
                    .replace("%multiplier%", String.valueOf(slot.getMultiplier(type, plugin)))
                    .replace("%win-money%", PluginUtils.format(plugin.getExpectedMoney(type, price, slot, winType)))
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

    @Override
    public void handle(@NotNull InventoryClickEvent event) {
        super.handle(event);

        Player player = (Player) event.getWhoClicked();

        ItemStack current = event.getCurrentItem();
        if (current == null) return;

        List<Bet> bets = game.getBets(player);

        ClickType click = event.getClick();
        boolean left = click.isLeftClick(), right = click.isRightClick();
        if (!left && !right) return;

        Messages messages = plugin.getMessages();

        if (isCustomItem(current, "glow-color")) {
            // Change glow color and update it for the existing bets.
            game.changeGlowColor(player, right);

            // Update the glow for the selected bet.
            Bet bet = game.getSelectedBet(player);
            if (bet != null) bet.updateStandGlow(player);

            setGlowColorItem();
            return;
        }

        if (isCustomItem(current, "new-bet")) {
            // At this point, the player shouldn't have access to this inventory.
            if (game.isDone(player)) {
                closeInventory(player);
                return;
            }

            // The player reached the betting limit.
            if (game.getBets(player).size() == game.getMaxBets()
                    || !game.isSlotAvailable(player, true)) {
                messages.send(player, Messages.Message.NO_MORE_SLOTS);
                closeInventory(player);
                return;
            }

            if (plugin.getChipManager().hasEnoughMoney(game, player)) {
                // Open a new chip menu for a new bet.
                runTask(() -> new ChipGUI(game, player, true));
                return;
            }

            closeInventory(player);
            return;
        }

        if (isCustomItem(current, "done")) {
            runTask(() -> {
                ConfirmGUI confirm = new ConfirmGUI(game, player, ConfirmGUI.ConfirmType.DONE);
                confirm.setPreviousPage(currentPage);
            });
            return;
        }

        ItemMeta meta = current.getItemMeta();
        if (meta == null) return;

        Integer betIndex = meta.getPersistentDataContainer().get(plugin.getBetIndexKey(), PersistentDataType.INTEGER);
        if (betIndex == null) return;

        Bet bet = bets.get(betIndex);
        if (bet == null) return;

        if (left) {
            // We don't want players to interact with prison bets.
            if (bet.isEnPrison()) {
                messages.send(player, Messages.Message.BET_IN_PRISON);
                return;
            }

            // This bet is already selected.
            if (betIndex.equals(game.getSelectedBetIndex(player))) {
                messages.send(player, Messages.Message.BET_ALREADY_SELECTED);
                closeInventory(player);
                return;
            }

            // Select bet.
            game.selectBet(player, betIndex);

            // Handle holograms and close inventory.
            game.handlePlayerBetHolograms(player);
            closeInventory(player);

            messages.send(player, Messages.Message.BET_SELECTED,
                    line -> line.replace("%bet%", String.valueOf(betIndex + 1)));
            return;
        }

        // We don't want players to interact with prison bets.
        if (bet.isEnPrison()) {
            messages.send(player, Messages.Message.BET_IN_PRISON);
            closeInventory(player);
            return;
        }

        if (bets.stream()
                .filter(Predicates.not(Bet::isEnPrison))
                .count() == 1) {
            messages.send(player, Messages.Message.AT_LEAST_ONE_BET_REQUIRED);
            closeInventory(player);
            return;
        }

        // First try to return the money, then remove the bet.
        game.tryToReturnMoney(player, bet);
        game.removeBet(player, betIndex);

        // Remove hologram and chip.
        bet.remove();

        // Select the last bet.
        game.selectLast(player);

        // Handle holograms and close inventory.
        game.handlePlayerBetHolograms(player);
        closeInventory(player);

        messages.send(player, Messages.Message.BET_REMOVED,
                line -> line.replace("%bet%", String.valueOf(betIndex + 1)));
    }
}