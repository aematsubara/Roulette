package me.matsubara.roulette.game;

import me.matsubara.roulette.util.Lang3Utils;

public enum WinType {
    NORMAL,
    LA_PARTAGE,
    EN_PRISON,
    SURRENDER;

    public boolean isNormalWin() {
        return this == NORMAL;
    }

    public boolean isLaPartageWin() {
        return this == LA_PARTAGE;
    }

    public boolean isEnPrisonWin() {
        return this == EN_PRISON;
    }

    public boolean isSurrenderWin() {
        return this == SURRENDER;
    }

    public String getFormatName() {
        return Lang3Utils.capitalizeFully(name().replace("_", " "));
    }
}