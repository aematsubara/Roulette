package me.matsubara.roulette.gui;

import lombok.Getter;
import me.matsubara.roulette.file.Config;
import me.matsubara.roulette.file.Messages;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.GameRule;
import me.matsubara.roulette.game.data.Chip;
import me.matsubara.roulette.manager.InputManager;
import me.matsubara.roulette.util.ItemBuilder;
import me.matsubara.roulette.util.PluginUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

@Getter
public final class GameGUI extends RouletteGUI {

    // The game that is being edited.
    private final Game game;

    // Inventoy being used.
    private final Inventory inventory;

    // Task id used for showing the chips enabled in this game.
    private int taskId;

    public GameGUI(@NotNull Game game, @NotNull Player player) {
        super(game.getPlugin(), "game-menu");
        this.game = game;
        this.inventory = Bukkit.createInventory(
                this,
                36,
                Config.GAME_MENU_TITLE.asStringTranslated().replace("%name%", game.getName()));

        // Fill inventory.
        fillInventory();

        // Open inventory.
        player.openInventory(inventory);
    }

    private void fillInventory() {
        ItemStack background = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .setDisplayName("&7")
                .build();

        for (int i = 0; i < 36; i++) {
            inventory.setItem(i, background);
        }

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

        ItemBuilder croupier = getItem("croupier-settings")
                .setType(Material.PLAYER_HEAD);

        String url = game.getNpcTextureAsURL();
        if (url != null) croupier.setHead(url, true);

        inventory.setItem(19, croupier.build());

        setBetAllItem();

        taskId = new GameChipRunnable(inventory, game)
                .runTaskTimer(plugin, 0L, 20L)
                .getTaskId();

        inventory.setItem(22, getItem("table-settings").build());
    }

    public void setBetAllItem() {
        String state = (game.isBetAllEnabled() ? Config.STATE_ENABLED : Config.STATE_DISABLED).asStringTranslated();
        inventory.setItem(20, getItem("bet-all")
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
            builder.addLore(Config.ONLY_AMERICAN.asStringTranslated());
        }

        return builder.build();
    }

    @Override
    public void handle(@NotNull InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        if (current == null) return;

        Inventory inventory = event.getClickedInventory();
        if (inventory == null) return;

        Player player = (Player) event.getWhoClicked();
        Messages messages = plugin.getMessages();

        boolean isMinAccount;
        if ((isMinAccount = isCustomItem(current, "min-amount")) || isCustomItem(current, "max-amount")) {
            setLimitPlayers(event, !isMinAccount);
        } else if (isCustomItem(current, "account") || isCustomItem(current, "no-account")) {
            if (event.getClick() == ClickType.RIGHT) {
                // Remove the account only if there's one.
                if (game.getAccountGiveTo() != null) {
                    game.setAccountGiveTo(null);
                    messages.send(player, Messages.Message.NO_ACCOUNT);
                    event.setCurrentItem(plugin.getItem("game-menu.items.no-account").build());
                } else {
                    messages.send(player, Messages.Message.ACCOUNT_ALREADY_DELETED);
                }
            } else {
                // Add account.
                plugin.getInputManager().newInput(player, InputManager.InputType.ACCOUNT_NAME, game);
                messages.send(player, Messages.Message.ACCOUNT_NAME);
            }
            closeInventory(player);
        } else if (isCustomItem(current, "start-time")) {
            setStartTime(event);
        } else if (isCustomItem(current, "bet-all")) {
            game.handleGameChange(
                    this,
                    temp -> temp.setBetAllEnabled(!temp.isBetAllEnabled()),
                    GameGUI::setBetAllItem,
                    false);
        } else if (isCustomItem(current, "table-settings")) {
            runTask(() -> new TableGUI(game, player));
        } else if (isCustomItem(current, "croupier-settings")) {
            runTask(() -> new CroupierGUI(game, player));
            return;
        } else if (isCustomItem(current, "game-chip")) {
            runTask(() -> new GameChipGUI(game, player));
            return;
        }

        ItemMeta meta = current.getItemMeta();
        if (meta == null) return;

        String ruleName = meta.getPersistentDataContainer().get(plugin.getRouletteRuleKey(), PersistentDataType.STRING);
        if (ruleName == null) return;

        // Can't be null since the names are equals to the rule ones.
        GameRule rule = PluginUtils.getOrNull(GameRule.class, ruleName);
        if (rule == null) return;

        if (rule.isSurrender() && !game.getType().isAmerican()) {
            messages.send(player, Messages.Message.ONLY_AMERICAN);
            closeInventory(player);
            return;
        }

        // Prison rule can only be applied in a game with 1 min player required.
        if (rule.isEnPrison() && game.getMinPlayers() > 1) {
            messages.send(player, Messages.Message.PRISON_ERROR);
            closeInventory(player);
            return;
        }

        boolean ruleState = !game.isRuleEnabled(rule);
        game.getRules().put(rule, ruleState);

        // If enabled, disable other rules.
        if (ruleState) disableRules(inventory, rule);

        // Update selected rule.
        inventory.setItem(rule.getGUIIndex(), createRuleItem(rule));

        // Save data.
        plugin.getGameManager().save(game);
    }

