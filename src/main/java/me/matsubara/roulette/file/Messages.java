package me.matsubara.roulette.file;

import com.cryptomorin.xseries.messages.ActionBar;
import lombok.Getter;
import lombok.Setter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.util.PluginUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.UnaryOperator;

@Getter
@Setter
public final class Messages {

    private final RoulettePlugin plugin;
    private FileConfiguration configuration;

    private static final UnaryOperator<String> IDENTITY = UnaryOperator.identity();

    public Messages(RoulettePlugin plugin) {
        this.plugin = plugin;
        this.plugin.saveResource("messages.yml");
    }

    public void send(CommandSender sender, @NotNull Message message) {
        send(sender, message, null);
    }

    public void send(CommandSender sender, @NotNull Message message, @Nullable UnaryOperator<String> operator) {
        String path = message.getPath();
        if (configuration.get(path) instanceof List) {
            for (String line : configuration.getStringList(path)) {
                sender.sendMessage(operator(operator).apply(PluginUtils.translate(line)));
            }
            return;
        }
        sendSingleMessage(sender, configuration.getString(path), operator);
    }

    public void sendNPCMessage(CommandSender sender, @NotNull Game game, @NotNull Message message) {
        sendNPCMessage(sender, game, message, null);
    }

    public void sendNPCMessage(CommandSender sender, @NotNull Game game, @NotNull Message message, @Nullable UnaryOperator<String> operator) {
        sendSingleMessage(sender, getNPCMessage(game, message), operator);
    }

    private void sendSingleMessage(CommandSender sender, @Nullable String message, @Nullable UnaryOperator<String> operator) {
        if (message == null) return;

        String newMessage = (operator(operator)).apply(PluginUtils.translate(message.replace("[AB]", "")));

        if (!message.startsWith("[AB]") || !(sender instanceof Player player)) {
            sender.sendMessage(newMessage);
            return;
        }

        new BukkitRunnable() {
            long repeater = 50L;

            @Override
            public void run() {
                ActionBar.sendActionBar(player, newMessage);
                repeater -= 40L;
                if (repeater - 40L < -20L) cancel();
            }
        }.runTaskTimerAsynchronously(plugin, 0L, 40L);
    }

    private String getNPCMessage(@NotNull Game game, @NotNull Message message) {
        List<String> messages = message.asList();
        String random = messages.get(PluginUtils.RANDOM.nextInt(messages.size()));

        String name = game.getNPCName(), prefix = Message.CROUPIER_PREFIX.asString();
        if (name == null || prefix.equalsIgnoreCase("")) return random;

        return prefix.replace("%croupier%", name).concat(random);
    }

    private UnaryOperator<String> operator(@Nullable UnaryOperator<String> operator) {
        return operator != null ? operator : IDENTITY;
    }

