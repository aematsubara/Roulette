package me.matsubara.roulette.event;

import lombok.Getter;
import me.matsubara.roulette.game.Game;
import org.bukkit.event.Event;

public abstract class RouletteEvent extends Event {

    protected final @Getter Game game;

    public RouletteEvent(Game game) {
        super(false);
        this.game = game;
    }
}