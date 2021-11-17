package me.matsubara.roulette.manager.winner;

import me.matsubara.roulette.game.WinType;
import me.matsubara.roulette.game.data.Slot;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Winner {

    private final UUID uuid;
    private final List<WinnerData> winnerData;

    public Winner(UUID uuid) {
        this.uuid = uuid;
        this.winnerData = new ArrayList<>();
    }

    public void add(String game, Integer mapId, double money, long date, Slot slot, Slot winner, WinType type, double originalMoney) {
        winnerData.add(new WinnerData(game, mapId, money, date, slot, winner, type, originalMoney));
    }

    public UUID getUUID() {
        return uuid;
    }

    public List<WinnerData> getWinnerData() {
        return winnerData;
    }

    public final static class WinnerData {

        // Name of the game.
        private final String game;

        // Unique id created for the map.
        private final Integer mapId;

        // Money earned in the game (can be different from the original amount due to partage/prison/surrender rule).
        private final double money;

        // The original amount of money that the played has bet.
        private final double originalMoney;

        // Date of winning in millis.
        private final long date;

        // The slot the player selected (can be any @Slot value).
        private final Slot selected;

        // The winner slot of the game (only single numbers).
        private final Slot winner;

        // Winning type.
        private final WinType type;

        public WinnerData(String game, Integer mapId, double money, long date, Slot selected, Slot winner, WinType type, double originalMoney) {
            this.game = game;
            this.mapId = mapId;
            this.money = money;
            this.date = date;
            this.selected = selected;
            this.winner = winner;
            this.type = type;
            this.originalMoney = originalMoney;
        }

        public String getGame() {
            return game;
        }

        public Integer getMapId() {
            return mapId;
        }

        public double getMoney() {
            return money;
        }

        public long getDate() {
            return date;
        }

        public Slot getSelected() {
            return selected;
        }

        public Slot getWinner() {
            return winner;
        }

        public WinType getWinType() {
            return type;
        }

        public double getOriginalMoney() {
            return originalMoney;
        }
    }
}