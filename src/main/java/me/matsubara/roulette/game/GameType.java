package me.matsubara.roulette.game;

import me.matsubara.roulette.manager.ConfigManager;

public enum GameType {
    // 0 & 00.
    AMERICAN,
    // Single 0.
    EUROPEAN;

    public boolean isAmerican() {
        return this == AMERICAN;
    }

    public boolean isEuropean() {
        return this == EUROPEAN;
    }

    public String getName() {
        return isAmerican() ? ConfigManager.Config.TYPE_AMERICAN.asString() : ConfigManager.Config.TYPE_EUROPEAN.asString();
    }

    public String getModelName() {
        return (name() + "_roulette").toLowerCase();
    }
}