package me.matsubara.roulette.command;

import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.file.Config;
import me.matsubara.roulette.file.Messages;
import me.matsubara.roulette.file.config.ConfigValue;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.GameType;
import me.matsubara.roulette.game.data.Slot;
import me.matsubara.roulette.game.data.WinData;
import me.matsubara.roulette.game.state.Spinning;
import me.matsubara.roulette.gui.data.SessionsGUI;
import me.matsubara.roulette.manager.GameManager;
import me.matsubara.roulette.manager.data.PlayerResult;
import me.matsubara.roulette.manager.data.RouletteSession;
import me.matsubara.roulette.util.PluginUtils;
import org.apache.commons.lang3.ArrayUtils;
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

    private static final List<String> COMMAND_ARGS = List.of("create", "delete", "reload", "sessions", "map", "force");
    private static final List<String> TABLE_NAME_ARG = List.of("<name>");
    private static final List<String> TYPES = List.of("american", "european");
    private static final List<String> HELP = Stream.of(
                    "&8&m--------------------------------------------------",
                    "&6&lRoulette &f&oCommands &c<required> | [optional]",
                    "&e/roulette create <name> <type> &f- &7Create a new game.",
                    "&e/roulette delete <name> &f- &7Delete a game.",
                    "&e/roulette sessions &f- &7Open the sessions menu.",
                    "&e/roulette reload &f- &7Reload configuration files.",
                    "&e/roulette map &f- &7Gives a win voucher.",
                    "&e/roulette force <slot> &f- &7Force the winning slot.",
                    "&8&m--------------------------------------------------")
            .map(PluginUtils::translate)
            .toList();

    public MainCommand(RoulettePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // If the player doesn't have permission to use roulette commands, send (@no-permission) message.
        if (!hasPermission(sender, "roulette.help")) return true;

        Messages messages = plugin.getMessages();

        // No arguments provided.
        boolean noArgs = args.length == 0;
        if (noArgs || args.length > 3 || (!COMMAND_ARGS.contains(args[0].toLowerCase(Locale.ROOT)))) {
            // Otherwise, send a help message.
            if (noArgs) HELP.forEach(sender::sendMessage);
            else messages.send(sender, Messages.Message.SINTAX);
            return true;
        }

        GameManager manager = plugin.getGameManager();

        if (args.length == 1) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "sessions" -> {
                    // This command can't be executed from the console.
                    Player player = getPlayerFromSender(sender);
                    if (player == null) return true;

                    // If the player doesn't have permission to open the session menu, send (@no-permission) message.
                    if (!hasPermission(player, "roulette.sessions")) return true;

                    if (plugin.getDataManager().getSessions().isEmpty()) {
                        messages.send(player, Messages.Message.SESSION_EMPTY);
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

                    messages.send(sender, Messages.Message.RELOADING);
                    CompletableFuture.runAsync(plugin::updateConfigs).thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                        ConfigValue.ALL_VALUES.forEach(ConfigValue::reloadValue);

                        plugin.resetEconomyProvider();
                        plugin.reloadAbbreviations();

                        // Reload chips config.
                        plugin.getChipManager().reloadConfig();

                        // Reload games.
                        manager.reloadConfig();
                        manager.getModels().clear();

                        // Send reload messages.
                        messages.send(sender, Messages.Message.RELOAD);
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
                            GameType.AMERICAN,
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
                    messages.send(sender, Messages.Message.SINTAX);
                    return true;
                }
            }
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("delete")) {
                // If the player doesn't have permission to delete games, send (@no-permission) message.
                if (!hasPermission(sender, "roulette.delete")) return true;

                Game game = manager.getGame(args[1]);
                if (game == null) {
                    messages.send(sender, Messages.Message.UNKNOWN, message -> message.replace("%name%", args[1]));
                    return true;
                }

                // If the sender is a player and doesn't have permission to delete games from other players,
                // send (@no-permission) message.
                if (sender instanceof Player player
                        && !game.getOwner().equals(player.getUniqueId())
                        && !hasPermission(player, "roulette.delete.others")) {
                    return true;
                }

                manager.deleteGame(game);
                messages.send(sender, Messages.Message.DELETE, message -> message.replace("%name%", game.getName()));
                return true;
            }

            if (!args[0].equalsIgnoreCase("force")) {
                messages.send(sender, Messages.Message.SINTAX);
                return true;
            }

            // If the player doesn't have permission to force, send (@no-permission) message.
            if (!hasPermission(sender, "roulette.force")) return true;

            Player player = getPlayerFromSender(sender);
            if (player == null) return true;

            Game game = manager.getGameByPlayer(player);
            if (game == null) {
                messages.send(sender, Messages.Message.FORCE_NOT_PLAYING);
                return true;
            }

            Spinning spinning = game.getSpinning();
            if (spinning == null || spinning.isCancelled()) {
                messages.send(player, Messages.Message.FORCE_NOT_SPINNING);
                return true;
            }

            Slot slot = PluginUtils.getOrNull(Slot.class, args[1]);
            if (!ArrayUtils.contains(Slot.singleValues(game).toArray(), slot)) {
                messages.send(player, Messages.Message.FORCE_UNKNOWN_SLOT);
                return true;
            }

            spinning.setForce(slot);
            spinning.setForcedBy(player);

            messages.send(player, Messages.Message.FORCE_SLOT_CHANGED, message -> message.replace("%slot%", PluginUtils.getSlotName(slot)));
            return true;
        }

        if (!args[0].equalsIgnoreCase("create")) {
            messages.send(sender, Messages.Message.SINTAX);
            return true;
        }

        Player player = getPlayerFromSender(sender);
        if (player == null) return true;

        // If the player doesn't have permission to create games, send (@no-permission) message.
        if (!hasPermission(player, "roulette.create")) return true;

        // "." cannot be used in the name as it creates conflicts with YAML.
        String name = args[1];
        if (name.contains(".")) {
            messages.send(player, Messages.Message.INVALID_NAME);
            return true;
        }

        // A game with the same name already exists.
        if (manager.exist(name)) {
            messages.send(player, Messages.Message.EXIST, message -> message.replace("%name%", name));
            return true;
        }

        // Get the type of the roulette, or AMERICAN by default.
        GameType type;
        try {
            type = GameType.valueOf(args[2].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            messages.send(player, Messages.Message.INVALID_TYPE);
            return true;
        }

        // The model was created looking to the opposite side, so removing BlockFace#getOppositeFace() will work.
        Location location = player
                .getTargetBlock(null, 15)
                .getLocation()
                .add(0.5d, 1.0d, 0.5d)
                .setDirection(PluginUtils.getDirection(PluginUtils.getFace(player.getLocation().getYaw())));

        manager.addFreshGame(
                name,
                1,
                10,
                type,
                UUID.randomUUID(),
                location,
                player.getUniqueId(),
                Config.COUNTDOWN_WAITING.asInt());

        messages.send(player, Messages.Message.CREATE, message -> message.replace("%name%", name));
        return true;
    }

    private @Nullable Player getPlayerFromSender(CommandSender sender) {
        if (sender instanceof Player player) return player;
        plugin.getMessages().send(sender, Messages.Message.FROM_CONSOLE);
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

        GameManager manager = plugin.getGameManager();

        if (args.length == 2 && args[0].equalsIgnoreCase("force") && sender instanceof Player player) {
            Game game = manager.getGameByPlayer(player);
            if (game == null) return Collections.emptyList();

            List<String> slots = Slot.singleValues(game).map(Enum::name).toList();
            return StringUtil.copyPartialMatches(args[1], slots, new ArrayList<>());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("delete")) {
            return StringUtil.copyPartialMatches(
                    args[1],
                    manager.getGames().stream()
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
        plugin.getMessages().send(sender, Messages.Message.NOT_PERMISSION);
        return false;
    }
}