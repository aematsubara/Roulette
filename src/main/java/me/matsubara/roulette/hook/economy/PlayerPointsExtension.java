package me.matsubara.roulette.hook.economy;

import me.matsubara.roulette.RoulettePlugin;
import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.black_ixx.playerpoints.manager.LocaleManager;
import org.black_ixx.playerpoints.util.PointsUtils;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class PlayerPointsExtension implements EconomyExtension<PlayerPointsExtension> {

    private RoulettePlugin plugin;
    private PlayerPointsAPI api;
    private LocaleManager localeManager;

    @Override
    public PlayerPointsExtension init(@NotNull RoulettePlugin plugin) {
        this.plugin = plugin;
        PlayerPoints instance = PlayerPoints.getInstance();
        this.api = instance.getAPI();
        this.localeManager = instance.getManager(LocaleManager.class);
        plugin.getLogger().info("Using {" + instance.getDescription().getFullName() + "} as the economy provider.");
        return this;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public double getBalance(@NotNull OfflinePlayer player) {
        return api.look(player.getUniqueId());
    }

    @Override
    public boolean has(@NotNull OfflinePlayer player, double money) {
        return api.look(player.getUniqueId()) >= money;
    }

    @Override
    public String format(double money) {
        return PointsUtils.formatPoints((int) money) + " " + (money == 1 ? currencyNameSingular() : currencyNamePlural());
    }

    @Override
    public boolean deposit(@NotNull OfflinePlayer player, double money) {
        if (api.give(player.getUniqueId(), (int) money)) return true;

        plugin.getLogger().warning("It wasn't possible to deposit {" + format(money) + "} to {" + player.getName() + "}.");
        return false;
    }

    @Override
    public boolean withdraw(@NotNull OfflinePlayer player, double money) {
        if (api.take(player.getUniqueId(), (int) money)) return true;

        plugin.getLogger().warning("It wasn't possible to withdraw {" + format(money) + "} to {" + player.getName() + "}.");
        return false;
    }

    public String currencyNamePlural() {
        return localeManager.getLocaleMessage("currency-plural");
    }

    public String currencyNameSingular() {
        return localeManager.getLocaleMessage("currency-singular");
    }
}