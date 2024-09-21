package me.matsubara.roulette.command;

import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.GameType;
import me.matsubara.roulette.game.data.Slot;
import me.matsubara.roulette.game.data.WinData;
import me.matsubara.roulette.gui.data.SessionsGUI;
import me.matsubara.roulette.manager.ConfigManager;
import me.matsubara.roulette.manager.MessageManager;
import me.matsubara.roulette.manager.data.PlayerResult;
import me.matsubara.roulette.manager.data.RouletteSession;
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
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class MainCommand implements CommandExecutor, TabCompleter {

    private final RoulettePlugin plugin;

    private static final List<String> COMMAND_ARGS = List.of("create", "delete", "reload", "sessions", "map");
    private static final List<String> TABLE_NAME_ARG = List.of("<name>");
    private static final List<String> TYPES = List.of("american", "european");
    private static final List<String> HELP = Stream.of(
            "&8&m--------------------------------------------------",
            "&6&lRoulette &f&oCommands &c<required> | [optional]",
            "&e/roulette create <name> <type> &f- &7Create a new roulette.",
            "&e/roulette delete <name> &f- &7Delete a game.",
            "&e/roulette sessions &f- &7Open the sessions menu.",
            "&e/roulette reload &f- &7Reload configuration files.",
            "&e/roulette map &f- &7Gives a win voucher.",
            "&8&m--------------------------------------------------").map(PluginUtils::translate).toList();

    public MainCommand(RoulettePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // If the player doesn't have permission to use roulette commands, send (@no-permission) message.
        if (!hasPermission(sender, "roulette.help")) return true;

        MessageManager messages = plugin.getMessageManager();

        // No arguments provided.
        boolean noArgs = args.length == 0;
        if (noArgs || args.length > 3 || (!COMMAND_ARGS.contains(args[0].toLowerCase(Locale.ROOT)))) {
            // Otherwise, send a help message.
            if (noArgs) HELP.forEach(sender::sendMessage);
            else messages.send(sender, MessageManager.Message.SINTAX);
            return true;
        }

        if (args.length == 1) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "sessions" -> {
                    // This command can't be executed from the console.
                    Player player = getPlayerFromSender(sender);
                    if (player == null) return true;

                    // If the player doesn't have permission to open the session menu, send (@no-permission) message.
                    if (!hasPermission(player, "roulette.sessions")) return true;

                    if (plugin.getDataManager().getSessions().isEmpty()) {
                        messages.send(player, MessageManager.Message.SESSION_EMPTY);
                        return true;
                    }

                    new SessionsGUI(plugin, player);
                    return true;
                }
                case "reload" -> {
                    // If the player doesn't have permission to reload, send (@no-permission) message.
                    if (!hasPermission(sender, "roulette.reload")) return true;

                    // Log reloading message.
                    plugin.getLogger().info("Reloading " + plugin.getDescription().getFullName());

                    messages.send(sender, MessageManager.Message.RELOADING);
                    CompletableFuture.runAsync(plugin::updateConfigs).thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                        plugin.reloadAbbreviations();

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
                    // This command can't be executed from the console.
                    Player player = getPlayerFromSender(sender);
                    if (player == null) return true;

                    // If the player doesn't have permission to get a map, send (@no-permission) message.
                    if (!hasPermission(player, "roulette.map")) return true;

                    // Dummy session.
                    RouletteSession session = new RouletteSession(UUID.randomUUID(),
                            "Roulette",
                            new ArrayList<>(),
                            Slot.SLOT_0,
                            System.currentTimeMillis());

                    // Dummy result.
                    PlayerResult result = new PlayerResult(session,
                            player.getUniqueId(),
                            PluginUtils.getRandomFromEnum(WinData.WinType.class),
                            PluginUtils.RANDOM.nextInt(100000),
                            PluginUtils.getRandomFromEnum(Slot.class));

                    session.results().add(result);

                    Map.Entry<Integer, ItemStack> data = plugin.getWinnerManager().render(player.getUniqueId(), session);
                    if (data != null) player.getInventory().addItem(data.getValue());
                    return true;
                }
                default -> {
                    messages.send(sender, MessageManager.Message.SINTAX);
                    return true;
                }
            }
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("delete")) {
                // If the player doesn't have permission to delete games, send (@no-permission) message.
                if (!hasPermission(sender, "roulette.delete")) return true;

                Game game = plugin.getGameManager().getGame(args[1]);
                if (game != null) {
                    // If the sender is a player and doesn't have permission to delete games from other players,
                    // send (@no-permission) message.
                    if (sender instanceof Player player
                            && !game.getOwner().equals(player.getUniqueId())
                            && !hasPermission(player, "roulette.delete.others")) {
                        return true;
                    }

                    plugin.getGameManager().deleteGame(game);
                    messages.send(sender, MessageManager.Message.DELETE, message -> message.replace("%name%", game.getName()));
                } else {
                    messages.send(sender, MessageManager.Message.UNKNOWN, message -> message.replace("%name%", args[1]));
                }
            } else {
                messages.send(sender, MessageManager.Message.SINTAX);
            }
            return true;
        }

        if (!args[0].equalsIgnoreCase("create")) {
            messages.send(sender, MessageManager.Message.SINTAX);
            return true;
        }

        Player player = getPlayerFromSender(sender);
        if (player == null) return true;

        // If the player doesn't have permission to create games, send (@no-permission) message.
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
            type = GameType.valueOf(args[2].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            type = GameType.AMERICAN;
        }

        plugin.getGameManager().addFreshGame(
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

    private @Nullable Player getPlayerFromSender(CommandSender sender) {
        if (sender instanceof Player player) return player;
        plugin.getMessageManager().send(sender, MessageManager.Message.FROM_CONSOLE);
        return null;
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