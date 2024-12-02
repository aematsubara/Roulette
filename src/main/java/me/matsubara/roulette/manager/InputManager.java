package me.matsubara.roulette.manager;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.file.Config;
import me.matsubara.roulette.file.Messages;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.util.PluginUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class InputManager implements Listener {

    private final RoulettePlugin plugin;
    private final Map<UUID, InputType> players;

    public InputManager(RoulettePlugin plugin) {
        this.plugin = plugin;
        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.players = new HashMap<>();
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onAsyncPlayerChat(@NotNull AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!player.hasMetadata("rouletteEditing")) return;

        InputType type = players.get(player.getUniqueId());
        if (type == null) return;

        String message = ChatColor.stripColor(event.getMessage());
        Messages messages = plugin.getMessages();
        GameManager gameManager = plugin.getGameManager();

        if (message.equalsIgnoreCase(Config.CANCEL_WORD.asString())) {
            messages.send(player, Messages.Message.REQUEST_CANCELLED);
            event.setCancelled(true);
            remove(player);
            return;
        }

        Game game = gameManager.getGame(player.getMetadata("rouletteEditing").get(0).asString());
        if (game == null) return;

        event.setCancelled(true);

        if (type == InputType.ACCOUNT_NAME) {
            if (isInvalidPlayerName(player, message)) return;

            @SuppressWarnings("deprecation") OfflinePlayer target = Bukkit.getOfflinePlayer(ChatColor.stripColor(message));
            if (target.hasPlayedBefore()) {
                game.setAccountGiveTo(target.getUniqueId());

                messages.send(player, Messages.Message.ACCOUNT);
                gameManager.save(game);
            } else {
                messages.send(player, Messages.Message.UNKNOWN_ACCOUNT);
            }
        } else if (type == InputType.CROUPIER_NAME) {
            if (isInvalidPlayerName(player, message)) return;

            // Limited to 16 characters.
            String name = PluginUtils.translate(message);
            if (name.length() > 16) name = name.substring(0, 16);

            // Team shouldn't be null since we created it @onEnable().
            if (game.getNPCName() != null) {
                plugin.getHideTeam().removeEntry(game.getNPCName());
            }

            String texture = game.getNPCTexture();
            String signature = game.getNPCSignature();

            game.setNPC(name, texture, signature);
            messages.send(player, Messages.Message.NPC_RENAMED);
            gameManager.save(game);
        } else {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    URL target = new URL("https://api.mineskin.org/generate/url");

                    HttpURLConnection connection = (HttpURLConnection) target.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setDoOutput(true);
                    connection.setConnectTimeout(1000);
                    connection.setReadTimeout(30000);

                    DataOutputStream out = new DataOutputStream(connection.getOutputStream());
                    out.writeBytes("url=" + URLEncoder.encode(message, StandardCharsets.UTF_8));
                    out.close();

                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));

                    JsonObject textureObject = JsonParser.parseReader(reader)
                            .getAsJsonObject()
                            .getAsJsonObject("data")
                            .getAsJsonObject("texture");

                    reader.close();

                    String texture = textureObject.get("value").getAsString();
                    String signature = textureObject.get("signature").getAsString();

                    connection.disconnect();

                    String name = game.getNPCName() == null ? "" : game.getNPCName();
                    game.setNPC(name, texture, signature);
                    messages.send(player, Messages.Message.NPC_TEXTURIZED);
                    gameManager.save(game);

                } catch (IOException throwable) {
                    messages.send(player, Messages.Message.REQUEST_INVALID);
                    throwable.printStackTrace();
                }
            });
        }

        remove(player);
    }

    private boolean isInvalidPlayerName(Player player, @NotNull String message) {
        if (message.matches("\\w{3,16}")) return false;

        plugin.getMessages().send(player, Messages.Message.REQUEST_INVALID);
        remove(player);
        return true;
    }

    public void newInput(@NotNull Player player, InputType type, @NotNull Game game) {
        players.put(player.getUniqueId(), type);
        player.setMetadata("rouletteEditing", new FixedMetadataValue(plugin, game.getName()));
    }

    public void remove(@NotNull Player player) {
        players.remove(player.getUniqueId());
        player.removeMetadata("rouletteEditing", plugin);
    }

    public enum InputType {
        ACCOUNT_NAME,
        CROUPIER_NAME,
        CROUPIER_TEXTURE
    }
}