package me.matsubara.roulette.game;

import me.matsubara.roulette.manager.ConfigManager;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public enum GameType {
    AMERICAN, // 0 & 00.
    EUROPEAN; // Single 0.

    public boolean isAmerican() {
        return this == AMERICAN;
    }

    public boolean isEuropean() {
        return this == EUROPEAN;
    }

    public String getName() {
        return isAmerican() ? ConfigManager.Config.TYPE_AMERICAN.asString() : ConfigManager.Config.TYPE_EUROPEAN.asString();
    }

    public @NotNull String getModelName() {
        return (name() + "_roulette").toLowerCase(Locale.ROOT);
    }
}