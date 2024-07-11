package me.matsubara.roulette.manager;

import com.cryptomorin.xseries.messages.ActionBar;
import lombok.Getter;
import lombok.Setter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.npc.NPC;
import me.matsubara.roulette.util.PluginUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.UnaryOperator;

public final class MessageManager {

    private final RoulettePlugin plugin;

    @Getter
    private @Setter FileConfiguration configuration;

    public MessageManager(RoulettePlugin plugin) {
        this.plugin = plugin;
        this.plugin.saveResource("messages.yml");
    }

    public void send(CommandSender sender, @NotNull Message message) {
        send(sender, message.getPath(), null);
    }

    public void send(CommandSender sender, @NotNull Message message, UnaryOperator<String> operator) {
        send(sender, message.getPath(), operator);
    }

    public void send(CommandSender sender, Message... messages) {
        for (Message message : messages) {
            send(sender, message.getPath(), null);
        }
    }

    public void send(CommandSender sender, String path, @Nullable UnaryOperator<String> operator) {
        if (configuration.get(path) instanceof List) {
            // Multiple messages.
            List<String> messages = configuration.getStringList(path);
            for (String line : messages) {
                String message = PluginUtils.translate(line);
                if (operator != null) message = operator.apply(message);
                sender.sendMessage(message);
            }
        } else {
            // Single line message.
            String message = configuration.getString(path);
            if (message != null) {
                String newMessage = PluginUtils.translate(message.replace("[AB]", ""));
                if (operator != null) newMessage = operator.apply(newMessage);

                if (!message.startsWith("[AB]") || !(sender instanceof Player)) {
                    sender.sendMessage(newMessage);
                    return;
                }
                ActionBar.sendActionBar(plugin, ((Player) sender), newMessage, 50L);
            }
        }
    }

    public String getRandomNPCMessage(@NotNull NPC npc, @NotNull String type) {
        String npcName = npc.getProfile().getName().equalsIgnoreCase("") ? null : npc.getProfile().getName();
        String message = switch (type) {
            case "bets" -> getMessage(Message.BETS.asList());
            case "no-bets" -> getMessage(Message.NO_BETS.asList());
            default -> getMessage(Message.WINNER.asList());
        };
        if (npcName == null || Message.CROUPIER_PREFIX.asString().equalsIgnoreCase("")) return message;
        return Message.CROUPIER_PREFIX.asString().replace("%croupier%", npcName).concat(message);
    }

    private String getMessage(@NotNull List<String> list) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return list.get(random.nextInt(list.size()));
    }

    public enum Message {
        CROUPIER_PREFIX("npc.croupier-prefix"),
        BETS("npc.bets"),
        NO_BETS("npc.no-bets"),
        WINNER("npc.winner"),
        CREATE("command.create"),
        DELETE("command.delete"),
        EXIST("command.exist"),
        UNKNOWN("command.unknown"),
        SINTAX("command.sintax"),
        FROM_CONSOLE("command.from-console"),
        NOT_PERMISSION("command.not-permission"),
        RELOADING("command.reloading"),
        RELOAD("command.reload"),
        STARTING("game.starting"),
        SELECT_BET("game.select-bet"),
        BET_IN_PRISON("game.bet-in-prison"),
        CHANGE_GLOW_COLOR("game.change-glow-color"),
        SPINNING("game.spinning"),
        OUT_OF_TIME("game.out-of-time"),
        YOUR_BET("game.your-bet"),
        SPINNING_START("game.spinning-start"),
        JOIN("game.join"),
        LEAVE("game.leave"),
        NO_WINNER("game.no-winner"),
        WINNERS("game.winners"),
        PRICE("game.price"),
        RESTART("game.restart"),
        PRISON_REMINDER("game.prison-reminder"),
        LEAVE_PLAYER("game.leave-player"),
        LA_PARTAGE("game.la-partage"),
        EN_PRISON("game.en-prison"),
        SURRENDER("game.surrender"),
        ALREADY_INGAME("other.already-ingame"),
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
        MODEL_NOT_LOADED("other.model-not-loaded"),
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
        NPC_ALREADY_TEXTURIZED("other.npc-already-texturized");

        private final RoulettePlugin plugin = JavaPlugin.getPlugin(RoulettePlugin.class);
        private final @Getter String path;

        Message(String path) {
            this.path = path;
        }

        public String asString() {
            return PluginUtils.translate(plugin.getMessageManager().getConfiguration().getString(path));
        }

        public @NotNull List<String> asList() {
            return PluginUtils.translate(plugin.getMessageManager().getConfiguration().getStringList(path));
        }
    }
}