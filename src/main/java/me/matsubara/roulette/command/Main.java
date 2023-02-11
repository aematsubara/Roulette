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
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public final class Main implements CommandExecutor, TabCompleter {

    private final RoulettePlugin plugin;

    public Main(RoulettePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // This command can't be executed from the console.
        if (!(sender instanceof Player)) {
            plugin.getMessageManager().send(sender, MessageManager.Message.FROM_CONSOLE);
            return true;
        }

        Player player = (Player) sender;

        // No arguments provided.
        if (args.length == 0 || args.length > 3) {
            // If player doesn't have permission to use roulette commands, send @no-permission message.
            if (!hasPermission(player, "roulette.help")) return true;

            // Otherwise, send help message.
            plugin.getMessageManager().send(player, args.length == 0 ? MessageManager.Message.HELP : MessageManager.Message.SINTAX);
            return true;
        }

        if (args.length == 1) {
            switch (args[0].toLowerCase()) {
                case "reload":
                    // If player doesn't have permission to reload, send @no-permission message.
                    if (!hasPermission(player, "roulette.reload")) return true;

                    // Log reloading message.
                    plugin.getLogger().info("Reloading " + plugin.getDescription().getFullName());

                    // Reload main config.
                    plugin.reloadConfig();

                    // Reload winners config.
                    plugin.getWinnerManager().reloadConfig();

                    // Reload chips config.
                    plugin.getChipManager().reloadConfig();

                    // Reload messages config.
                    plugin.getMessageManager().reloadConfig();

                    // Reload games.
                    plugin.getGameManager().reloadConfig();

                    // Send reload messages.
                    plugin.getMessageManager().send(sender, MessageManager.Message.RELOAD);
                    return true;
                case "map":
                    // If player doesn't have permission to get a map, send @no-permission message.
                    if (!hasPermission(player, "roulette.map")) return true;

                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    double randomPrice = random.nextDouble(100000d);

                    Map.Entry<Winner.WinnerData, ItemStack> data = plugin.getWinnerManager().renderMap(
                            player.getName(),
                            new Winner.WinnerData(
                                    "Roulette",
                                    -1,
                                    randomPrice,
                                    System.currentTimeMillis(),
                                    getRandomFromEnum(Slot.class),
                                    getRandomFromEnum(Slot.class),
                                    getRandomFromEnum(WinType.class),
                                    randomPrice / 2));
                    if (data != null) player.getInventory().addItem(data.getValue());

                    return true;
                default:
                    plugin.getMessageManager().send(player, MessageManager.Message.SINTAX);
                    return true;
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
                    plugin.getMessageManager().send(player, MessageManager.Message.DELETE, message -> message.replace("%name%", game.getName()));
                } else {
                    plugin.getMessageManager().send(player, MessageManager.Message.UNKNOWN, message -> message.replace("%name%", args[1]));
                }
            } else {
                plugin.getMessageManager().send(player, MessageManager.Message.SINTAX);
            }
            return true;
        }

        if (!args[0].equalsIgnoreCase("create")) {
            plugin.getMessageManager().send(player, MessageManager.Message.SINTAX);
            return true;
        }

        // If player doesn't have permission to create games, send @no-permission message.
        if (!hasPermission(player, "roulette.create")) return true;

        if (plugin.getGameManager().exist(args[1])) {
            plugin.getMessageManager().send(player, MessageManager.Message.EXIST, message -> message.replace("%name%", args[1]));
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

        plugin.getMessageManager().send(player, MessageManager.Message.CREATE, message -> message.replace("%name%", args[1]));

        return true;
    }

    private <T extends Enum<T>> T getRandomFromEnum(Class<T> clazz) {
        T[] constants = clazz.getEnumConstants();
        return constants[ThreadLocalRandom.current().nextInt(constants.length)];
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], Arrays.asList("create", "delete", "reload", "map"), new ArrayList<>());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("delete")) {
            List<String> games = plugin.getGameManager().getGames().stream().map(Game::getName).collect(Collectors.toList());
            return StringUtil.copyPartialMatches(args[1], games, new ArrayList<>());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            return StringUtil.copyPartialMatches(args[2], Arrays.asList("american", "european"), new ArrayList<>());
        }
        return null;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean hasPermission(Player player, String permission) {
        if (player.hasPermission(permission)) return true;
        plugin.getMessageManager().send(player, MessageManager.Message.NOT_PERMISSION);
        return false;
    }
}