package me.matsubara.roulette.game.data;

import lombok.Getter;
import org.bukkit.entity.Player;

public record WinData(Player player, int betIndex, WinType winType) {

    @Getter
    public enum WinType {
        NORMAL("normal"),
        LA_PARTAGE("partage"),
        EN_PRISON("prison"),
        SURRENDER("surrender");

        private final String shortName;

        WinType(String shortName) {
            this.shortName = shortName;
        }

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
    }
}