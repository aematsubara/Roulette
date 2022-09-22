package me.matsubara.roulette.event;

import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.WinType;
import me.matsubara.roulette.game.data.Slot;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

@SuppressWarnings("unused")
public class RouletteEndEvent extends RouletteEvent {

    private final Map<Player, WinType> winners;
    private final Slot winnerSlot;

    private final static HandlerList handlers = new HandlerList();

    public RouletteEndEvent(Game game, Map<Player, WinType> winners, Slot winnerSlot) {
        super(game);
        this.winners = winners;
        this.winnerSlot = winnerSlot;
    }

    public Map<Player, WinType> getWinners() {
        return winners;
    }

    public Slot getWinnerSlot() {
        return winnerSlot;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return handlers;
    }
}