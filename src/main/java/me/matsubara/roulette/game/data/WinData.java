package me.matsubara.roulette.game.data;

import lombok.Getter;
import me.matsubara.roulette.game.GameRule;
import org.bukkit.entity.Player;

public record WinData(Player player, int betIndex, WinType winType) {

    @Getter
    public enum WinType {
        NORMAL(),
        LA_PARTAGE("partage", GameRule.LA_PARTAGE),
        EN_PRISON("prison", GameRule.EN_PRISON),
        SURRENDER("surrender", GameRule.SURRENDER);

        private final String shortName;
        private final GameRule rule;

        WinType() {
            this(null, null);
        }

        WinType(String shortName, GameRule rule) {
            this.shortName = shortName;
            this.rule = rule;
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