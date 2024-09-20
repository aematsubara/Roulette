package me.matsubara.roulette.gui;

import lombok.Getter;
import lombok.Setter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.manager.ConfigManager;
import me.matsubara.roulette.util.ItemBuilder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

@Getter
public final class ConfirmGUI extends RouletteGUI {

    // The instance of the plugin.
    private final RoulettePlugin plugin;

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
        super("confirmation-menu");
        this.plugin = game.getPlugin();
        this.game = game;
        this.inventory = plugin.getServer().createInventory(this, 9, ConfigManager.Config.CONFIRM_MENU_TITLE.asString());
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
}