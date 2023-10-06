package me.matsubara.roulette.game;

import org.apache.commons.lang3.text.WordUtils;

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

    @SuppressWarnings("deprecation")
    public String getFormatName() {
        return WordUtils.capitalizeFully(name().replace("_", " "));
    }
}