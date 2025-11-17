package me.matsubara.roulette.manager.data;

import lombok.AccessLevel;
import lombok.Getter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.data.WinData;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

@Getter
public class PlayerStats {

    private final RoulettePlugin plugin;
    private final DataManager manager;
    private final Player player;
    private long expireTime;

    private @Getter(AccessLevel.NONE) int wins;
    private double totalMoney;
    private double maxMoney;
    private final @Getter(AccessLevel.NONE) Map<WinData.WinType, Integer> ruleWin = new HashMap<>();

    private static final long EXPIRE_TIME = 30000L;

    public PlayerStats(@NotNull RoulettePlugin plugin, Player player) {
        this.plugin = plugin;
        this.manager = plugin.getDataManager();
        this.player = player;
        resetStats();
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expireTime;
    }

    public void resetStats() {
        this.expireTime = System.currentTimeMillis() + EXPIRE_TIME;
        this.wins = calculateWins(null);
        this.totalMoney = calculateTotalMoney();
        this.maxMoney = calculateMaxMoney();
        for (WinData.WinType type : WinData.WinType.values()) {
            this.ruleWin.put(type, calculateWins(type));
        }
    }

    private int calculateWins(@Nullable WinData.WinType type) {
        int wins = 0;
        for (RouletteSession session : manager.getSessions()) {
            for (PlayerResult result : session.results()) {
                if (!result.playerUUID().equals(player.getUniqueId()) || !result.won()) continue;
                if (type != null && result.win() != type) continue;
                wins++;
            }
        }
        return wins;
    }

    private double calculateTotalMoney() {
        double totalMoney = 0.0d;
        for (RouletteSession session : manager.getSessions()) {
            for (PlayerResult result : session.results()) {
                if (!result.playerUUID().equals(player.getUniqueId()) || !result.won()) continue;
                totalMoney += plugin.getExpectedMoney(result);
            }
        }
        return totalMoney;
    }

    private double calculateMaxMoney() {
        double maxMoney = 0.0d;
        for (RouletteSession session : manager.getSessions()) {
            for (PlayerResult result : session.results()) {
                if (!result.playerUUID().equals(player.getUniqueId()) || !result.won()) continue;
                double money = plugin.getExpectedMoney(result);
                if (money > maxMoney) maxMoney = money;
            }
        }
        return maxMoney;
    }

    public int getWins(@Nullable WinData.WinType type) {
        return type != null ? ruleWin.getOrDefault(type, 0) : wins;
    }
}