package me.matsubara.roulette.listener;

import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.XSound;
import io.github.bananapuncher714.nbteditor.NBTEditor;
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
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public final class InventoryClick implements Listener {

    private final RoulettePlugin plugin;

    public InventoryClick(RoulettePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked().getType() != EntityType.PLAYER) return;

        Player player = (Player) event.getWhoClicked();

        if (event.getClickedInventory() == null) return;

        InventoryHolder holder = event.getClickedInventory().getHolder();
        if (!(holder instanceof ConfirmGUI) && !(holder instanceof ChipGUI) && !(holder instanceof GameGUI)) return;

        event.setCancelled(true);

        Game game = plugin.getGameManager().getGameByPlayer(player);
        if (game == null && !(holder instanceof GameGUI)) return;

        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        // Play click sound.
        XSound.play(player, ConfigManager.Config.SOUND_CLICK.asString());

        if (holder instanceof ConfirmGUI) {
            handleConfirmGUI(event, game);
        } else if (holder instanceof ChipGUI) {
            handleChipGUI(event, game);
        } else {
            handleGameGUI(event);
        }
    }

    @SuppressWarnings("ConstantConditions")
    private void handleConfirmGUI(InventoryClickEvent event, Game game) {
        Player player = (Player) event.getWhoClicked();
        ItemStack item = event.getCurrentItem();

        boolean hasDisplayName = item.getItemMeta().hasDisplayName();
        String displayName = item.getItemMeta().getDisplayName();

        if (((ConfirmGUI) event.getClickedInventory().getHolder()).getType().isLeave()) {

            if (hasDisplayName && displayName.equalsIgnoreCase(ConfigManager.Config.CONFIRM_GUI_CONFIRM.asString())) {
                plugin.getMessageManager().send(player, MessageManager.Message.LEAVE_PLAYER);

                // Remove player from chair.
                game.kickPlayer(player);

                // Remove player from game.
                game.remove(player, false);
            } else if (hasDisplayName && displayName.equalsIgnoreCase(plugin.getConfigManager().getDisplayName("shop", "exit"))) {
                // Do nothing.
                return;
            }

        } else {
            if (hasDisplayName && displayName.equalsIgnoreCase(ConfigManager.Config.CONFIRM_GUI_CONFIRM.asString())) {
                double money = plugin.getEconomy().getBalance(player);

                // If the @bet-all item has URL, use it. Otherwise, use a default one.
                String skin;
                if (plugin.getConfigManager().hasUrl("shop", "bet-all")) {
                    skin = plugin.getConfigManager().getUrl("shop", "bet-all");
                } else {
                    skin = "e36e94f6c34a35465fce4a90f2e25976389eb9709a12273574ff70fd4daa6852";
                }

                // Bet all chip.
                Chip betAll = new Chip(
                        "bet-all",
                        skin,
                        money);

                // If the bet-all money is the same of one chip from chips.yml, use that chip.
                for (Chip chip : plugin.getChipManager().getChipsList()) {
                    if (money == chip.getPrice()) betAll = chip;
                }

                // Take money from player.
                EconomyResponse response = plugin.getEconomy().withdrawPlayer(player, money);
                if (!response.transactionSuccess()) {
                    plugin.getLogger().warning(String.format("It wasn't possible to withdraw $%s to %s.", money, player.getName()));
                    return;
                }

                plugin.getMessageManager().send(player, MessageManager.Message.SELECTED_AMOUNT, message -> message.replace("%money%", plugin.getEconomy().format(money)));
                plugin.getMessageManager().send(player, MessageManager.Message.CONTROL);

                playerBet(game, player, betAll);
            } else if (hasDisplayName && displayName.equalsIgnoreCase(plugin.getConfigManager().getDisplayName("shop", "bet-all"))) {
                // Do nothing.
                return;
            }
        }

        closeInventory(player);
    }

    @SuppressWarnings("ConstantConditions")
    private void handleChipGUI(InventoryClickEvent event, Game game) {
        Player player = (Player) event.getWhoClicked();
        ItemStack item = event.getCurrentItem();
        ChipGUI holder = (ChipGUI) event.getClickedInventory().getHolder();

        boolean hasDisplayName = item.getItemMeta().hasDisplayName();
        String displayName = item.getItemMeta().getDisplayName();

        if (hasDisplayName && displayName.equalsIgnoreCase(plugin.getConfigManager().getDisplayName("shop", "previous"))) {
            holder.previousPage(event.getClick().isShiftClick());
            return;
        } else if (hasDisplayName && displayName.equalsIgnoreCase(plugin.getConfigManager().getDisplayName("shop", "next"))) {
            holder.nextPage(event.getClick().isShiftClick());
            return;
        } else if (hasDisplayName && displayName.equalsIgnoreCase(plugin.getConfigManager().getDisplayName("shop", "bet-all"))) {
            // Open confirm gui.
            int current = holder.getCurrent();
            runTask(() -> {
                ConfirmGUI gui = new ConfirmGUI(plugin, player, ConfirmGUI.ConfirmType.BET_ALL);
                gui.setPreviousPage(current);
            });
            return;
        } else if (hasDisplayName && displayName.equalsIgnoreCase(plugin.getConfigManager().getDisplayName("shop", "exit"))) {
            // Remove player from game.
            plugin.getMessageManager().send(player, MessageManager.Message.LEAVE_PLAYER);
            game.remove(player, false);
        } else if (NBTEditor.contains(item, "chipName")) {
            String chipName = NBTEditor.getString(item, "chipName");
            if (chipName == null) return;

            Chip chip = plugin.getChipManager().getByName(chipName);
            if (chip == null) return;

            double money = chip.getPrice();

            // Check if the player has the required money for this chip.
            if (!plugin.getEconomy().has(player, money)) {
                // Set not enough money item.
                item.setType(XMaterial
                        .matchXMaterial(ConfigManager.Config.NOT_ENOUGH_MONEY_MATERIAL.asString())
                        .orElse(XMaterial.BARRIER)
                        .parseMaterial());

                ItemMeta meta = item.getItemMeta();
                if (meta == null) return;

                meta.setDisplayName(ConfigManager.Config.NOT_ENOUGH_MONEY_DISPLAY_NAME.asString());
                meta.setLore(ConfigManager.Config.NOT_ENOUGH_MONEY_LORE.asList());
                item.setItemMeta(meta);
                return;
            }

            // Take money from player.
            EconomyResponse response = plugin.getEconomy().withdrawPlayer(player, money);
            if (!response.transactionSuccess()) {
                plugin.getLogger().warning(String.format("It wasn't possible to withdraw $%s to %s.", money, player.getName()));
                return;
            }

            // Remove the money and close inventory.
            plugin.getMessageManager().send(player, MessageManager.Message.SELECTED_AMOUNT, message -> message.replace("%money%", plugin.getEconomy().format(money)));
            plugin.getMessageManager().send(player, MessageManager.Message.CONTROL);

            playerBet(game, player, chip);
        } else return;

        closeInventory(player);
    }

    @SuppressWarnings("ConstantConditions")
    private void handleGameGUI(InventoryClickEvent event) {
        GameGUI holder = (GameGUI) event.getClickedInventory().getHolder();
        Game game = holder.getGame();

        int min = holder.getGame().getMinPlayers(), max = holder.getGame().getMaxPlayers();

        Player player = (Player) event.getWhoClicked();
        ItemStack current = event.getCurrentItem();
        Inventory inventory = event.getClickedInventory();

        boolean hasDisplayName = current.getItemMeta().hasDisplayName();
        String displayName = current.getItemMeta().getDisplayName();

        String noAccount = plugin.getConfigManager().getDisplayName("game-menu", "no-account");
        String minAmount = plugin.getConfigManager().getDisplayName("game-menu", "min-amount");
        String maxAmount = plugin.getConfigManager().getDisplayName("game-menu", "max-amount");
        String startTime = plugin.getConfigManager().getStartTimeDisplayName(game.getStartTime());
        String laPartage = plugin.getConfigManager().getDisplayName("game-menu", "la-partage");
        String enPrison = plugin.getConfigManager().getDisplayName("game-menu", "en-prison");
        String surrender = plugin.getConfigManager().getDisplayName("game-menu", "surrender");

        String state = holder.getGame().isBetAll() ? ConfigManager.Config.STATE_ENABLED.asString() : ConfigManager.Config.STATE_DISABLED.asString();
        String betAll = plugin.getConfigManager().getDisplayName("game-menu", "bet-all").replace("%state%", state);

        String close = plugin.getConfigManager().getDisplayName("game-menu", "close");

        String npcName = game.getNPCName();
        if (npcName == null) npcName = ConfigManager.Config.UNNAMED_CROUPIER.asString();
        String croupier = plugin.getConfigManager().getDisplayName("game-menu", "croupier").replace("%croupier-name%", npcName);

        if (hasDisplayName && (displayName.equalsIgnoreCase(minAmount) || displayName.equalsIgnoreCase(maxAmount))) {
            setLimitPlayers(event, game, min, max, displayName.equalsIgnoreCase(maxAmount));
        } else if (isAccountItem(current) || (hasDisplayName && displayName.equalsIgnoreCase(noAccount))) {
            if (current.getType() == XMaterial.PLAYER_HEAD.parseMaterial() && event.getClick() == ClickType.RIGHT) {
                game.setAccountGiveTo(null);
                plugin.getMessageManager().send(player, MessageManager.Message.NO_ACCOUNT);
                event.setCurrentItem(plugin.getConfigManager().getItem("game-menu", "no-account", null));
            } else {
                plugin.getInputManager().newInput(player, InputManager.InputType.ACCOUNT_NAME, game);
                plugin.getMessageManager().send(player, MessageManager.Message.NPC_NAME);
            }
            closeInventory(player);
        } else if (hasDisplayName && displayName.equalsIgnoreCase(startTime)) {
            setStartTime(event, game);
        } else if (hasDisplayName && (displayName.equalsIgnoreCase(laPartage) || displayName.equalsIgnoreCase(enPrison) || displayName.equalsIgnoreCase(surrender))) {

            // Can't be null since the names are equals to the rules ones.
            GameRule rule = GameRule.valueOf(NBTEditor.getString(current, "rouletteRule"));
            if (rule.isSurrender() && !game.getType().isAmerican()) {
                plugin.getMessageManager().send(player, MessageManager.Message.ONLY_AMERICAN);
                closeInventory(player);
                return;
            }

            // Prison rule can only be applied in a game with 1 min player required.
            if (rule.isEnPrison() && game.getMinPlayers() > 1) {
                plugin.getMessageManager().send(player, MessageManager.Message.PRISON_ERROR);
                closeInventory(player);
                return;
            }

            boolean ruleState = !game.isRuleEnabled(rule);
            game.getRules().put(rule, ruleState);

            // If enabled, disable other rules.
            if (ruleState) disableRules(game, inventory, rule);

            setBannerColor(current, ruleState);

            // Save data.
            plugin.getGameManager().save(game);

        } else if (hasDisplayName && displayName.equalsIgnoreCase(betAll)) {
            setBetAll(event, game);
        } else if (hasDisplayName && displayName.equalsIgnoreCase(close)) {
            closeInventory(player);
        } else if (hasDisplayName && ChatColor.stripColor(displayName).equalsIgnoreCase(ChatColor.stripColor(croupier))) {
            if (event.getClick() == ClickType.LEFT) {
                plugin.getInputManager().newInput(player, InputManager.InputType.CROUPIER_NAME, game);
                plugin.getMessageManager().send(player, MessageManager.Message.NPC_NAME);
            } else if (event.getClick() == ClickType.RIGHT) {
                plugin.getInputManager().newInput(player, InputManager.InputType.CROUPIER_TEXTURE, game);
                plugin.getMessageManager().send(player, MessageManager.Message.NPC_TEXTURE);
            } else if (event.getClick() == ClickType.SHIFT_LEFT) {
                String texture = game.getNPCTexture();
                String signature = game.getNPCSignature();

                plugin.getMessageManager().send(player, MessageManager.Message.NPC_RENAMED);
                game.setNPC(null, texture, signature);
                plugin.getGameManager().save(game);
            } else if (event.getClick() == ClickType.SHIFT_RIGHT) {
                String name = game.getNPCName() == null ? "" : game.getNPCName();

                plugin.getMessageManager().send(player, MessageManager.Message.NPC_TEXTURIZED);
                game.setNPC(name, null, null);
                plugin.getGameManager().save(game);
            }
            closeInventory(player);
        }
    }

    private void disableRules(Game game, Inventory inventory, GameRule enabled) {
        for (GameRule rule : GameRule.values()) {
            if (rule == enabled) continue;
            disableRule(game, inventory, rule);
        }
    }

    @SuppressWarnings("ConstantConditions")
    private void disableRule(Game game, Inventory inventory, GameRule... rules) {
        for (GameRule rule : rules) {
            if (!game.isRuleEnabled(rule)) continue;

            game.getRules().put(rule, false);
            setBannerColor(inventory.getItem(rule.getGUIIndex()), false);
        }
    }

    private void setBannerColor(ItemStack item, boolean enabled) {
        BannerMeta meta = (BannerMeta) item.getItemMeta();
        if (meta != null) {
            meta.addPattern(new Pattern(enabled ? DyeColor.LIME : DyeColor.RED, PatternType.BASE));
            item.setItemMeta(meta);
        }
    }


    private boolean isAccountItem(ItemStack item) {
        //noinspection ConstantConditions, already checked in the event.
        return item.getType() == XMaterial.PLAYER_HEAD.parseMaterial() && ((SkullMeta) item.getItemMeta()).hasOwner();
    }

    @SuppressWarnings("ConstantConditions")
    private void setLimitPlayers(InventoryClickEvent event, Game game, int min, int max, boolean isMax) {
        if (event.getClick() == ClickType.LEFT) {
            game.setLimitPlayers(!isMax ? min - 1 : min, !isMax ? max : max - 1);
        } else if (event.getClick() == ClickType.RIGHT) {
            game.setLimitPlayers(!isMax ? min + 1 : min, !isMax ? max : max + 1);
        }

        int current = !isMax ? game.getMinPlayers() : game.getMaxPlayers();
        if (event.getCurrentItem().getAmount() != current) {
            if (!isMax && game.isRuleEnabled(GameRule.EN_PRISON)) {
                game.getRules().put(GameRule.EN_PRISON, false);
                setBannerColor(event.getClickedInventory().getItem(15), false);
            }
            event.getCurrentItem().setAmount(current);
            event.getClickedInventory().getItem(!isMax ? 12 : 11).setAmount(!isMax ? game.getMaxPlayers() : game.getMinPlayers());
        }

        // Save data.
        game.updateJoinHologram(false);
        plugin.getGameManager().save(game);
    }

    @SuppressWarnings("ConstantConditions")
    private void setStartTime(InventoryClickEvent event, Game game) {
        int time = game.getStartTime();
        if (event.getClick() == ClickType.LEFT) {
            time -= 5;
            if (time < 0) time = 0;
        } else if (event.getClick() == ClickType.RIGHT) {
            time += 5;
            if (time > 60) time = 60;
        }

        game.setStartTime(time);

        if (event.getCurrentItem().getAmount() != time) {
            event.getCurrentItem().setAmount(Math.max(1, time));

            ItemMeta meta = event.getCurrentItem().getItemMeta();
            if (meta == null) return;

            meta.setDisplayName(plugin.getConfigManager().getStartTimeDisplayName(time));
            event.getCurrentItem().setItemMeta(meta);
        }

        // Save data.
        plugin.getGameManager().save(game);
    }

    private void closeInventory(Player player) {
        runTask(player::closeInventory);
    }

    private void runTask(Runnable runnable) {
        plugin.getServer().getScheduler().runTask(plugin, runnable);
    }

    private void playerBet(Game game, Player player, Chip chip) {
        // Save player chip.
        game.getPlayers().get(player).setChip(chip);

        // First automated move.
        game.moveChip(player, true);

        // If the money animation isn't running, run now.
        if (game.getMoneyAnimation() == null) {
            MoneyAnimation anim = new MoneyAnimation(game);
            anim.runTaskTimer(plugin, 1L, 1L);
            game.setMoneyAnimation(anim);
        }
    }

    @SuppressWarnings("ConstantConditions")
    private void setBetAll(InventoryClickEvent event, Game game) {
        boolean betAll = !game.isBetAll();

        game.setBetAll(betAll);

        String state = game.isBetAll() ? ConfigManager.Config.STATE_ENABLED.asString() : ConfigManager.Config.STATE_DISABLED.asString();

        ItemMeta meta = event.getCurrentItem().getItemMeta();
        meta.setDisplayName(plugin.getConfigManager().getDisplayName("game-menu", "bet-all").replace("%state%", state));
        if (!meta.hasItemFlag(ItemFlag.HIDE_ATTRIBUTES)) meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        if (betAll) {
            event.getCurrentItem().addUnsafeEnchantment(Enchantment.ARROW_DAMAGE, 1);
        } else {
            if (event.getCurrentItem().containsEnchantment(Enchantment.ARROW_DAMAGE)) {
                event.getCurrentItem().removeEnchantment(Enchantment.ARROW_DAMAGE);
            }
        }

        event.getCurrentItem().setItemMeta(meta);

        // Save data.
        plugin.getGameManager().save(game);
    }
}