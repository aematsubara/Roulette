package me.matsubara.roulette.gui;

import lombok.Getter;
import lombok.Setter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.manager.ConfigManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

@Getter
public final class ConfirmGUI implements RouletteGUI {

    // The game related to this GUI.
    private final Game game;

    // The inventory being used.
    private final Inventory inventory;

    // The confirmation type gui.
    private final ConfirmType type;

    // The previous page of the chip gui.
    private @Setter int previousPage;

    public ConfirmGUI(@NotNull Game game, Player player, ConfirmType type) {
        RoulettePlugin plugin = game.getPlugin();

        this.game = game;
        this.inventory = plugin.getServer().createInventory(this, 9, ConfigManager.Config.CONFIRM_GUI_TITLE.asString());
        this.type = type;
        this.previousPage = 0;

        // Fill inventory.
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, i < 4 ?
                    plugin.getItem("confirmation-gui.confirm").build() :
                    i > 4 ?
                            plugin.getItem("confirmation-gui.cancel").build() :
                            type == ConfirmType.LEAVE ?
                                    plugin.getItem("shop.exit").build() :
                                    plugin.getItem("shop.bet-all").build());
        }

        // Open inventory.
        player.openInventory(inventory);
    }

    public enum ConfirmType {
        LEAVE,
        BET_ALL;

        public boolean isLeave() {
            return this == LEAVE;
        }

        @SuppressWarnings("unused")
        public boolean isBetAll() {
            return this == BET_ALL;
        }
    }
}