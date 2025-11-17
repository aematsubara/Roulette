package me.matsubara.roulette.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.data.WinData;
import me.matsubara.roulette.manager.data.PlayerStats;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public final class PAPIExtension extends PlaceholderExpansion {

    private final RoulettePlugin plugin;

    private static final String NULL = "null";

    public PAPIExtension(RoulettePlugin plugin) {
        this.plugin = plugin;

        // Register our placeholder.
        register();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "roulette";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public @NotNull String onPlaceholderRequest(Player player, @NotNull String params) {
        // If somehow the player is null, return empty.
        if (player == null) return NULL;

        // Split the entire parameter with underscores.
        String[] values = params.split("_");

        // We only need 1 or 2 parameters.
        if (values.length == 0 || values.length > 2) return NULL;

        PlayerStats stats = plugin.getDataManager().getStats(player);

        // if parameter doesn't contain underscores.
        if (values.length == 1) {
            return switch (params.toLowerCase(Locale.ROOT)) {
                // %roulette_win% → returns the number of wins.
                case "win" -> stats.getWins(null);
                // %roulette_total% → returns the total amount of money earned.
                case "total" -> stats.getTotalMoney();
                // %roulette_max% → returns the highest amount of money earned.
                case "max" -> stats.getMaxMoney();
                default -> NULL;
            } + "";
        }

        // %roulette_win_{X=partage/prison/surrender}% → returns the number of @X wins.
        if (values[0].equals("win")) {
            WinData.WinType type = WinData.WinType.getByShortName(values[1].toLowerCase(Locale.ROOT));
            return type != null ? stats.getWins(type) + "" : NULL;
        }

        return NULL;
    }
}