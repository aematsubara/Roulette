package me.matsubara.roulette.manager.data;

import me.matsubara.roulette.game.data.Bet;
import me.matsubara.roulette.game.data.Chip;
import me.matsubara.roulette.game.data.Slot;
import me.matsubara.roulette.game.data.WinData;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public record RouletteSession(UUID sessionUUID, String name, List<PlayerResult> results, Slot slot, long timestamp) {

    public RouletteSession(UUID sessionUUID, String name, Slot slot, long timestamp) {
        this(sessionUUID, name, new ArrayList<>(), slot, timestamp);
    }

    public RouletteSession(UUID sessionUUID, String name, Slot slot, long timestamp, Collection<Map.Entry<Player, Bet>> bets) {
        this(sessionUUID, name, new ArrayList<>(), slot, timestamp);
        results.addAll(createResultsFromBets(bets));
    }

    private @NotNull List<PlayerResult> createResultsFromBets(@NotNull Collection<Map.Entry<Player, Bet>> bets) {
        List<PlayerResult> results = new ArrayList<>();

        // Now, we save the players.
        for (Map.Entry<Player, Bet> entry : bets) {
            Player player = entry.getKey();
            Bet bet = entry.getValue();

            WinData data = bet.getWinData();
            WinData.WinType win = data != null ? data.winType() : null;

            Slot slot = bet.getSlot();
            Chip chip = bet.getChip();

            // We save the original money.
            double money = chip.price();

            results.add(new PlayerResult(this, player.getUniqueId(), win, money, slot));
        }

        return results;
    }
}