package me.matsubara.roulette.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.data.WinData;
import me.matsubara.roulette.manager.data.PlayerResult;
import me.matsubara.roulette.manager.data.RouletteSession;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;
import java.util.stream.DoubleStream;

public final class PAPIExtension extends PlaceholderExpansion {

    private final RoulettePlugin plugin;

    private static final String NULL = "null";
    private static final List<String> WIN_TYPE = List.of("partage", "prison", "surrender");
    private static final BiPredicate<Player, PlayerResult> WIN_RESULT = (player, result) -> result.playerUUID().equals(player.getUniqueId())
            && result.won();

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

        List<RouletteSession> sessions = plugin.getDataManager().getSessions();

        // if parameter doesn't contain underscores.
        if (values.length == 1) {
            switch (params.toLowerCase()) {
                // %roulette_win% → returns the number of wins.
                case "win" -> {
                    return String.valueOf(sessions.stream()
                            .mapToInt(session -> (int) session.results().stream()
                                    .filter(result -> WIN_RESULT.test(player, result))
                                    .count())
                            .sum());
                }
                // %roulette_total% → returns the total amount of money earned.
                case "total" -> {
                    return String.valueOf(sessions.stream()
                            .mapToDouble(session -> mapToWinningDouble(player, session).sum())
                            .sum());
                }
                // %roulette_max% → returns the highest amount of money earned.
                case "max" -> {
                    return String.valueOf(sessions.stream()
                            .mapToDouble(session -> mapToWinningDouble(player, session).max().orElse(0))
                            .max());
                }
            }
        } else if (values[0].equalsIgnoreCase("win")) {
            String type = values[1].toLowerCase();
            if (!WIN_TYPE.contains(type)) return NULL;

            // %roulette_win_{X=partage/prison/surrender}% → returns the number of @X wins.
            return String.valueOf(sessions.stream()
                    .mapToInt(session -> (int) session.results().stream()
                            .filter(result -> WIN_RESULT.test(player, result)
                                    && type.equals(Objects.requireNonNullElse(result.win(), WinData.WinType.NORMAL).getShortName()))
                            .count())
                    .sum());
        }

        return NULL;
    }

    private DoubleStream mapToWinningDouble(Player player, @NotNull RouletteSession session) {
        return mapToWinningDouble(player, session, (temp, result) -> true);
    }

    private DoubleStream mapToWinningDouble(Player player, @NotNull RouletteSession session, BiPredicate<Player, PlayerResult> and) {
        return session.results().stream()
                .filter(result -> WIN_RESULT.and(and).test(player, result))
                .mapToDouble(plugin::getExpectedMoney);
    }
}