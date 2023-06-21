package me.matsubara.roulette.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.manager.winner.Winner;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public final class PAPIExtension extends PlaceholderExpansion {

    private final RoulettePlugin plugin;

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
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        // If somehow the player is null, return empty.
        if (player == null) return "";

        // Split the entire parameter with underscores.
        String[] values = params.split("_");

        // We only need 1 or 2 parameters.
        if (values.length == 0 || values.length > 2) return "";

        // Get the winner object by the player UUID, can be null.
        Winner winner = plugin.getWinnerManager().getByUniqueId(player.getUniqueId());

        // if parameter doesn't contain underscores.
        if (values.length == 1) {

            switch (params.toLowerCase()) {
                // Return the amount of wins of a player (%roulette_wins%).
                case "wins":
                    // If the winner is null, return 0.
                    if (winner == null) return "0";

                    return String.valueOf(winner.getWinnerData().size());
                // Returns all winnings of a player (%roulette_winnings%).
                case "winnings":
                    // If the winner is null, return 0.
                    if (winner == null) return "0";

                    return String.valueOf(winner.getWinnerData()
                            .stream()
                            .mapToDouble(Winner.WinnerData::getMoney)
                            .sum());
            }
        } else {
            if (values[0].equalsIgnoreCase("wins")) {
                String type = values[1].toLowerCase();

                if (!Arrays.asList("partage", "prison", "surrender").contains(type)) return "";
                if (winner == null) return "0";

                return String.valueOf(winner.getWinnerData()
                        .stream()
                        .filter(winnerData -> winnerData.getType().name().toLowerCase().contains(type))
                        .count());
            }
        }

        return "";
    }
}