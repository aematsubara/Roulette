package me.matsubara.roulette.gui;

import lombok.Getter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.util.ItemBuilder;
import org.bukkit.inventory.InventoryHolder;

@Getter
public abstract class RouletteGUI implements InventoryHolder {

    protected final String name;

    public RouletteGUI(String name) {
        this.name = name;
    }

    public ItemBuilder getItem(String path) {
        return getPlugin().getItem(name + ".items." + path);
    }

    public abstract RoulettePlugin getPlugin();

    public abstract Game getGame();
}