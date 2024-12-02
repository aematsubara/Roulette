package me.matsubara.roulette.hook.economy;

import lombok.Getter;
import me.matsubara.roulette.RoulettePlugin;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;

public class VaultExtension implements EconomyExtension<VaultExtension> {

    private Economy economy;
    private RoulettePlugin plugin;
    private @Getter boolean enabled;

    @Override
    public VaultExtension init(@NotNull RoulettePlugin plugin) {
        this.plugin = plugin;

        RegisteredServiceProvider<Economy> provider = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (provider == null) {
            plugin.getLogger().severe("Vault found, you need to install an economy provider (EssentialsX, CMI, etc...), disabling economy support...");
            return null;
        }

        Plugin providerPlugin = provider.getPlugin();
        plugin.getLogger().info("Using {" + providerPlugin.getDescription().getFullName() + "} as the economy provider.");

        economy = provider.getProvider();
        enabled = true;
        return this;
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return economy.getBalance(player);
    }

    @Override
    public boolean has(OfflinePlayer player, double money) {
        return economy.has(player, money);
    }

    @Override
    public String format(double money) {
        return economy.format(money);
    }

    @Override
    public boolean deposit(OfflinePlayer player, double money) {
        EconomyResponse response = economy.depositPlayer(player, money);
        if (response.transactionSuccess()) return true;

        plugin.getLogger().warning("It wasn't possible to deposit {" + format(money) + "} to {" + player.getName() + "}.");
        return false;
    }

    @Override
    public boolean withdraw(OfflinePlayer player, double money) {
        EconomyResponse response = economy.withdrawPlayer(player, money);
        if (response.transactionSuccess()) return true;

        plugin.getLogger().warning("It wasn't possible to withdraw {" + format(money) + "} to {" + player.getName() + "}.");
        return false;
    }
}