package me.matsubara.roulette.gui;

import lombok.Getter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.GameRule;
import me.matsubara.roulette.manager.ConfigManager;
import me.matsubara.roulette.manager.winner.Winner;
import me.matsubara.roulette.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Getter
public final class GameGUI implements RouletteGUI {

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

    private void fillInventory() {
        ItemStack background = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .setDisplayName("&7")
                .build();

        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, background);
        }

        ItemBuilder croupier = plugin.getItem("game-menu.croupier-settings")
                .setType(Material.PLAYER_HEAD);

        String url = game.getNpcTextureAsURL();
        if (url != null) croupier.setHead(url, true);

        String npcName = game.getNPCName();
        inventory.setItem(0, croupier
                .replace("%croupier-name%", npcName != null ? npcName : ConfigManager.Config.UNNAMED_CROUPIER.asString())
                .build());

        OfflinePlayer accountTo = game.getAccountGiveTo() != null ? Bukkit.getOfflinePlayer(game.getAccountGiveTo()) : null;

        ItemBuilder account;
        if (accountTo != null) {
            String accountName = accountTo.getName();
            account = plugin.getItem("game-menu.account")
                    .setOwningPlayer(accountTo)
                    .replace("%player%", accountName != null ? accountName : "???");
        } else {
            account = plugin.getItem("game-menu.no-account");
        }

        inventory.setItem(10, account.build());

        int minAmount = game.getMinPlayers(), maxAmount = game.getMaxPlayers();

        inventory.setItem(11, plugin.getItem("game-menu.min-amount").setAmount(minAmount).build());
        inventory.setItem(12, plugin.getItem("game-menu.max-amount").setAmount(maxAmount).build());

        inventory.setItem(13, createStartTimeItem());

        // Rules.
        inventory.setItem(14, createRuleItem(GameRule.LA_PARTAGE));
        inventory.setItem(15, createRuleItem(GameRule.EN_PRISON));
        inventory.setItem(16, createRuleItem(GameRule.SURRENDER));

        inventory.setItem(8, createBetAllItem());

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
            inventory.setItem(18, plugin.getItem("game-menu.last-winning-numbers")
                    .setHead("badc048a7ce78f7dad72a07da27d85c0916881e5522eeed1e3daf217a38c1a", true)
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

                inventory.setItem(18, plugin.getItem("game-menu.last-winning-numbers")
                        .setHead(winners.get(index).getWinner().getUrl(), true)
                        .build());

                index++;
            }
        }.runTaskTimer(plugin, 0, 40L).getTaskId();

        inventory.setItem(26, plugin.getItem("game-menu.close").build());
    }

    public ItemStack createBetAllItem() {
        String state = game.isBetAllEnabled() ? ConfigManager.Config.STATE_ENABLED.asString() : ConfigManager.Config.STATE_DISABLED.asString();
        return plugin.getItem("game-menu.bet-all")
                .replace("%state%", state)
                .build();
    }

    public ItemStack createStartTimeItem() {
        int time = game.getStartTime();
        return plugin.getItem("game-menu.start-time")
                .replace("%seconds%", time)
                .setAmount(time)
                .build();
    }

    public ItemStack createRuleItem(@NotNull GameRule rule) {
        Material bannerColor;
        if (rule.isSurrender() && !game.getType().isAmerican()) bannerColor = Material.GRAY_BANNER;
        else bannerColor = game.getRules().getOrDefault(rule, false) ? Material.LIME_BANNER : Material.RED_BANNER;

        ItemBuilder builder = plugin.getItem("game-menu." + rule.name().toLowerCase().replace("_", "-"))
                .setType(bannerColor)
                .addItemFlags(ItemFlag.HIDE_POTION_EFFECTS)
                .setData(plugin.getRouletteRuleKey(), PersistentDataType.STRING, rule.name());

        // Surrender rule only applies to american tables.
        if (!game.getType().isAmerican() && rule.isSurrender()) {
            builder.addLore(ConfigManager.Config.ONLY_AMERICAN.asString());
        }

        return builder.build();
    }
}