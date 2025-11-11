package me.matsubara.roulette.gui;

import com.google.common.base.Predicates;
import lombok.Getter;
import lombok.Setter;
import me.matsubara.roulette.file.Config;
import me.matsubara.roulette.file.Messages;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.data.Bet;
import me.matsubara.roulette.game.state.Selecting;
import me.matsubara.roulette.util.ItemBuilder;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Getter
public final class ConfirmGUI extends RouletteGUI {

    // The game related to this GUI.
    private final Game game;

    // The inventory being used.
    private final Inventory inventory;

    // The confirmation type gui.
    private final ConfirmType type;

    // The previous page of the chip gui.
    private @Setter int previousPage;

    // The chip GUI source (for BET_ALL).
    private @Setter ChipGUI sourceGUI;

    public ConfirmGUI(@NotNull Game game, Player player, ConfirmType type) {
        super(game.getPlugin(), "confirmation-menu");
        this.game = game;
        this.inventory = plugin.getServer().createInventory(this, 9, Config.CONFIRM_MENU_TITLE.asStringTranslated());
        this.type = type;
        this.previousPage = 0;

        // Fill inventory.
        for (int i = 0; i < 9; i++) {
            ItemBuilder builder = i == 4 ?
                    plugin.getItem(type.getIconPath()) :
                    getItem(i < 4 ? "confirm" : "cancel");
            inventory.setItem(i, builder.build());
        }

        // Open inventory.
        player.openInventory(inventory);
    }

    @Getter
    public enum ConfirmType {
        LEAVE("chip-menu.items.exit"),
        BET_ALL("chip-menu.items.bet-all"),
        DONE("bets-menu.items.done");

        private final String iconPath;

        ConfirmType(String iconPath) {
            this.iconPath = iconPath;
        }

        public boolean isLeave() {
            return this == LEAVE;
        }

        public boolean isBetAll() {
            return this == BET_ALL;
        }

        public boolean isDone() {
            return this == DONE;
        }
    }

    @Override
    public void handle(@NotNull InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();

        ItemStack current = event.getCurrentItem();
        if (current == null) return;

        // Clicked on the item of the middle.
        String iconPath = type.getIconPath();
        if (isCustomItem(current, iconPath.substring(iconPath.lastIndexOf(".") + 1))) return;

        // Close the inventory.
        if (isCustomItem(current, "cancel")) {
            closeInventory(player);
            return;
        }

        Messages messages = plugin.getMessages();

        if (type.isLeave()) {
            // Remove the player from the game.
            messages.send(player, Messages.Message.LEAVE_PLAYER);
            game.removeCompletely(player);
        } else if (type.isBetAll()) {
            // Bet all the money.
            double money = plugin.getEconomyExtension().getBalance(player);

            game.takeMoneyAndPlaceBet(
                    player,
                    plugin.getChipManager().getExistingOrBetAll(game, money),
                    sourceGUI.isNewBet());
        } else {
            // Make the call.
            game.setDone(player);

            // Remove glow and hide hologram.
            game.getBets(player).forEach(Bet::hide);

            // If all players are done, then we want to reduce the start time.
            if (game.getPlayers().stream().noneMatch(Predicates.not(game::isDone))) {
                game.broadcast(Messages.Message.ALL_PLAYERS_DONE);

                Selecting selecting = game.getSelecting();
                if (selecting != null
                        && !selecting.isCancelled()
                        && selecting.getTicks() > 100) {
                    selecting.setTicks(100);
                }
            } else {
                messages.send(player, Messages.Message.YOU_ARE_DONE);
                // Let the other players know.
                game.broadcast(
                        Messages.Message.YOU_ARE_DONE,
                        line -> line.replace("%player-name%", player.getName()),
                        player);
            }
        }

        closeInventory(player);
    }
}