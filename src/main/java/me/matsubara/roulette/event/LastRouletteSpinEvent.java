package me.matsubara.roulette.event;

import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.data.Slot;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public class LastRouletteSpinEvent extends RouletteEvent {

    private Slot winnerSlot;

    private static final HandlerList handlers = new HandlerList();

    public LastRouletteSpinEvent(Game game, Slot winnerSlot) {
        super(game);
        this.winnerSlot = winnerSlot;
    }

    public Slot getWinnerSlot() {
        return winnerSlot;
    }

    public void setWinnerSlot(Slot winnerSlot) {
        this.winnerSlot = winnerSlot;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }
}