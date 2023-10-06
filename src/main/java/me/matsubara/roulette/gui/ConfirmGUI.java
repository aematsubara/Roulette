package me.matsubara.roulette.gui;

import lombok.Getter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.manager.ConfigManager;
import me.matsubara.roulette.util.ItemBuilder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

@Getter
public final class ConfirmGUI implements InventoryHolder, RouletteGUI {

    // The game related to this GUI.
    private final Game game;

    // The inventory being used.
    private final Inventory inventory;

    // The confirmation type gui.
    private final ConfirmType type;

    // The previous page of the chip gui.
    private int previousPage;

    public ConfirmGUI(@NotNull Game game, Player player, ConfirmType type) {
        RoulettePlugin plugin = game.getPlugin();

        this.game = game;
        this.inventory = plugin.getServer().createInventory(this, 9, ConfigManager.Config.CONFIRM_GUI_TITLE.asString());
        this.type = type;
        // Set first page as default previous.
        this.previousPage = 0;

        ItemStack confirm = new ItemBuilder("LIME_STAINED_GLASS_PANE")
                .setDisplayName(ConfigManager.Config.CONFIRM_GUI_CONFIRM.asString())
                .build();

        ItemStack cancel = new ItemBuilder("RED_STAINED_GLASS_PANE")
                .setDisplayName(ConfigManager.Config.CONFIRM_GUI_CANCEL.asString())
                .build();

        ItemStack betAll = new ItemBuilder(plugin.getConfigManager().getItem("shop", "bet-all", null))
                .setLore(Collections.emptyList())
                .build();

        ItemStack leave = new ItemBuilder(plugin.getConfigManager().getItem("shop", "exit", null))
                .setLore(Collections.emptyList())
                .build();

        // Fill inventory.
        for (int i = 0; i < 9; i++) {
            inventory.setItem(i, i < 4 ? confirm : i > 4 ? cancel : type == ConfirmType.LEAVE ? leave : betAll);
        }

        // Open inventory.
        player.openInventory(inventory);
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public ConfirmType getType() {
        return type;
    }

    public int getPreviousPage() {
        return previousPage;
    }

    public void setPreviousPage(int previousPage) {
        this.previousPage = previousPage;
    }

    @SuppressWarnings("unused")
    public enum ConfirmType {
        LEAVE,
        BET_ALL;

        public boolean isLeave() {
            return this == LEAVE;
        }

        public boolean isBetAll() {
            return this == BET_ALL;
        }
    }
}