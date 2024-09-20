package me.matsubara.roulette.event;

import lombok.Getter;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.data.Slot;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

@Getter
public class RouletteEndEvent extends RouletteEvent {

    private final Set<Player> winners;
    private final Slot winnerSlot;

    private static final HandlerList handlers = new HandlerList();

    public RouletteEndEvent(Game game, Set<Player> winners, Slot winnerSlot) {
        super(game);
        this.winners = winners;
        this.winnerSlot = winnerSlot;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    @SuppressWarnings("unused")
    public static HandlerList getHandlerList() {
        return handlers;
    }
}