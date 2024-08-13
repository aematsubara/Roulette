package me.matsubara.roulette.gui;

import me.matsubara.roulette.game.Game;
import org.bukkit.inventory.InventoryHolder;

public interface RouletteGUI extends InventoryHolder {

    Game getGame();
}