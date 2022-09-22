package me.matsubara.roulette.event;

import me.matsubara.roulette.game.Game;
import org.bukkit.event.Event;

public abstract class RouletteEvent extends Event {

    protected final Game game;

    public RouletteEvent(Game game) {
        this.game = game;
    }

    public Game getGame() {
        return game;
    }
}