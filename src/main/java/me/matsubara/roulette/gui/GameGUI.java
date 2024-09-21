package me.matsubara.roulette.gui;

import lombok.Getter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.GameRule;
import me.matsubara.roulette.game.data.Chip;
import me.matsubara.roulette.manager.ConfigManager;
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

import java.util.List;
import java.util.Locale;

@Getter
public final class GameGUI extends RouletteGUI {

    // Instance of the plugin.
    private final RoulettePlugin plugin;

    // The game that is being edited.
    private final Game game;

    // Inventoy being used.
    private final Inventory inventory;

    // Task id used for showing the chips enabled in this game.
    private int taskId;

    public GameGUI(@NotNull Game game, @NotNull Player player) {
        super("game-menu");
        this.plugin = game.getPlugin();
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

        ItemBuilder croupier = getItem("croupier-settings")
                .setType(Material.PLAYER_HEAD);

        String url = game.getNpcTextureAsURL();
        if (url != null) croupier.setHead(url, true);

        inventory.setItem(0, croupier.build());

        OfflinePlayer accountTo = game.getAccountGiveTo() != null ? Bukkit.getOfflinePlayer(game.getAccountGiveTo()) : null;

        ItemBuilder account;
        if (accountTo != null) {
            String accountName = accountTo.getName();
            account = getItem("account")
                    .setOwningPlayer(accountTo)
                    .replace("%player%", accountName != null ? accountName : "???");
        } else {
            account = getItem("no-account");
        }

        inventory.setItem(10, account.build());

        int minAmount = game.getMinPlayers(), maxAmount = game.getMaxPlayers();

        inventory.setItem(11, getItem("min-amount").setAmount(minAmount).build());
        inventory.setItem(12, getItem("max-amount").setAmount(maxAmount).build());

        setStartTimeItem();

        // Rules.
        inventory.setItem(14, createRuleItem(GameRule.LA_PARTAGE));
        inventory.setItem(15, createRuleItem(GameRule.EN_PRISON));
        inventory.setItem(16, createRuleItem(GameRule.SURRENDER));

        setBetAllItem();

        taskId = new GameChipRunnable(game, inventory)
                .runTaskTimer(plugin, 0L, 20L)
                .getTaskId();

        inventory.setItem(26, getItem("table-settings").build());
    }

    public void setBetAllItem() {
        String state = game.isBetAllEnabled() ? ConfigManager.Config.STATE_ENABLED.asString() : ConfigManager.Config.STATE_DISABLED.asString();
        inventory.setItem(8, getItem("bet-all")
                .replace("%state%", state)
                .build());
    }

    public void setStartTimeItem() {
        int time = game.getStartTime();
        inventory.setItem(13, getItem("start-time")
                .replace("%seconds%", time)
                .setAmount(time)
                .build());
    }

    public ItemStack createRuleItem(@NotNull GameRule rule) {
        Material bannerColor;
        if (rule.isSurrender() && !game.getType().isAmerican()) bannerColor = Material.GRAY_BANNER;
        else bannerColor = game.getRules().getOrDefault(rule, false) ? Material.LIME_BANNER : Material.RED_BANNER;

        ItemBuilder builder = getItem(rule.name().toLowerCase(Locale.ROOT).replace("_", "-"))
                .setType(bannerColor)
                .addItemFlags(ItemFlag.HIDE_POTION_EFFECTS)
                .setData(plugin.getRouletteRuleKey(), PersistentDataType.STRING, rule.name());

        // Surrender rule only applies to american tables.
        if (!game.getType().isAmerican() && rule.isSurrender()) {
            builder.addLore(ConfigManager.Config.ONLY_AMERICAN.asString());
        }

        return builder.build();
    }

    private class GameChipRunnable extends BukkitRunnable {

        private final Inventory inventory;
        private final List<Chip> chips;

        private int index;

        private GameChipRunnable(@NotNull Game game, Inventory inventory) {
            this.inventory = inventory;
            this.chips = game.getPlugin().getChipManager().getChipsByGame(game);
        }

        @Override
        public void run() {
            inventory.setItem(18, getItem("game-chip")
                    .setHead(chips.get(index).url(), true)
                    .build());

            if (++index == chips.size()) index = 0;
        }
    }
}