package me.matsubara.roulette.manager.data;

import me.matsubara.roulette.game.data.Slot;
import me.matsubara.roulette.game.data.WinData;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record PlayerResult(RouletteSession session,
                           UUID playerUUID,
                           UUID sessionUUID,
                           @Nullable WinData.WinType win,
                           double money,
                           Slot slot) {

    public PlayerResult(RouletteSession session, UUID playerUUID, @Nullable WinData.WinType win, double money, Slot slot) {
        this(session, playerUUID, session.sessionUUID(), win, money, slot);
    }

    public boolean won() {
        return win != null;
    }
}