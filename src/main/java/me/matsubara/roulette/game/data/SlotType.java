package me.matsubara.roulette.game.data;

import lombok.Getter;
import me.matsubara.roulette.game.Game;
import org.apache.commons.lang3.ArrayUtils;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Getter
public enum SlotType {
    COLOR(Slot.SLOT_RED, Slot.SLOT_BLACK),
    ODD_EVEN(Slot.SLOT_EVEN, Slot.SLOT_ODD),
    HIGH_LOW(Slot.SLOT_LOW, Slot.SLOT_HIGH),
    COLUMN(Slot.SLOT_COLUMN_1, Slot.SLOT_COLUMN_2, Slot.SLOT_COLUMN_3),
    DOZEN(Slot.SLOT_DOZEN_1, Slot.SLOT_DOZEN_2, Slot.SLOT_DOZEN_3);

    private final Slot[] slots;

    SlotType(Slot... slots) {
        this.slots = slots;
    }

    public static @Nullable SlotType hasConflict(Game game, Player player, Slot slot, boolean ignoreCurrent) {
        for (SlotType type : values()) {
            if (!ArrayUtils.contains(type.slots, slot)) continue;

            List<Bet> bets = game.getBets(player);
            if (bets.isEmpty()) return null;

            for (Bet bet : bets) {
                if (!bet.hasSlot()) continue;
                if (!ArrayUtils.contains(type.slots, bet.getSlot())) continue;
                if (!ignoreCurrent && bet.equals(game.getSelectedBet(player))) continue;
                return type;
            }
        }
        return null;
    }
}