    public enum Message {
        CROUPIER_PREFIX("npc.croupier-prefix"),
        BETS("npc.bets"),
        NO_BETS("npc.no-bets"),
        WINNER("npc.winner"),
        INVITE("npc.invite"),
        CREATE("command.create"),
        DELETE("command.delete"),
        EXIST("command.exist"),
        INVALID_NAME("command.invalid-name"),
        INVALID_TYPE("command.invalid-type"),
        UNKNOWN("command.unknown"),
        SINTAX("command.sintax"),
        FROM_CONSOLE("command.from-console"),
        NOT_PERMISSION("command.not-permission"),
        RELOADING("command.reloading"),
        RELOAD("command.reload"),
        FORCE_NOT_PLAYING("command.force.not-playing"),
        FORCE_NOT_SPINNING("command.force.not-spinning"),
        FORCE_UNKNOWN_SLOT("command.force.unknown-slot"),
        FORCE_SLOT_CHANGED("command.force.slot-changed"),
        SESSION_RESULT_REMOVED("session.result-removed"),
        SESSION_LOST_RECOVERED("session.lost-recovered"),
        SESSION_BET_IN_PRISON("session.bet-in-prison"),
        SESSION_BET_REVERTED("session.bet-reverted"),
        SESSION_TRANSACTION_COMPLETED("session.transaction-completed"),
        SESSION_TRANSACTION_FAILED("session.transaction-failed"),
        SESSION_EMPTY("session.empty"),
        NO_ECONOMY_PROVIDER("game.no-economy-provider"),
        STARTING("game.starting"),
        NO_MORE_SLOTS("game.no-more-slots"),
        SELECT_BET("game.select-bet"),
        BET_IN_PRISON("game.bet-in-prison"),
        BET_SELECTED("game.bet-selected"),
        BET_ALREADY_SELECTED("game.bet-already-selected"),
        AT_LEAST_ONE_BET_REQUIRED("game.at-least-one-bet-required"),
        BET_REMOVED("game.bet-removed"),
        YOU_ARE_DONE("game.you-are-done"),
        PLAYER_DONE("game.player-done"),
        ALL_PLAYERS_DONE("game.all-players-done"),
        EXTRA_TIME_ADDED("game.extra-time-added"),
        SPINNING("game.spinning"),
        OUT_OF_TIME("game.out-of-time"),
        YOUR_BETS("game.your-bets"),
        BET_HOVER("game.bet-hover"),
        JOIN("game.join"),
        LEAVE("game.leave"),
        NO_WINNER("game.no-winner"),
        ALL_WINNERS("game.all-winners"),
        WINNER_HOVER("game.winner-hover"),
        YOUR_WINNING_BETS("game.your-winning-bets"),
        WINNING_BET_HOVER("game.winning-bet-hover"),
        NO_WINNING_BETS("game.no-winning-bets"),
        RESTART("game.restart"),
        PRISON_REMINDER("game.prison-reminder"),
        LEAVE_PLAYER("game.leave-player"),
        ALREADY_INGAME("other.already-ingame"),
        ALREADY_PLAYING("other.already-playing"),
        INSIDE_VEHICLE("other.inside-vehicle"),
        ALREADY_STARTED("other.already-started"),
        SEAT_TAKEN("other.seat-taken"),
        GAME_STOPPED("other.game-stopped"),
        MIN_REQUIRED("other.min-required"),
        CONFIRM("other.confirm"),
        CONFIRM_LOSE("other.confirm-lose"),
        SELECTED_AMOUNT("other.selected-amount"),
        CONTROL("other.control"),
        ACCOUNT("other.account"),
        NO_ACCOUNT("other.no-account"),
        ACCOUNT_ALREADY_DELETED("other.account-already-deleted"),
        UNKNOWN_ACCOUNT("other.unknown-account"),
        RECEIVED("other.received"),
        VANISH("other.vanish"),
        FULL("other.full"),
        RESTARTING("other.restarting"),
        ONLY_AMERICAN("other.only-american"),
        PRISON_ERROR("other.prison-error"),
        CAN_NOT_HIT("other.can-not-hit"),
        ACCOUNT_NAME("other.account-name"),
        NPC_NAME("other.npc-name"),
        NPC_TEXTURE("other.npc-texture"),
        REQUEST_CANCELLED("other.request-cancelled"),
        REQUEST_INVALID("other.request-invalid"),
        NPC_RENAMED("other.npc-renamed"),
        NPC_ALREADY_RENAMED("other.npc-already-renamed"),
        NPC_TEXTURIZED("other.npc-texturized"),
        NPC_ALREADY_TEXTURIZED("other.npc-already-texturized"),
        AT_LEAST_ONE_CHIP_REQUIRED("other.at-least-one-chip-required");

        private final RoulettePlugin plugin = JavaPlugin.getPlugin(RoulettePlugin.class);
        private final @Getter String path;

        Message(String path) {
            this.path = path;
        }

        public @NotNull String asString() {
            return PluginUtils.translate(plugin.getMessages().getConfiguration().getString(path));
        }

        public @NotNull List<String> asList() {
            return PluginUtils.translate(plugin.getMessages().getConfiguration().getStringList(path));
        }
    }
}