package me.matsubara.roulette.game.data;

import lombok.Getter;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.GameType;
import org.apache.commons.lang3.ArrayUtils;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;

@Getter
public enum SlotType {
    COLOR(true, (slot, type) -> slot.isRed() || slot.isBlack()),
    ODD_EVEN(true, (slot, type) -> slot.isOdd() || slot.isEven()),
    HIGH_LOW(true, (slot, type) -> slot.isLow() || slot.isHigh()),
    COLUMN(true, (slot, type) -> slot.isColumn()),
    DOZEN(true, (slot, type) -> slot.isDozen());

    private final boolean conflict;
    private final BiPredicate<Slot, GameType> condition;


    public Slot[] getSlots(GameType type) {
        return Arrays.stream(Slot.values())
                .filter(slot -> condition.test(slot, type))
                .toArray(Slot[]::new);
    }

    SlotType(boolean conflict, BiPredicate<Slot, GameType> condition) {
        this.conflict = conflict;
        this.condition = condition;
    }

    public static @Nullable SlotType hasConflict(Game game, Player player, Slot slot, boolean ignoreCurrent) {
        for (SlotType type : values()) {
            if (!type.conflict) continue;

            GameType gameType = game.getType();
            if (!ArrayUtils.contains(type.getSlots(gameType), slot)) continue;

            List<Bet> bets = game.getBets(player);
            if (bets.isEmpty()) return null;

            for (Bet bet : bets) {
                if (!bet.hasSlot()) continue;
                if (!ArrayUtils.contains(type.getSlots(gameType), bet.getSlot())) continue;
                if (!ignoreCurrent && bet.equals(game.getSelectedBet(player))) continue;
                return type;
            }
        }
        return null;
    }
}