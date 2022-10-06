package me.matsubara.roulette.event;

import me.matsubara.roulette.game.Game;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class PlayerRouletteEnterEvent extends RouletteEvent implements Cancellable {

    private final Player player;
    private boolean cancelled;

    private final static HandlerList handlers = new HandlerList();

    public PlayerRouletteEnterEvent(Game game, Player player) {
        super(game, true);
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
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