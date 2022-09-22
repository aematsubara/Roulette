package me.matsubara.roulette.event;

import me.matsubara.roulette.game.Game;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class RouletteStartEvent extends RouletteEvent {

    private final static HandlerList handlers = new HandlerList();

    public RouletteStartEvent(Game game) {
        super(game);
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    @SuppressWarnings("unused")
    @NotNull
    public static HandlerList getHandlerList() {
        return handlers;
    }
}