package me.matsubara.roulette.game;

import me.matsubara.roulette.file.Config;
import me.matsubara.roulette.file.config.ConfigValue;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public enum GameType {
    AMERICAN(Config.TYPE_AMERICAN), // 0 & 00.
    EUROPEAN(Config.TYPE_EUROPEAN); // Single 0.

    private final ConfigValue value;

    GameType(ConfigValue value) {
        this.value = value;
    }

    public boolean isAmerican() {
        return this == AMERICAN;
    }

    public boolean isEuropean() {
        return this == EUROPEAN;
    }

    public @NotNull String getName() {
        return value.asStringTranslated();
    }

    public @NotNull String getFileName() {
        return (name() + "_roulette.yml").toLowerCase(Locale.ROOT);
    }
}