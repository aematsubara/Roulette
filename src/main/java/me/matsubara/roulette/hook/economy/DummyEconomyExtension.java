package me.matsubara.roulette.hook.economy;

import me.matsubara.roulette.RoulettePlugin;
import org.bukkit.OfflinePlayer;

public class DummyEconomyExtension implements EconomyExtension<DummyEconomyExtension> {

    protected DummyEconomyExtension() {

    }

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return 0.0d;
    }

    @Override
    public boolean has(OfflinePlayer player, double money) {
        return false;
    }

    @Override
    public String format(double money) {
        return "$" + money;
    }

    @Override
    public boolean deposit(OfflinePlayer player, double money) {
        return false;
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double money) {
        return false;
    }

    @Override
    public DummyEconomyExtension init(RoulettePlugin plugin) {
        return this;
    }
}
