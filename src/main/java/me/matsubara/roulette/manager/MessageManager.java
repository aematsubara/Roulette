package me.matsubara.roulette.manager;

import com.cryptomorin.xseries.messages.ActionBar;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.npc.NPC;
import me.matsubara.roulette.util.PluginUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.UnaryOperator;

public final class MessageManager {

    // Instance of the plugin.
    private static RoulettePlugin plugin;

    // I/O objects.
    private File file;
    private FileConfiguration configuration;

    public MessageManager(RoulettePlugin plugin) {
        MessageManager.plugin = plugin;
        load();
    }

    private void load() {
        file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        configuration = new YamlConfiguration();
        try {
            configuration.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            exception.printStackTrace();
        }
    }

    public void reloadConfig() {
        try {
            configuration = new YamlConfiguration();
            configuration.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            exception.printStackTrace();
        }
    }

    public void send(CommandSender sender, Message message) {
        send(sender, message.getPath(), null);
    }

    public void send(CommandSender sender, Message message, UnaryOperator<String> operator) {
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

    public String getRandomNPCMessage(NPC npc, String type) {
        String npcName = npc.getProfile().getName().equalsIgnoreCase("") ? null : npc.getProfile().getName();
        String message;
        switch (type) {
            case "bets":
                message = getMessage(Message.BETS.asList());
                break;
            case "no-bets":
                message = getMessage(Message.NO_BETS.asList());
                break;
            default:
                message = getMessage(Message.WINNER.asList());
                break;
        }
        if (npcName == null || Message.CROUPIER_PREFIX.asString().equalsIgnoreCase("")) return message;
        return Message.CROUPIER_PREFIX.asString().replace("%croupier%", npcName).concat(message);
    }

    private String getMessage(List<String> list) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return list.get(random.nextInt(list.size()));
    }

    public FileConfiguration getConfig() {
        return configuration;
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
        RELOAD("command.reload"),
        STARTING("game.starting"),
        SELECT_BET("game.select-bet"),
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
        LEAVE_PLAYER("game.leave-player"),
        LA_PARTAGE("game.la-partage"),
        EN_PRISON("game.en-prison"),
        SURRENDER("game.surrender"),
        ALREADY_INGAME("other.already-ingame"),
        ALREADY_STARTED("other.already-started"),
        MIN_REQUIRED("other.min-required"),
        CONFIRM("other.confirm"),
        CONFIRM_LOSE("other.confirm-lose"),
        SELECTED_AMOUNT("other.selected-amount"),
        CONTROL("other.control"),
        ACCOUNT("other.account"),
        NO_ACCOUNT("other.no-account"),
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
        NPC_TEXTURIZED("other.npc-texturized"),
        HELP("help");

        private final String path;

        Message(String path) {
            this.path = path;
        }

        public String asString() {
            return PluginUtils.translate(plugin.getMessageManager().getConfig().getString(path));
        }

        public List<String> asList() {
            return PluginUtils.translate(plugin.getMessageManager().getConfig().getStringList(path));
        }

        public String getPath() {
            return path;
        }
    }
}