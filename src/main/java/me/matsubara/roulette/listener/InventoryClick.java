package me.matsubara.roulette.listener;

import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.GameRule;
import me.matsubara.roulette.game.data.Chip;
import me.matsubara.roulette.gui.ChipGUI;
import me.matsubara.roulette.gui.ConfirmGUI;
import me.matsubara.roulette.gui.GameGUI;
import me.matsubara.roulette.manager.ConfigManager;
import me.matsubara.roulette.manager.InputManager;
import me.matsubara.roulette.manager.MessageManager;
import me.matsubara.roulette.runnable.MoneyAnimation;
import me.matsubara.roulette.util.PluginUtils;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class InventoryClick implements Listener {

    private final RoulettePlugin plugin;

    public InventoryClick(RoulettePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof ChipGUI) && !(holder instanceof ConfirmGUI) && !(holder instanceof GameGUI)) return;

        if (event.getRawSlots().stream().noneMatch(integer -> integer < holder.getInventory().getSize())) return;

        if (event.getRawSlots().size() == 1) {
            plugin.getLogger().info("CANCELLED DRAG EVENT BUT CALLED INVENTORYCLICKEVENT DUE TO ONLY 1 ITEM CLICKED!");
            InventoryClickEvent clickEvent = new InventoryClickEvent(
                    event.getView(),
                    InventoryType.SlotType.CONTAINER,
                    event.getRawSlots().iterator().next(),
                    ClickType.LEFT,
                    InventoryAction.PICKUP_ONE);
            plugin.getServer().getPluginManager().callEvent(clickEvent);
        } else {
            plugin.getLogger().info("CANCELLED DRAG BECAUSE IT CONTAINS SLOTS FROM CUSTOM GUI!!!");
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory inventory = event.getClickedInventory();
        if (inventory == null) return;

        // Prevent moving items from player inventory to custom inventories by shift-clicking.
        InventoryHolder tempHolder = event.getView().getTopInventory().getHolder();
        if (inventory.getType() == InventoryType.PLAYER
                && event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                && (tempHolder instanceof ChipGUI || tempHolder instanceof ConfirmGUI || tempHolder instanceof GameGUI)) {
            plugin.getLogger().info("CANCELLED SHIFT CLICK FROM PLAYER INVENTORY TO CUSTOM GUI!");
            event.setCancelled(true);
            return;
        }

        InventoryHolder holder = inventory.getHolder();
        if (!(holder instanceof ConfirmGUI) && !(holder instanceof ChipGUI) && !(holder instanceof GameGUI)) return;

        event.setCancelled(true);

        Game game = plugin.getGameManager().getGameByPlayer(player);
        if (game == null && !(holder instanceof GameGUI)) return;

        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        // Play click sound.
        Sound clickSound = PluginUtils.getOrNull(Sound.class, ConfigManager.Config.SOUND_CLICK.asString());
        if (clickSound != null) player.playSound(player, clickSound, 1.0f, 1.0f);

        if (holder instanceof ConfirmGUI) {
            handleConfirmGUI(event, game);
        } else if (holder instanceof ChipGUI) {
            handleChipGUI(event, game);
        } else {
            handleGameGUI(event);
        }
    }

    @SuppressWarnings("ConstantConditions")
    private void handleConfirmGUI(@NotNull InventoryClickEvent event, Game game) {
        Player player = (Player) event.getWhoClicked();
        ItemStack current = event.getCurrentItem();

        if (((ConfirmGUI) event.getClickedInventory().getHolder()).getType().isLeave()) {

            if (isCustomItem(current, "confirm")) {
                plugin.getMessageManager().send(player, MessageManager.Message.LEAVE_PLAYER);
                game.remove(player, false);
            } else if (isCustomItem(current, "exit")) {
                // Do nothing.
                return;
            }

        } else {
            if (isCustomItem(current, "confirm")) {
                double money = plugin.getEconomy().getBalance(player);

                // If the @bet-all item has URL, use it. Otherwise, use a default one.
                String skin = plugin.getConfig().getString("shop.bet-all.url", "e36e94f6c34a35465fce4a90f2e25976389eb9709a12273574ff70fd4daa6852");

                takeMoney(game, player, money, getBetAllChip(skin, money));
            } else if (isCustomItem(current, "bet-all")) {
                // Do nothing.
                return;
            }
        }

        closeInventory(player);
    }

    private @NotNull Chip getBetAllChip(String skin, double money) {
        Chip betAll = new Chip("bet-all", skin, money);

        // If the bet-all money is the same of one chip from chips.yml, use that chip.
        for (Chip chip : plugin.getChipManager().getChips()) {
            if (money == chip.getPrice()) return chip;
        }

        return betAll;
    }

    public boolean isCustomItem(@NotNull ItemStack item, String name) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && Objects.equals(meta.getPersistentDataContainer().get(plugin.getItemIdKey(), PersistentDataType.STRING), name);
    }

    @SuppressWarnings("ConstantConditions")
    private void handleChipGUI(@NotNull InventoryClickEvent event, Game game) {
        Player player = (Player) event.getWhoClicked();
        ItemStack current = event.getCurrentItem();
        ChipGUI holder = (ChipGUI) event.getClickedInventory().getHolder();

        if (isCustomItem(current, "previous")) {
            holder.previousPage(event.getClick().isShiftClick());
            return;
        } else if (isCustomItem(current, "next")) {
            holder.nextPage(event.getClick().isShiftClick());
            return;
        } else if (isCustomItem(current, "bet-all")) {
            // Open confirm gui.
            int currentPage = holder.getCurrentPage();
            runTask(() -> {
                ConfirmGUI gui = new ConfirmGUI(game, player, ConfirmGUI.ConfirmType.BET_ALL);
                gui.setPreviousPage(currentPage);
            });
            return;
        } else if (isCustomItem(current, "exit")) {
            // Remove player from game.
            plugin.getMessageManager().send(player, MessageManager.Message.LEAVE_PLAYER);
            game.remove(player, false);
        } else {
            ItemMeta meta = current.getItemMeta();
            if (meta == null) return;

            String chipName = meta.getPersistentDataContainer().get(plugin.getChipNameKey(), PersistentDataType.STRING);
            if (chipName == null) return;

            Chip chip = plugin.getChipManager().getByName(chipName);
            if (chip == null) return;

            double money = chip.getPrice();

            // Check if the player has the required money for this chip.
            if (!plugin.getEconomy().has(player, money)) {
                // Not enough money.
                event.setCurrentItem(plugin.getItem("not-enough-money").build());
                return;
            }

            takeMoney(game, player, money, chip);
        }

        closeInventory(player);
    }

    private void takeMoney(Game game, Player player, double money, Chip chip) {
        // Take money from player.
        EconomyResponse response = plugin.getEconomy().withdrawPlayer(player, money);
        if (!response.transactionSuccess()) {
            plugin.getLogger().warning(String.format("It wasn't possible to withdraw $%s to %s.", money, player.getName()));
            return;
        }

        // Remove the money and close inventory.
        MessageManager messages = plugin.getMessageManager();
        messages.send(player, MessageManager.Message.SELECTED_AMOUNT, message -> message
                .replace("%money%", PluginUtils.format(money))
                .replace("%money-left%", PluginUtils.format(plugin.getEconomy().getBalance(player))));
        messages.send(player, MessageManager.Message.CONTROL);

        playerBet(game, player, chip);
    }

    @SuppressWarnings("ConstantConditions")
    private void handleGameGUI(@NotNull InventoryClickEvent event) {
        GameGUI gui = (GameGUI) event.getClickedInventory().getHolder();
        Game game = gui.getGame();

        Player player = (Player) event.getWhoClicked();
        ItemStack current = event.getCurrentItem();
        Inventory inventory = event.getClickedInventory();

        MessageManager messages = plugin.getMessageManager();

        boolean isMinAccount;
        if ((isMinAccount = isCustomItem(current, "min-amount")) || isCustomItem(current, "max-amount")) {
            setLimitPlayers(event, gui, !isMinAccount);
        } else if (isCustomItem(current, "account") || isCustomItem(current, "no-account")) {
            if (event.getClick() == ClickType.RIGHT) {
                // Remove the account only if there's one.
                if (game.getAccountGiveTo() != null) {
                    game.setAccountGiveTo(null);
                    messages.send(player, MessageManager.Message.NO_ACCOUNT);
                    event.setCurrentItem(plugin.getItem("game-menu.no-account").build());
                } else {
                    messages.send(player, MessageManager.Message.ACCOUNT_ALREADY_DELETED);
                }
            } else {
                // Add account.
                plugin.getInputManager().newInput(player, InputManager.InputType.ACCOUNT_NAME, game);
                messages.send(player, MessageManager.Message.ACCOUNT_NAME);
            }
            closeInventory(player);
        } else if (isCustomItem(current, "start-time")) {
            setStartTime(event, game, gui);
        } else if (isCustomItem(current, "bet-all")) {
            setBetAll(event, game, gui);
        } else if (isCustomItem(current, "close")) {
            closeInventory(player);
        } else if (isCustomItem(current, "croupier")) {
            if (event.getClick() == ClickType.LEFT) {
                plugin.getInputManager().newInput(player, InputManager.InputType.CROUPIER_NAME, game);
                messages.send(player, MessageManager.Message.NPC_NAME);
            } else if (event.getClick() == ClickType.RIGHT) {
                plugin.getInputManager().newInput(player, InputManager.InputType.CROUPIER_TEXTURE, game);
                messages.send(player, MessageManager.Message.NPC_TEXTURE);
            } else if (event.getClick() == ClickType.SHIFT_LEFT) {
                String npcName = game.getNPCName();
                if (npcName != null && !npcName.isEmpty() && !npcName.equals(ConfigManager.Config.UNNAMED_CROUPIER.asString())) {
                    messages.send(player, MessageManager.Message.NPC_RENAMED);
                    game.setNPC(null, game.getNPCTexture(), game.getNPCSignature());
                    plugin.getGameManager().save(game);
                } else {
                    messages.send(player, MessageManager.Message.NPC_ALREADY_RENAMED);
                }
            } else if (event.getClick() == ClickType.SHIFT_RIGHT) {
                if (game.hasNPCTexture()) {
                    messages.send(player, MessageManager.Message.NPC_TEXTURIZED);
                    game.setNPC(game.getNPCName(), null, null);
                    plugin.getGameManager().save(game);
                } else {
                    messages.send(player, MessageManager.Message.NPC_ALREADY_TEXTURIZED);
                }
            }
            closeInventory(player);
        }

        ItemMeta meta = current.getItemMeta();
        if (meta == null) return;

        String ruleName = meta.getPersistentDataContainer().get(plugin.getRouletteRuleKey(), PersistentDataType.STRING);
        if (ruleName == null) return;

        // Can't be null since the names are equals to the rule ones.
        GameRule rule = PluginUtils.getOrNull(GameRule.class, ruleName);
        if (rule == null) return;

        if (rule.isSurrender() && !game.getType().isAmerican()) {
            messages.send(player, MessageManager.Message.ONLY_AMERICAN);
            closeInventory(player);
            return;
        }

        // Prison rule can only be applied in a game with 1 min player required.
        if (rule.isEnPrison() && game.getMinPlayers() > 1) {
            messages.send(player, MessageManager.Message.PRISON_ERROR);
            closeInventory(player);
            return;
        }

        boolean ruleState = !game.isRuleEnabled(rule);
        game.getRules().put(rule, ruleState);

        // If enabled, disable other rules.
        if (ruleState) disableRules(gui, inventory, rule);

        // Update selected rule.
        inventory.setItem(rule.getGUIIndex(), gui.createRuleItem(rule));

        // Save data.
        plugin.getGameManager().save(game);
    }

    private void disableRules(GameGUI gui, Inventory inventory, GameRule enabled) {
        for (GameRule rule : GameRule.values()) {
            if (rule != enabled) disableRule(gui, inventory, rule);
        }
    }

    @SuppressWarnings("ConstantConditions")
    private void disableRule(GameGUI gui, Inventory inventory, GameRule @NotNull ... rules) {
        for (GameRule rule : rules) {
            Game game = gui.getGame();
            if (!game.isRuleEnabled(rule)) continue;

            game.getRules().put(rule, false);
            inventory.setItem(rule.getGUIIndex(), gui.createRuleItem(rule));
        }
    }

    @SuppressWarnings("ConstantConditions")
    private void setLimitPlayers(@NotNull InventoryClickEvent event, @NotNull GameGUI gui, boolean isMax) {
        Game game = gui.getGame();

        int min = game.getMinPlayers();
        int max = game.getMaxPlayers();

        if (event.getClick() == ClickType.LEFT) {
            game.setLimitPlayers(!isMax ? min - 1 : min, !isMax ? max : max - 1);
        } else if (event.getClick() == ClickType.RIGHT) {
            game.setLimitPlayers(!isMax ? min + 1 : min, !isMax ? max : max + 1);
        }

        int current = isMax ? game.getMaxPlayers() : game.getMinPlayers();
        if (event.getCurrentItem().getAmount() == current) return;

        Inventory inventory = event.getClickedInventory();

        // Prison rule can only be applied in a game with 1 min player required.
        GameRule prison = GameRule.EN_PRISON;
        if (!isMax && game.getMinPlayers() != 1 && game.isRuleEnabled(prison)) {
            disableRule(gui, inventory, prison);
        }

        // Update the current changed.
        event.getCurrentItem().setAmount(current);

        // Update the other, it may have been changed.
        inventory.getItem(!isMax ? 12 : 11).setAmount(!isMax ? game.getMaxPlayers() : game.getMinPlayers());

        // Save data.
        game.updateJoinHologram(false);
        plugin.getGameManager().save(game);
    }

    @SuppressWarnings("ConstantConditions")
    private void setStartTime(@NotNull InventoryClickEvent event, @NotNull Game game, GameGUI gui) {
        ClickType click = event.getClick();
        if (click != ClickType.LEFT && click != ClickType.RIGHT) return;

        // Minimum time = 5s | Maximum time = 60s
        int adjustment = (event.getClick() == ClickType.LEFT) ? -5 : 5;
        game.setStartTime(Math.max(5, Math.min(60, game.getStartTime() + adjustment)));

        if (event.getCurrentItem().getAmount() != game.getStartTime()) {
            event.setCurrentItem(gui.createStartTimeItem());
        }

        // Save data.
        plugin.getGameManager().save(game);
    }

    private void closeInventory(@NotNull Player player) {
        runTask(player::closeInventory);
    }

    private void runTask(Runnable runnable) {
        plugin.getServer().getScheduler().runTask(plugin, runnable);
    }

    private void playerBet(@NotNull Game game, Player player, Chip chip) {
        // Save player chip.
        game.getPlayers().get(player).setChip(chip);

        // First automated move.
        game.moveChip(player, true);

        // Send a glow advice message.
        plugin.getMessageManager().send(player, MessageManager.Message.CHANGE_GLOW_COLOR);

        if (game.getMoneyAnimation() != null) return;

        // If the money animation isn't running, run now.
        MoneyAnimation anim = new MoneyAnimation(game);
        anim.runTaskTimer(plugin, 1L, 1L);
        game.setMoneyAnimation(anim);
    }

    private void setBetAll(@NotNull InventoryClickEvent event, @NotNull Game game, @NotNull GameGUI gui) {
        game.setBetAll(!game.isBetAll());
        event.setCurrentItem(gui.createBetAllItem());
        plugin.getGameManager().save(game);
    }
}