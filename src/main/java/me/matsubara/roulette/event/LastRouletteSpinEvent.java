package me.matsubara.roulette.event;

import lombok.Getter;
import lombok.Setter;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.data.Slot;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Setter
@Getter
public class LastRouletteSpinEvent extends RouletteEvent {

    private Slot winnerSlot;
    private final @Nullable Player forcedBy;

    private static final HandlerList handlers = new HandlerList();

    public LastRouletteSpinEvent(Game game, Slot winnerSlot, @Nullable Player forcedBy) {
        super(game);
        this.winnerSlot = winnerSlot;
        this.forcedBy = forcedBy;
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