    private void disableRules(Inventory inventory, GameRule enabled) {
        for (GameRule rule : GameRule.values()) {
            if (rule != enabled) disableRule(inventory, rule);
        }
    }

    private void disableRule(Inventory inventory, GameRule @NotNull ... rules) {
        for (GameRule rule : rules) {
            if (!game.isRuleEnabled(rule)) continue;

            game.getRules().put(rule, false);
            inventory.setItem(rule.getGUIIndex(), createRuleItem(rule));
        }
    }

    private void setLimitPlayers(@NotNull InventoryClickEvent event, boolean isMax) {
        int min = game.getMinPlayers();
        int max = game.getMaxPlayers();

        if (event.getClick() == ClickType.LEFT) {
            game.setLimitPlayers(!isMax ? min - 1 : min, !isMax ? max : max - 1);
        } else if (event.getClick() == ClickType.RIGHT) {
            game.setLimitPlayers(!isMax ? min + 1 : min, !isMax ? max : max + 1);
        }

        int currentAmount = isMax ? game.getMaxPlayers() : game.getMinPlayers();

        ItemStack current = event.getCurrentItem();
        if (current == null || current.getAmount() == currentAmount) return;

        Inventory inventory = event.getClickedInventory();
        if (inventory == null) return;

        // Prison rule can only be applied in a game with 1 min player required.
        GameRule prison = GameRule.EN_PRISON;
        if (!isMax && game.getMinPlayers() != 1 && game.isRuleEnabled(prison)) {
            disableRule(inventory, prison);
        }

        // Update the current changed.
        current.setAmount(currentAmount);

        // Update the other, it may have been changed.
        ItemStack item = inventory.getItem(!isMax ? 12 : 11);
        if (item != null) {
            item.setAmount(!isMax ? game.getMaxPlayers() : game.getMinPlayers());
        }

        // Update join hologram and save data.
        game.updateJoinHologram(false);
        plugin.getGameManager().save(game);
    }

    private void setStartTime(@NotNull InventoryClickEvent event) {
        ClickType click = event.getClick();
        if (click != ClickType.LEFT && click != ClickType.RIGHT) return;

        // Minimum time = 5s | Maximum time = 60s
        int adjustment = (event.getClick() == ClickType.LEFT) ? -5 : 5;
        game.setStartTime(game.getStartTime() + adjustment);

        ItemStack current = event.getCurrentItem();
        if (current != null && current.getAmount() != game.getStartTime()) {
            setStartTimeItem();
        }

        // Save data.
        plugin.getGameManager().save(game);
    }

    private class GameChipRunnable extends BukkitRunnable {

        private final Inventory inventory;
        private final List<Chip> chips;

        private int index;

        private GameChipRunnable(Inventory inventory, @NotNull Game game) {
            this.inventory = inventory;
            this.chips = game.getPlugin().getChipManager().getChipsByGame(game);
        }

        @Override
        public void run() {
            inventory.setItem(21, getItem("game-chip")
                    .setHead(chips.get(index).url(), true)
                    .build());

            if (++index == chips.size()) index = 0;
        }
    }
}