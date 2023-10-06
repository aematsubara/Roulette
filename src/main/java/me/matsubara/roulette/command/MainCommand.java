package me.matsubara.roulette.command;

import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.GameType;
import me.matsubara.roulette.game.WinType;
import me.matsubara.roulette.game.data.Slot;
import me.matsubara.roulette.manager.ConfigManager;
import me.matsubara.roulette.manager.MessageManager;
import me.matsubara.roulette.manager.winner.Winner;
import me.matsubara.roulette.util.PluginUtils;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class MainCommand implements CommandExecutor, TabCompleter {

    private final RoulettePlugin plugin;

    private static final List<String> COMMAND_ARGS = List.of("create", "delete", "reload", "map");
    private static final List<String> TABLE_NAME_ARG = List.of("<name>");
    private static final List<String> TYPES = List.of("american", "european");
    private static final List<String> HELP = Stream.of(
            "&8&m--------------------------------------------------",
            "&6&lRoulette &f&oCommands &c(optional) <required>",
            "&e/roulette create <name> <type> &f- &7Create a new roulette.",
            "&e/roulette delete <name> &f- &7Delete a game.",
            "&e/roulette reload &f- &7Reload configuration files.",
            "&e/roulette map &f- &7Gives a win voucher.",
            "&8&m--------------------------------------------------").map(PluginUtils::translate).toList();

    public MainCommand(RoulettePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // If player doesn't have permission to use roulette commands, send @no-permission message.
        if (!hasPermission(sender, "roulette.help")) return true;

        MessageManager messages = plugin.getMessageManager();

        // This command can't be executed from the console.
        if (!(sender instanceof Player player)) {
            messages.send(sender, MessageManager.Message.FROM_CONSOLE);
            return true;
        }

        // No arguments provided.
        boolean noArgs = args.length == 0;
        if (noArgs || args.length > 3 || !COMMAND_ARGS.contains(args[0].toLowerCase())) {
            // Otherwise, send help message.
            if (noArgs) HELP.forEach(player::sendMessage);
            else messages.send(player, MessageManager.Message.SINTAX);
            return true;
        }

        if (args.length == 1) {
            switch (args[0].toLowerCase()) {
                case "reload" -> {
                    // If player doesn't have permission to reload, send @no-permission message.
                    if (!hasPermission(player, "roulette.reload")) return true;

                    // Log reloading message.
                    plugin.getLogger().info("Reloading " + plugin.getDescription().getFullName());

                    messages.send(sender, MessageManager.Message.RELOADING);
                    CompletableFuture.runAsync(plugin::updateConfigs).thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                        plugin.reloadAbbreviations();

                        // Reload winners config.
                        plugin.getWinnerManager().reloadConfig();

                        // Reload chips config.
                        plugin.getChipManager().reloadConfig();

                        // Reload games.
                        plugin.getGameManager().reloadConfig();

                        // Send reload messages.
                        messages.send(sender, MessageManager.Message.RELOAD);
                    }));
                    return true;
                }
                case "map" -> {
                    // If player doesn't have permission to get a map, send @no-permission message.
                    if (!hasPermission(player, "roulette.map")) return true;
                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    double randomPrice = random.nextDouble(100000d);
                    Map.Entry<Winner.WinnerData, ItemStack> data = plugin.getWinnerManager().render(
                            player.getName(),
                            new Winner.WinnerData(
                                    "Roulette",
                                    -1,
                                    randomPrice,
                                    System.currentTimeMillis(),
                                    getRandomFromEnum(Slot.class),
                                    getRandomFromEnum(Slot.class),
                                    getRandomFromEnum(WinType.class),
                                    randomPrice / 2),
                            null);
                    if (data != null) player.getInventory().addItem(data.getValue());
                    return true;
                }
                default -> {
                    messages.send(player, MessageManager.Message.SINTAX);
                    return true;
                }
            }
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("delete")) {
                // If player doesn't have permission to delete games, send @no-permission message.
                if (!hasPermission(player, "roulette.delete")) return true;

                Game game = plugin.getGameManager().getGame(args[1]);
                if (game != null) {
                    // If player doesn't have permission to delete games from other players, send @no-permission message.
                    if (!game.getOwner().equals(player.getUniqueId()) && !hasPermission(player, "roulette.delete.others")) {
                        return true;
                    }

                    plugin.getGameManager().deleteGame(game);
                    messages.send(player, MessageManager.Message.DELETE, message -> message.replace("%name%", game.getName()));
                } else {
                    messages.send(player, MessageManager.Message.UNKNOWN, message -> message.replace("%name%", args[1]));
                }
            } else {
                messages.send(player, MessageManager.Message.SINTAX);
            }
            return true;
        }

        if (!args[0].equalsIgnoreCase("create")) {
            messages.send(player, MessageManager.Message.SINTAX);
            return true;
        }

        // If player doesn't have permission to create games, send @no-permission message.
        if (!hasPermission(player, "roulette.create")) return true;

        if (plugin.getGameManager().exist(args[1])) {
            messages.send(player, MessageManager.Message.EXIST, message -> message.replace("%name%", args[1]));
            return true;
        }

        // The model was created looking to the opposite side, so removing BlockFace#getOppositeFace() will work.
        Location location = player
                .getTargetBlock(null, 15)
                .getLocation()
                .add(0.5d, 1.0d, 0.5d)
                .setDirection(PluginUtils.getDirection(PluginUtils.getFace(player.getLocation().getYaw(), false)));

        // Get the type of the roulette, or AMERICAN by default.
        GameType type;
        try {
            type = GameType.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException exception) {
            type = GameType.AMERICAN;
        }

        plugin.getGameManager().add(
                args[1],
                1,
                10,
                type,
                UUID.randomUUID(),
                location,
                player.getUniqueId(),
                ConfigManager.Config.COUNTDOWN_WAITING.asInt());

        messages.send(player, MessageManager.Message.CREATE, message -> message.replace("%name%", args[1]));
        return true;
    }

    private <T extends Enum<T>> T getRandomFromEnum(@NotNull Class<T> clazz) {
        T[] constants = clazz.getEnumConstants();
        return constants[ThreadLocalRandom.current().nextInt(constants.length)];
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String @NotNull [] args) {
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], COMMAND_ARGS, new ArrayList<>());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            return StringUtil.copyPartialMatches(args[1], TABLE_NAME_ARG, new ArrayList<>());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("delete")) {
            return StringUtil.copyPartialMatches(
                    args[1],
                    plugin.getGameManager().getGames().stream()
                            .map(Game::getName)
                            .collect(Collectors.toList()),
                    new ArrayList<>());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            return StringUtil.copyPartialMatches(args[2], TYPES, new ArrayList<>());
        }

        return Collections.emptyList();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean hasPermission(@NotNull CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) return true;
        plugin.getMessageManager().send(sender, MessageManager.Message.NOT_PERMISSION);
        return false;
    }
}