package me.matsubara.roulette.event;

import lombok.Getter;
import lombok.Setter;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.data.Slot;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public class LastRouletteSpinEvent extends RouletteEvent {

    @Getter
    private @Setter Slot winnerSlot;

    private static final HandlerList handlers = new HandlerList();

    public LastRouletteSpinEvent(Game game, Slot winnerSlot) {
        super(game);
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