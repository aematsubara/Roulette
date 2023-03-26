package me.matsubara.roulette.manager;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.matsubara.roulette.RoulettePlugin;
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
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!player.hasMetadata("rouletteEditing")) return;

        InputType type = players.get(player.getUniqueId());
        if (type == null) return;

        String message = ChatColor.stripColor(event.getMessage());

        if (message.equalsIgnoreCase(ConfigManager.Config.CANCEL_WORD.asString())) {
            plugin.getMessageManager().send(player, MessageManager.Message.REQUEST_CANCELLED);
            event.setCancelled(true);
            remove(player);
            return;
        }

        Game game = plugin.getGameManager().getGame(player.getMetadata("rouletteEditing").get(0).asString());
        if (game == null) return;

        event.setCancelled(true);

        if (type.isAccountName()) {
            if (isInvalidPlayerName(player, message, true)) return;

            @SuppressWarnings("deprecation") OfflinePlayer target = Bukkit.getOfflinePlayer(ChatColor.stripColor(message));
            if (target.hasPlayedBefore()) {
                game.setAccountGiveTo(target.getUniqueId());

                plugin.getMessageManager().send(player, MessageManager.Message.ACCOUNT);
                plugin.getGameManager().save(game);
            } else {
                plugin.getMessageManager().send(player, MessageManager.Message.UNKNOWN_ACCOUNT);
            }
        } else if (type.isCroupierName()) {
            if (isInvalidPlayerName(player, message, false)) return;

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
            plugin.getMessageManager().send(player, MessageManager.Message.NPC_RENAMED);
            plugin.getGameManager().save(game);
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
                    out.writeBytes("url=" + URLEncoder.encode(message, "UTF-8"));
                    out.close();

                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));

                    @SuppressWarnings("deprecation") JsonObject textureObject = new JsonParser().parse(reader)
                            .getAsJsonObject()
                            .getAsJsonObject("data")
                            .getAsJsonObject("texture");

                    String texture = textureObject.get("value").getAsString();
                    String signature = textureObject.get("signature").getAsString();

                    connection.disconnect();

                    String name = game.getNPCName() == null ? "" : game.getNPCName();
                    game.setNPC(name, texture, signature);
                    plugin.getMessageManager().send(player, MessageManager.Message.NPC_TEXTURIZED);
                    plugin.getGameManager().save(game);

                } catch (IOException throwable) {
                    plugin.getMessageManager().send(player, MessageManager.Message.REQUEST_INVALID);
                    throwable.printStackTrace();
                }
            });
        }

        remove(player);
    }

    private boolean isInvalidPlayerName(Player player, String message, boolean checkPattern) {
        boolean invalid = checkPattern ? !message.matches("\\w{3,16}") : message.length() > 16;
        if (!invalid) return false;

        plugin.getMessageManager().send(player, MessageManager.Message.REQUEST_INVALID);
        remove(player);
        return true;
    }

    public void newInput(Player player, InputType type, Game game) {
        players.put(player.getUniqueId(), type);
        player.setMetadata("rouletteEditing", new FixedMetadataValue(plugin, game.getName()));
    }

    public void remove(Player player) {
        players.remove(player.getUniqueId());
        player.removeMetadata("rouletteEditing", plugin);
    }

    public enum InputType {
        ACCOUNT_NAME,
        CROUPIER_NAME,
        CROUPIER_TEXTURE;

        public boolean isAccountName() {
            return this == ACCOUNT_NAME;
        }

        public boolean isCroupierName() {
            return this == CROUPIER_NAME;
        }

        public boolean isCroupierTexture() {
            return this == CROUPIER_TEXTURE;
        }
    }
}
