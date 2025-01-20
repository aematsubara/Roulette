package me.matsubara.roulette.listener;

import com.cryptomorin.xseries.XSound;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.google.common.base.Predicates;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.animation.MoneyAnimation;
import me.matsubara.roulette.file.Config;
import me.matsubara.roulette.file.Messages;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.GameRule;
import me.matsubara.roulette.game.data.Bet;
import me.matsubara.roulette.game.data.Chip;
import me.matsubara.roulette.game.data.CustomizationGroup;
import me.matsubara.roulette.game.data.WinData;
import me.matsubara.roulette.game.state.Selecting;
import me.matsubara.roulette.gui.*;
import me.matsubara.roulette.gui.data.SessionResultGUI;
import me.matsubara.roulette.gui.data.SessionsGUI;
import me.matsubara.roulette.hologram.Hologram;
import me.matsubara.roulette.hook.economy.EconomyExtension;
import me.matsubara.roulette.manager.InputManager;
import me.matsubara.roulette.manager.data.DataManager;
import me.matsubara.roulette.manager.data.PlayerResult;
import me.matsubara.roulette.manager.data.RouletteSession;
import me.matsubara.roulette.model.Model;
import me.matsubara.roulette.npc.NPC;
import me.matsubara.roulette.npc.modifier.MetadataModifier;
import me.matsubara.roulette.util.PluginUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
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

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public final class InventoryClick implements Listener {

    private final RoulettePlugin plugin;

    public InventoryClick(RoulettePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof RouletteGUI)) return;

        if (event.getRawSlots().stream().noneMatch(integer -> integer < holder.getInventory().getSize())) return;

        if (event.getRawSlots().size() == 1) {
            InventoryClickEvent clickEvent = new InventoryClickEvent(
                    event.getView(),
                    InventoryType.SlotType.CONTAINER,
                    event.getRawSlots().iterator().next(),
                    ClickType.LEFT,
                    InventoryAction.PICKUP_ONE);
            plugin.getServer().getPluginManager().callEvent(clickEvent);
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
                && tempHolder instanceof RouletteGUI) {
            event.setCancelled(true);
            return;
        }

        InventoryHolder holder = inventory.getHolder();
        if (!(holder instanceof RouletteGUI)) return;

        event.setCancelled(true);

        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;

        // Play click sound.
        XSound.play(Config.SOUND_CLICK.asString(), temp -> temp.forPlayers(player).play());

        if (holder instanceof BetsGUI gui) {
            handleBetsGUI(event, gui);
        } else if (holder instanceof ChipGUI gui) {
            handleChipGUI(event, gui);
        } else if (holder instanceof ConfirmGUI gui) {
            handleConfirmGUI(event, gui);
        } else if (holder instanceof CroupierGUI gui) {
            handleCroupierGUI(event, gui);
        } else if (holder instanceof GameChipGUI gui) {
            handleGameChipGUI(event, gui);
        } else if (holder instanceof GameGUI gui) {
            handleGameGUI(event, gui);
        } else if (holder instanceof TableGUI gui) {
            handleTableGUI(event, gui);
        } else if (holder instanceof SessionsGUI gui) {
            handleSessionsGUI(event, gui);
        } else if (holder instanceof SessionResultGUI gui) {
            handleSessionResultGUI(event, gui);
        }
    }

    private void handleSessionResultGUI(@NotNull InventoryClickEvent event, SessionResultGUI gui) {
        Player player = (Player) event.getWhoClicked();

        ItemStack current = event.getCurrentItem();
        if (current == null) return;

        ItemMeta meta = current.getItemMeta();
        if (meta == null) return;

        if (isCustomItem(current, "previous")) {
            gui.previousPage(event.isShiftClick());
        } else if (isCustomItem(current, "next")) {
            gui.nextPage(event.isShiftClick());
        }

        Integer resultIndex = meta.getPersistentDataContainer().get(plugin.getPlayerResultIndexKey(), PersistentDataType.INTEGER);
        if (resultIndex == null) return;

        RouletteSession session = gui.getSession();

        PlayerResult result = session.results().get(resultIndex);
        if (result == null) return;

        ClickType click = event.getClick();
        boolean left = click.isLeftClick(), right = click.isRightClick();
        if (!left && !right) return;

        DataManager dataManager = plugin.getDataManager();
        Messages messages = plugin.getMessages();

        if (right) {
            dataManager.remove(result);
            messages.send(player, Messages.Message.SESSION_RESULT_REMOVED);
            closeInventory(player);
            return;
        }

        // If there is no economy provider then we won't be able to deposit/withdraw money.
        EconomyExtension<?> economyExtension = plugin.getEconomyExtension();
        if (!economyExtension.isEnabled()) {
            messages.send(player, Messages.Message.NO_ECONOMY_PROVIDER);
            closeInventory(player);
            return;
        }

        WinData.WinType win = result.win();

        OfflinePlayer winner = Bukkit.getOfflinePlayer(result.playerUUID());

        double originalMoney = result.money();
        double expectedMoney = plugin.getExpectedMoney(originalMoney, result.slot(), win);

        // Player lost. We want to refund the original money.
        if (win == null) {
            if (!economyExtension.deposit(winner, originalMoney)) return;

            Optional.ofNullable(winner.getPlayer())
                    .ifPresent(temp -> messages.send(temp,
                            Messages.Message.SESSION_LOST_RECOVERED,
                            line -> line.replace("%money%", PluginUtils.format(originalMoney))));

            messages.send(player, Messages.Message.SESSION_TRANSACTION_COMPLETED);
            dataManager.remove(result);
            closeInventory(player);
            return;
        }

        // Player won in prison, the player already recovered his original money.
        if (win.isEnPrisonWin()) {
            messages.send(player, Messages.Message.SESSION_BET_IN_PRISON);
            dataManager.remove(result);
            closeInventory(player);
            return;
        }

        if (economyExtension.has(winner, expectedMoney)) {
            // Remove the money that the player won.
            if (!economyExtension.withdraw(winner, expectedMoney)) return;

            // Deposit the original money.
            if (!economyExtension.deposit(winner, originalMoney)) return;

            Optional.ofNullable(winner.getPlayer())
                    .ifPresent(temp -> messages.send(temp,
                            Messages.Message.SESSION_BET_REVERTED,
                            line -> line
                                    .replace("%win-money%", PluginUtils.format(expectedMoney))
                                    .replace("%money%", PluginUtils.format(originalMoney))));

            dataManager.remove(result);
            messages.send(player, Messages.Message.SESSION_TRANSACTION_COMPLETED);
        } else {
            messages.send(player, Messages.Message.SESSION_TRANSACTION_FAILED);
        }

        closeInventory(player);
    }

    private void handleSessionsGUI(@NotNull InventoryClickEvent event, SessionsGUI gui) {
        Player player = (Player) event.getWhoClicked();

        ItemStack current = event.getCurrentItem();
        if (current == null) return;

        ItemMeta meta = current.getItemMeta();
        if (meta == null) return;

        if (isCustomItem(current, "previous")) {
            gui.previousPage(event.isShiftClick());
        } else if (isCustomItem(current, "next")) {
            gui.nextPage(event.isShiftClick());
        }

        UUID sessionUUID = meta.getPersistentDataContainer().get(plugin.getSessionKey(), PluginUtils.UUID_TYPE);
        if (sessionUUID == null) return;

        RouletteSession session = plugin.getDataManager().getSessionByUUID(sessionUUID);
        if (session == null) return;

        // Open results.
        runTask(() -> new SessionResultGUI(plugin, player, session));
    }

    private void handleGameChipGUI(@NotNull InventoryClickEvent event, GameChipGUI gui) {
        Player player = (Player) event.getWhoClicked();

        ItemStack current = event.getCurrentItem();
        if (current == null) return;

        ItemMeta meta = current.getItemMeta();
        if (meta == null) return;

        Game game = gui.getGame();

        if (isCustomItem(current, "max-bets")) {
            ClickType click = event.getClick();
            boolean left = click.isLeftClick(), right = click.isRightClick();
            if (!left && !right) return;

            int step = click.isShiftClick() ? 5 : 1;
            game.setMaxBets(game.getMaxBets() + (left ? -step : step));

            gui.setMaxBetsItem();

            // Save data.
            plugin.getGameManager().save(game);
        } else if (isCustomItem(current, "previous")) {
            gui.previousPage(event.isShiftClick());
        } else if (isCustomItem(current, "next")) {
            gui.nextPage(event.isShiftClick());
        }

        String chipName = meta.getPersistentDataContainer().get(plugin.getChipNameKey(), PersistentDataType.STRING);
        if (chipName == null) return;

        Chip chip = plugin.getChipManager().getByName(chipName);
        if (chip == null) return;

        if (game.isChipDisabled(chip)) {
            game.enableChip(chip);
        } else {
            if (plugin.getChipManager().getChipsByGame(game).size() == 1) {
                plugin.getMessages().send(player, Messages.Message.AT_LEAST_ONE_CHIP_REQUIRED);
                closeInventory(player);
                return;
            }
            game.disableChip(chip);
        }

        int slot = event.getRawSlot() + (isCustomItem(current, "chip") ? 9 : 0);
        gui.setChipStatusItem(slot, chip);

        // Update join hologram and save data.
        game.updateJoinHologram(false);
        plugin.getGameManager().save(game);
    }

    private void handleTableGUI(@NotNull InventoryClickEvent event, TableGUI gui) {
        ItemStack current = event.getCurrentItem();
        if (current == null) return;

        ItemMeta meta = current.getItemMeta();
        if (meta == null) return;

        Game game = gui.getGame();
        Model model = game.getModel();

        ClickType click = event.getClick();
        boolean left = click.isLeftClick(), right = click.isRightClick();
        if (!left && !right) return;

        Model.CustomizationChange change;
        if (isCustomItem(current, "texture")) {
            change = Model.CustomizationChange.TABLE;
            CustomizationGroup texture = PluginUtils.getNextOrPrevious(CustomizationGroup.GROUPS,
                    CustomizationGroup.GROUPS.indexOf(model.getTexture()),
                    right);
            model.setTexture(texture);
            gui.setTextureItem();
        } else if (isCustomItem(current, "chair")) {
            change = Model.CustomizationChange.CHAIR_CARPET;
            Material newCarpet = PluginUtils.getNextOrPrevious(TableGUI.VALID_CARPETS,
                    model.getCarpetsType(),
                    right);
            model.setCarpetsType(newCarpet);
            gui.setChairItem();
        } else if (isCustomItem(current, "decoration")) {
            change = Model.CustomizationChange.DECO;
            String[] pattern = PluginUtils.getNextOrPrevious(Model.PATTERNS,
                    model.getPatternIndex(),
                    right);
            model.setPatternIndex(ArrayUtils.indexOf(Model.PATTERNS, pattern));
            gui.setDecorationItem();
        } else return;

        model.updateModel(game.getSeeingPlayers(), change);
        plugin.getGameManager().save(game);
    }

    private void handleBetsGUI(@NotNull InventoryClickEvent event, BetsGUI gui) {
        Player player = (Player) event.getWhoClicked();

        ItemStack current = event.getCurrentItem();
        if (current == null) return;

        Game game = gui.getGame();
        List<Bet> bets = game.getBets(player);

        ClickType click = event.getClick();
        boolean left = click.isLeftClick(), right = click.isRightClick();
        if (!left && !right) return;

        Messages messages = plugin.getMessages();

        if (isCustomItem(current, "glow-color")) {
            // Change glow color and update it for the existing bets.
            game.changeGlowColor(player, right);

            // Update the glow for the selected bet.
            Bet bet = game.getSelectedBet(player);
            if (bet != null) bet.updateStandGlow(player);

            gui.setGlowColorItem();
            return;
        } else if (isCustomItem(current, "new-bet")) {
            // At this point, the player shouldn't have access to this inventory.
            if (game.isDone(player)) {
                closeInventory(player);
                return;
            }

            // The player reached the betting limit.
            if (game.getBets(player).size() == game.getMaxBets()
                    || !game.isSlotAvailable(player, true)) {
                messages.send(player, Messages.Message.NO_MORE_SLOTS);
                closeInventory(player);
                return;
            }

            if (plugin.getChipManager().hasEnoughMoney(game, player)) {
                // Open a new chip menu for a new bet.
                runTask(() -> new ChipGUI(game, player, true));
                return;
            }

            closeInventory(player);
            return;
        } else if (isCustomItem(current, "previous")) {
            gui.previousPage(event.getClick().isShiftClick());
            return;
        } else if (isCustomItem(current, "next")) {
            gui.nextPage(event.getClick().isShiftClick());
            return;
        } else if (isCustomItem(current, "done")) {
            runTask(() -> {
                ConfirmGUI confirm = new ConfirmGUI(game, player, ConfirmGUI.ConfirmType.DONE);
                confirm.setPreviousPage(gui.getCurrentPage());
            });
            return;
        }

        ItemMeta meta = current.getItemMeta();
        if (meta == null) return;

        Integer betIndex = meta.getPersistentDataContainer().get(plugin.getBetIndexKey(), PersistentDataType.INTEGER);
        if (betIndex == null) return;

        Bet bet = bets.get(betIndex);
        if (bet == null) return;

        if (left) {
            // We don't want players to interact with prison bets.
            if (bet.isEnPrison()) {
                messages.send(player, Messages.Message.BET_IN_PRISON);
                return;
            }

            // This bet is already selected.
            if (betIndex.equals(game.getSelectedBetIndex(player))) {
                messages.send(player, Messages.Message.BET_ALREADY_SELECTED);
                closeInventory(player);
                return;
            }

            // Select bet.
            game.selectBet(player, betIndex);

            // Handle holograms and close inventory.
            handlePlayerBetHolograms(game, player);
            closeInventory(player);

            messages.send(player, Messages.Message.BET_SELECTED,
                    line -> line.replace("%bet%", String.valueOf(betIndex + 1)));
            return;
        }

        // We don't want players to interact with prison bets.
        if (bet.isEnPrison()) {
            messages.send(player, Messages.Message.BET_IN_PRISON);
            closeInventory(player);
            return;
        }

        if (bets.stream()
                .filter(Predicates.not(Bet::isEnPrison))
                .count() == 1) {
            messages.send(player, Messages.Message.AT_LEAST_ONE_BET_REQUIRED);
            closeInventory(player);
            return;
        }

        // First try to return the money, then remove the bet.
        game.tryToReturnMoney(player, bet);
        game.removeBet(player, betIndex);

        // Remove hologram and chip.
        bet.remove();

        // Select the last bet.
        game.selectLast(player);

        // Handle holograms and close inventory.
        handlePlayerBetHolograms(game, player);
        closeInventory(player);

        messages.send(player, Messages.Message.BET_REMOVED,
                line -> line.replace("%bet%", String.valueOf(betIndex + 1)));
    }

    private void handleCroupierGUI(@NotNull InventoryClickEvent event, @NotNull CroupierGUI gui) {
        Player player = (Player) event.getWhoClicked();
        Messages messages = plugin.getMessages();

        ItemStack current = event.getCurrentItem();
        if (current == null) return;

        Game game = gui.getGame();

        ClickType click = event.getClick();
        boolean left = click.isLeftClick(), right = click.isRightClick();

        if (isCustomItem(current, "croupier-name")) {
            if (left) {
                // Change name.
                plugin.getInputManager().newInput(player, InputManager.InputType.CROUPIER_NAME, game);
                messages.send(player, Messages.Message.NPC_NAME);
            } else if (right) {
                // Reset name.
                String npcName = game.getNPCName();
                if (npcName != null && !npcName.isEmpty() && !npcName.equals(Config.UNNAMED_CROUPIER.asStringTranslated())) {
                    messages.send(player, Messages.Message.NPC_RENAMED);
                    game.setNPC(null, game.getNPCTexture(), game.getNPCSignature());
                    plugin.getGameManager().save(game);
                } else {
                    messages.send(player, Messages.Message.NPC_ALREADY_RENAMED);
                }
            }
        } else if (isCustomItem(current, "croupier-texture")) {
            if (left) {
                // Change texture.
                plugin.getInputManager().newInput(player, InputManager.InputType.CROUPIER_TEXTURE, game);
                messages.send(player, Messages.Message.NPC_TEXTURE);
            } else if (right) {
                // Reset texture.
                if (game.hasNPCTexture()) {
                    messages.send(player, Messages.Message.NPC_TEXTURIZED);
                    game.setNPC(game.getNPCName(), null, null);
                    plugin.getGameManager().save(game);
                } else {
                    messages.send(player, Messages.Message.NPC_ALREADY_TEXTURIZED);
                }
            }
        } else if (isCustomItem(current, "croupier-action")) {
            if (click == ClickType.MIDDLE) {
                handleGameChange(
                        gui,
                        temp -> temp.setNpcActionFOV(!temp.isNpcActionFOV()),
                        CroupierGUI::setCroupierActionItem,
                        false);
                return;
            }

            handleGameChange(
                    gui,
                    temp -> temp.setNpcAction(PluginUtils.getNextOrPreviousEnum(temp.getNpcAction(), right)),
                    CroupierGUI::setCroupierActionItem,
                    false);

            if (game.getNpcAction() != NPC.NPCAction.INVITE) return;

            NPC npc = game.getNpc();
            npc.getSeeingPlayers().forEach(npc::lookAtDefaultLocation);
            return;
        } else if (isCustomItem(current, "parrot")) {
            handleGameChange(
                    gui,
                    temp -> temp.setParrotEnabled(!temp.isParrotEnabled()),
                    CroupierGUI::setParrotItem,
                    true);
            return;
        } else if (isCustomItem(current, "parrot-sounds")) {
            handleGameChange(
                    gui,
                    temp -> temp.setParrotSounds(!temp.isParrotSounds()),
                    CroupierGUI::setParrotSoundsItem,
                    false);
            return;
        } else if (isCustomItem(current, "parrot-variant")) {
            handleGameChange(
                    gui,
                    temp -> temp.setParrotVariant(PluginUtils.getNextOrPreviousEnum(temp.getParrotVariant(), right)),
                    CroupierGUI::setParrotVariantItem,
                    true);
            return;
        } else if (isCustomItem(current, "parrot-shoulder")) {
            handleGameChange(
                    gui,
                    temp -> temp.setParrotShoulder(PluginUtils.getNextOrPreviousEnum(temp.getParrotShoulder(), right)),
                    CroupierGUI::setParrotShoulderItem,
                    true);

            // Send another metadata packet but for the other shoulder.
            NPC npc = game.getNpc();
            MetadataModifier metadata = npc.metadata();
            metadata.queue(game.getParrotShoulder().isLeft() ?
                    MetadataModifier.EntityMetadata.SHOULDER_ENTITY_RIGHT :
                    MetadataModifier.EntityMetadata.SHOULDER_ENTITY_LEFT, new NBTCompound());
            metadata.send();
            return;
        } else if (isCustomItem(current, "croupier-rotation")) {
            handleGameChange(
                    gui,
                    temp -> temp.setCurrentNPCFace(PluginUtils.getNextOrPrevious(PluginUtils.RADIAL,
                            temp.getCurrentNPCFace(),
                            right)),
                    CroupierGUI::setCroupierRotationItem,
                    false);

            game.lookAtFace(game.getCurrentNPCFace());
            return;
        } else if (isCustomItem(current, "croupier-distance")) {
            game.setNpcDistance(game.getNpcDistance() + (right ? 5.0d : -5.0d));
            gui.setCroupierDistanceItem();
            plugin.getGameManager().save(game);
            return;
        } else return;

        closeInventory(player);
    }

    private <T extends RouletteGUI> void handleGameChange(
            @NotNull T gui,
            @NotNull Consumer<Game> gameChange,
            @NotNull Consumer<T> guiChange,
            boolean refreshParrot) {
        Game game = gui.getGame();
        gameChange.accept(game);

        guiChange.accept(gui);
        plugin.getGameManager().save(game);

        if (refreshParrot) {
            World world = game.getNpc().getLocation().getWorld();
            if (world != null) refreshParrotChange(game);
        }
    }

    private void refreshParrotChange(@NotNull Game game) {
        NPC npc = game.getNpc();
        MetadataModifier metadata = npc.metadata();
        npc.toggleParrotVisibility(metadata);
        metadata.send();
    }

    private void handleConfirmGUI(@NotNull InventoryClickEvent event, ConfirmGUI confirm) {
        Player player = (Player) event.getWhoClicked();

        ItemStack current = event.getCurrentItem();
        if (current == null) return;

        Game game = confirm.getGame();
        ConfirmGUI.ConfirmType type = confirm.getType();

        // Clicked on the item of the middle.
        String iconPath = type.getIconPath();
        if (isCustomItem(current, iconPath.substring(iconPath.lastIndexOf(".") + 1))) return;

        // Close the inventory.
        if (isCustomItem(current, "cancel")) {
            closeInventory(player);
            return;
        }

        Messages messages = plugin.getMessages();

        if (type.isLeave()) {
            // Remove the player from the game.
            messages.send(player, Messages.Message.LEAVE_PLAYER);
            game.removeCompletely(player);
        } else if (type.isBetAll()) {
            // Bet all the money.
            double money = plugin.getEconomyExtension().getBalance(player);

            // If the @bet-all item has URL, use it. Otherwise, use a default one.
            String skin = plugin.getConfig().getString("chip-menu.items.bet-all.url", "e36e94f6c34a35465fce4a90f2e25976389eb9709a12273574ff70fd4daa6852");

            takeMoney(game, player, money, getBetAllChip(game, skin, money), confirm.getSourceGUI().isNewBet());
        } else {
            // Make the call.
            game.setDone(player);

            // Remove glow and hide hologram.
            game.getBets(player).forEach(Bet::hide);

            // If all players are done, then we want to reduce the start time.
            if (game.getPlayers().stream().noneMatch(Predicates.not(game::isDone))) {
                game.broadcast(Messages.Message.ALL_PLAYERS_DONE);

                Selecting selecting = game.getSelecting();
                if (selecting != null
                        && !selecting.isCancelled()
                        && selecting.getTicks() > 100) {
                    selecting.setTicks(100);
                }
            } else {
                messages.send(player, Messages.Message.YOU_ARE_DONE);
                // Let the other players know.
                game.broadcast(
                        Messages.Message.YOU_ARE_DONE,
                        line -> line.replace("%player-name%", player.getName()),
                        player);
            }
        }

        closeInventory(player);
    }

    private @NotNull Chip getBetAllChip(Game game, String skin, double money) {
        // If the bet-all money is the same of one chip from chips.yml, use that chip.
        Chip chip = plugin.getChipManager().getChipByPrice(game, money);
        return chip != null ? chip : new Chip("bet-all", skin, money);
    }

    public boolean isCustomItem(@NotNull ItemStack item, String name) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && Objects.equals(meta.getPersistentDataContainer().get(plugin.getItemIdKey(), PersistentDataType.STRING), name);
    }

    private void handleChipGUI(@NotNull InventoryClickEvent event, ChipGUI holder) {
        Player player = (Player) event.getWhoClicked();

        ItemStack current = event.getCurrentItem();
        if (current == null) return;

        Game game = holder.getGame();

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
                gui.setSourceGUI(holder);
                gui.setPreviousPage(currentPage);
            });
            return;
        } else if (isCustomItem(current, "exit")) {
            // Remove player from game.
            plugin.getMessages().send(player, Messages.Message.LEAVE_PLAYER);
            game.removeCompletely(player);
        } else {
            ItemMeta meta = current.getItemMeta();
            if (meta == null) return;

            String chipName = meta.getPersistentDataContainer().get(plugin.getChipNameKey(), PersistentDataType.STRING);
            if (chipName == null) return;

            Chip chip = plugin.getChipManager().getByName(chipName);
            if (chip == null) return;

            double money = chip.price();

            // Check if the player has the required money for this chip.
            if (!plugin.getEconomyExtension().has(player, money)) {
                // Not enough money.
                event.setCurrentItem(plugin.getItem("not-enough-money").build());
                return;
            }

            takeMoney(game, player, money, chip, holder.isNewBet());
        }

        closeInventory(player);
    }

    private void takeMoney(Game game, Player player, double money, Chip chip, boolean isNewBet) {
        // Take money from player.
        EconomyExtension<?> economyExtension = plugin.getEconomyExtension();
        if (!economyExtension.withdraw(player, money)) return;

        // Remove the money and close inventory.
        Messages messages = plugin.getMessages();
        messages.send(player, Messages.Message.SELECTED_AMOUNT, message -> message
                .replace("%money%", PluginUtils.format(money))
                .replace("%money-left%", PluginUtils.format(economyExtension.getBalance(player))));
        if (!isNewBet) messages.send(player, Messages.Message.CONTROL);

        playerBet(game, player, chip, isNewBet);
    }

    private void handleGameGUI(@NotNull InventoryClickEvent event, @NotNull GameGUI gui) {
        Game game = gui.getGame();

        ItemStack current = event.getCurrentItem();
        if (current == null) return;

        Inventory inventory = event.getClickedInventory();
        if (inventory == null) return;

        Player player = (Player) event.getWhoClicked();
        Messages messages = plugin.getMessages();

        boolean isMinAccount;
        if ((isMinAccount = isCustomItem(current, "min-amount")) || isCustomItem(current, "max-amount")) {
            setLimitPlayers(event, gui, !isMinAccount);
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
            setStartTime(event, game, gui);
        } else if (isCustomItem(current, "bet-all")) {
            handleGameChange(
                    gui,
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

    private void disableRule(GameGUI gui, Inventory inventory, GameRule @NotNull ... rules) {
        for (GameRule rule : rules) {
            Game game = gui.getGame();
            if (!game.isRuleEnabled(rule)) continue;

            game.getRules().put(rule, false);
            inventory.setItem(rule.getGUIIndex(), gui.createRuleItem(rule));
        }
    }

    private void setLimitPlayers(@NotNull InventoryClickEvent event, @NotNull GameGUI gui, boolean isMax) {
        Game game = gui.getGame();

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
            disableRule(gui, inventory, prison);
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

    private void setStartTime(@NotNull InventoryClickEvent event, @NotNull Game game, GameGUI gui) {
        ClickType click = event.getClick();
        if (click != ClickType.LEFT && click != ClickType.RIGHT) return;

        // Minimum time = 5s | Maximum time = 60s
        int adjustment = (event.getClick() == ClickType.LEFT) ? -5 : 5;
        game.setStartTime(game.getStartTime() + adjustment);

        ItemStack current = event.getCurrentItem();
        if (current != null && current.getAmount() != game.getStartTime()) {
            gui.setStartTimeItem();
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

    private void handlePlayerBetHolograms(@NotNull Game game, Player player) {
        List<Bet> bets = game.getBets(player);
        for (int i = 0; i < bets.size(); i++) {
            Bet bet = bets.get(i);

            Hologram hologram = bet.getHologram();
            if (hologram == null) continue;

            if (i == game.getSelectedBetIndex(player)) {
                // Show hologram.
                hologram.showTo(player);

                // Update hologram lines.
                List<String> lines = hologram.getLines();
                for (int j = 0; j < lines.size(); j++) {
                    hologram.setLine(j, lines.get(j));
                }

                // Add glow.
                bet.updateStandGlow(player);
            } else {
                // Hide hologram and remove glow.
                hologram.hideTo(player);
                bet.removeStandGlow(player);
            }
        }
    }

    private void playerBet(@NotNull Game game, Player player, Chip chip, boolean isNewBet) {
        // Handle the new bet (if needed).
        if (isNewBet) handleNewBet(game, player);

        // Set the chip of the bet.
        Bet bet = game.getSelectedBet(player);
        if (bet != null) bet.setChip(chip);

        // After adding a new bet, we only want to show the hologram of the SELECTED bet.
        handlePlayerBetHolograms(game, player);

        // Place the bet on the first empty slot.
        game.firstChipMove(player);

        // If the money animation isn't running, run now.
        if (game.getMoneyAnimation() == null) {
            new MoneyAnimation(game);
        }
    }

    private void handleNewBet(@NotNull Game game, Player player) {
        game.addEmptyBetAndSelect(player);

        Selecting selecting = game.getSelecting();
        if (selecting == null || selecting.isCancelled()) return;

        int extra = Config.COUNTDOWN_SELECTING_EXTRA.asInt();
        int max = Config.COUNTDOWN_SELECTING_MAX.asInt();

        selecting.setTicks(Math.min(selecting.getTicks() + extra * 20, max * 20 - 20));

        game.broadcast(Messages.Message.EXTRA_TIME_ADDED, line -> line
                .replace("%extra%", String.valueOf(extra))
                .replace("%player-name%", player.getName()), player);
    }
}