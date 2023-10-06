package me.matsubara.roulette.gui;

import com.comphenix.protocol.wrappers.WrappedSignedProperty;
import com.cryptomorin.xseries.XMaterial;
import com.google.gson.JsonParser;
import lombok.Getter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.GameRule;
import me.matsubara.roulette.manager.ConfigManager;
import me.matsubara.roulette.manager.winner.Winner;
import me.matsubara.roulette.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

@Getter
public final class GameGUI implements InventoryHolder, RouletteGUI {

    // Instance of the plugin.
    private final RoulettePlugin plugin;

    // The game that is being edited.
    private final Game game;

    // Inventoy being used.
    private final Inventory inventory;

    // Task id used for changing the winning slots.
    private int taskId;

    public GameGUI(RoulettePlugin plugin, @NotNull Game game, @NotNull Player player) {
        this.plugin = plugin;
        this.game = game;
        this.inventory = Bukkit.createInventory(
                this,
                27,
                ConfigManager.Config.GAME_MENU_TITLE.asString().replace("%name%", game.getName()));

        // Fill inventory.
        fillInventory();

        // Open inventory.
        player.openInventory(inventory);
    }

    @SuppressWarnings("deprecation")
    private void fillInventory() {
        ItemStack background = new ItemBuilder(XMaterial.GRAY_STAINED_GLASS_PANE.parseItem()).setDisplayName("&7").build();
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, background);
        }

        String npcName = game.getNPCName();
        if (npcName == null) npcName = ConfigManager.Config.UNNAMED_CROUPIER.asString();

        String url = null;
        if (!game.getNpc().getProfile().getProperties().get("textures").isEmpty()) {
            for (WrappedSignedProperty property : game.getNpc().getProfile().getProperties().get("textures")) {
                url = property.getValue();
                break;
            }

            // Decode base64.
            String base64 = new String(Base64.getDecoder().decode(url));

            // Get url from json.
            url = new JsonParser().parse(base64).getAsJsonObject()
                    .getAsJsonObject("textures")
                    .getAsJsonObject("SKIN")
                    .get("url")
                    .getAsString();
        }

        ConfigManager configManager = plugin.getConfigManager();

        ItemBuilder croupierBuilder = ((url == null) ?
                new ItemBuilder(XMaterial.PLAYER_HEAD.parseItem()) :
                new ItemBuilder(url, true))
                .setDisplayName(configManager.getDisplayName("game-menu", "croupier").replace("%croupier-name%", npcName))
                .setLore(configManager.getLore("game-menu", "croupier"));

        inventory.setItem(0, croupierBuilder.build());

        ItemStack account = new ItemBuilder(XMaterial.PLAYER_HEAD.parseItem()).setLore(configManager.getAccountLore()).build();
        ItemStack noAccount = configManager.getItem("game-menu", "no-account", null);

        OfflinePlayer accountTo = game.getAccountGiveTo() != null ? Bukkit.getOfflinePlayer(game.getAccountGiveTo()) : null;

        inventory.setItem(10, (accountTo != null) ? new ItemBuilder(account)
                .setOwningPlayer(accountTo)
                .setDisplayName(configManager.getAccountDisplayName(accountTo.getName()))
                .build() : noAccount);

        int minAmount = game.getMinPlayers(), maxAmount = game.getMaxPlayers();

        ItemStack min = configManager.getItem("game-menu", "min-amount", null);
        inventory.setItem(11, new ItemBuilder(min).setAmount(minAmount).build());

        ItemStack max = configManager.getItem("game-menu", "max-amount", null);
        inventory.setItem(12, new ItemBuilder(max).setAmount(maxAmount).build());

        int timeSeconds = game.getStartTime();
        ItemStack time = configManager.getItem("game-menu", "start-time", null);
        inventory.setItem(13, new ItemBuilder(time)
                .setDisplayName(configManager.getStartTimeDisplayName(timeSeconds))
                .setAmount(timeSeconds).build());

        // Rules.
        inventory.setItem(14, getRuleItem(GameRule.LA_PARTAGE));
        inventory.setItem(15, getRuleItem(GameRule.EN_PRISON));
        inventory.setItem(16, getRuleItem(GameRule.SURRENDER));

        String state = game.isBetAll() ? ConfigManager.Config.STATE_ENABLED.asString() : ConfigManager.Config.STATE_DISABLED.asString();
        inventory.setItem(8, new ItemBuilder(configManager.getItem("game-menu", "bet-all", null))
                .setDisplayName(configManager.getDisplayName("game-menu", "bet-all").replace("%state%", state))
                .build());

        List<Winner.WinnerData> winners = new ArrayList<>();

        for (Winner winner : plugin.getWinnerManager().getWinners()) {
            for (Winner.WinnerData data : winner.getWinnerData()) {
                if (!game.getName().equalsIgnoreCase(data.getGame())) continue;
                winners.add(data);
            }
        }

        // Sort them by the winning date.
        winners.sort(Comparator.comparingLong(Winner.WinnerData::getDate).reversed());

        if (winners.isEmpty()) {
            inventory.setItem(18, new ItemBuilder("badc048a7ce78f7dad72a07da27d85c0916881e5522eeed1e3daf217a38c1a", true)
                    .setDisplayName(configManager.getDisplayName("game-menu", "last-winning-numbers"))
                    .setLore(configManager.getLore("game-menu", "last-winning-numbers"))
                    .build());
        }

        this.taskId = winners.isEmpty() ? -1 : new BukkitRunnable() {
            private int index = 0;

            @Override
            public void run() {
                if (index > 0 && index == winners.size() && winners.size() == 1) {
                    cancel();
                    return;
                }

                // Go back to the first.
                if (index == winners.size()) index = 0;

                inventory.setItem(18, new ItemBuilder(winners.get(index).getWinner().getUrl(), true)
                        .setDisplayName(configManager.getDisplayName("game-menu", "last-winning-numbers"))
                        .setLore(configManager.getLore("game-menu", "last-winning-numbers"))
                        .build());

                index++;
            }
        }.runTaskTimer(plugin, 0, 40L).getTaskId();

        inventory.setItem(26, configManager.getItem("game-menu", "close", null));
    }

    private ItemStack getRuleItem(@NotNull GameRule rule) {
        String path = rule.name().toLowerCase().replace("_", "-");

        List<String> lore = plugin.getConfigManager().getLore("game-menu", path);

        // Surrender rule only applies to american tables.
        if (!lore.isEmpty() && !game.getType().isAmerican() && rule.isSurrender()) {
            lore.add(ConfigManager.Config.ONLY_AMERICAN.asString());
        }

        DyeColor color;
        if (rule.isSurrender() && !game.getType().isAmerican()) color = DyeColor.GRAY;
        else color = game.getRules().getOrDefault(rule, false) ? DyeColor.LIME : DyeColor.RED;

        return new ItemBuilder("BANNER")
                .setBannerColor(color)
                .setDisplayName(plugin.getConfigManager().getDisplayName("game-menu", path))
                .setLore(lore)
                .addItemFlags(ItemFlag.HIDE_POTION_EFFECTS)
                .modifyNBT("rouletteRule", rule.name())
                .build();
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}