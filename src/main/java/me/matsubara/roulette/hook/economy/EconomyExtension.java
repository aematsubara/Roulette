package me.matsubara.roulette.hook.economy;

import me.matsubara.roulette.hook.RExtension;
import org.bukkit.OfflinePlayer;

public interface EconomyExtension<T> extends RExtension<T> {

    DummyEconomyExtension DUMMY = new DummyEconomyExtension();

    boolean isEnabled();

    double getBalance(OfflinePlayer player);

    boolean has(OfflinePlayer player, double money);

    String format(double money);

    boolean deposit(OfflinePlayer player, double money);

    boolean withdraw(OfflinePlayer player, double money);
}