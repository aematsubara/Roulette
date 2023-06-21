package me.matsubara.roulette.game;

import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedSignedProperty;
import com.cryptomorin.xseries.ReflectionUtils;
import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.XSound;
import com.google.common.base.Preconditions;
import lombok.Getter;
import lombok.Setter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.event.RouletteEndEvent;
import me.matsubara.roulette.game.data.Bet;
import me.matsubara.roulette.game.data.Chip;
import me.matsubara.roulette.game.data.Slot;
import me.matsubara.roulette.game.state.Starting;
import me.matsubara.roulette.hologram.Hologram;
import me.matsubara.roulette.manager.ConfigManager;
import me.matsubara.roulette.manager.MessageManager;
import me.matsubara.roulette.manager.winner.Winner;
import me.matsubara.roulette.model.Model;
import me.matsubara.roulette.model.stand.PacketStand;
import me.matsubara.roulette.model.stand.StandSettings;
import me.matsubara.roulette.npc.NPC;
import me.matsubara.roulette.npc.modifier.MetadataModifier;
import me.matsubara.roulette.runnable.MoneyAnimation;
import me.matsubara.roulette.util.Lang3Utils;
import me.matsubara.roulette.util.PluginUtils;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Consumer;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@SuppressWarnings("deprecation")
@Getter
@Setter
public final class Game {

    // Instance of the plugin.
    private final RoulettePlugin plugin;

    // The name of this game.
    private final String name;

    // The model of this game.
    private final Model model;

    // The croupier of this game.
    private NPC npc;

    // The mininum amount of players required in this game.
    private int minPlayers;

    // The maximum amount of players allowed in this game.
    private int maxPlayers;

    // Players in this game.
    private final Map<Player, Bet> players;

    // The slots disabled in this game.
    private final List<Slot> disabledSlots;

    // Rules applied in this game.
    private final Map<GameRule, Boolean> rules;

    private final Map<String, ArmorStand> chairs;

    // The main hologram of this game, used to join.
    private final Hologram joinHologram;

    // The spinning hologram.
    private final Hologram spinHologram;

    // The type of this game.
    private final GameType type;

    // The current state of this game, may change over time.
    private GameState state;

    // The unique id of the player who'll receive the money from the bets.
    private UUID accountGiveTo;

    // Unique id of the player who created this game.
    private final UUID owner;

    // Time before starting the game.
    private int startTime;

    // If bet-all is allowed in this game.
    private boolean betAll;

    // Tasks.
    private BukkitTask startingTask;
    private BukkitTask selectingTask;
    private BukkitTask spinningTask;
    private MoneyAnimation moneyAnimation;

    // The current slot selected in this game.
    private Slot winner;

    // Stands used for showing winner chip.
    private PacketStand selectedOne, selectedTwo;

    // Chairs of this game, range goes from 0 to max amount of chairs; 10 in this case.
    public static final int[] CHAIRS = IntStream.range(0, 10).map(x -> (x * 3) + 2).toArray();

    public Game(
            RoulettePlugin plugin,
            String name,
            @Nullable String npcName,
            @Nullable String npcTexture,
            @Nullable String npcSignature,
            Model model,
            int minPlayers,
            int maxPlayers,
            GameType type,
            UUID owner,
            int startTime,
            boolean betAll,
            @Nullable UUID accountGiveTo,
            @Nullable EnumMap<GameRule, Boolean> rules) {
        this.plugin = plugin;
        this.name = name;
        this.model = model;
        this.players = new HashMap<>();
        this.owner = owner;

        // Spawn NPC.
        setNPC(npcName, npcTexture, npcSignature);

        setLimitPlayers(minPlayers, maxPlayers);

        this.disabledSlots = new ArrayList<>();
        for (String slot : ConfigManager.Config.DISABLED_SLOTS.asList()) {
            try {
                this.disabledSlots.add(Slot.valueOf(slot));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().info("Invalid slot to disable: " + slot);
            }
        }

        this.rules = new EnumMap<>(GameRule.class);
        if (rules != null && !rules.isEmpty()) this.rules.putAll(rules);

        this.chairs = new ConcurrentHashMap<>();
        this.type = type;
        this.state = GameState.IDLE;
        this.startTime = startTime;
        this.betAll = betAll;
        this.accountGiveTo = accountGiveTo;

        // Spawn join hologram.
        this.joinHologram = new Hologram(plugin, model
                .getLocation()
                .clone()
                .add(0.0d, 1.25d, 0.0d));

        // Add lines.
        updateJoinHologram(true);

        // Spawn spin hologram.
        this.spinHologram = new Hologram(plugin, model
                .getLocation()
                .clone());

        // This hologram is only visible for players playing this game.
        this.spinHologram.setVisibleByDefault(false);
    }

    private ArmorStand getChair(int chair) {
        String key = "CHAIR_" + chair;

        ArmorStand stand;
        if ((stand = chairs.get(key)) == null || !stand.isValid()) {
            ArmorStand newValue;
            if ((newValue = spawnChairStand(key)) != null) {
                chairs.put(key, newValue);
                return newValue;
            }
        }

        return stand != null && stand.isValid() ? stand : null;
    }

    private @Nullable ArmorStand spawnChairStand(String name) {
        PacketStand stand = model.getStands().get(name);
        if (stand == null) return null;

        World world = model.getLocation().getWorld();
        if (world == null) return null;

        return world.spawn(stand.getLocation(), ArmorStand.class, armorStand -> {
            StandSettings settings = stand.getSettings();

            armorStand.setInvisible(settings.isInvisible());
            armorStand.setSmall(settings.isSmall());
            armorStand.setBasePlate(settings.isBasePlate());
            armorStand.setArms(settings.isArms());
            armorStand.setFireTicks(settings.isFire() ? Integer.MAX_VALUE : 0);
            armorStand.setMarker(settings.isMarker());
            if (ReflectionUtils.supports(13)) armorStand.setPersistent(false);

            // Set poses.
            armorStand.setHeadPose(settings.getHeadPose());
            armorStand.setBodyPose(settings.getBodyPose());
            armorStand.setLeftArmPose(settings.getLeftArmPose());
            armorStand.setRightArmPose(settings.getRightArmPose());
            armorStand.setLeftLegPose(settings.getLeftLegPose());
            armorStand.setRightLegPose(settings.getRightLegPose());
        });
    }

    public Location getNPCLocation() {
        Location location = model.getLocation().clone();
        location.add(PluginUtils.offsetVector(new Vector(-3, 0, 1), location.getYaw(), location.getPitch()));
        return location.setDirection(PluginUtils.getDirection(PluginUtils.getFace(location.getYaw(), false).getOppositeFace()));
    }

    public boolean hasNPCTexture() {
        return !npc.getProfile().getProperties().get("textures").isEmpty();
    }

    public String getNPCTexture() {
        for (WrappedSignedProperty property : npc.getProfile().getProperties().get("textures")) {
            return property.getValue();
        }
        return null;
    }

    public String getNPCSignature() {
        for (WrappedSignedProperty property : npc.getProfile().getProperties().get("textures")) {
            return property.getSignature();
        }
        return null;
    }

    public void setNPC(@Nullable String name, @Nullable String texture, @Nullable String signature) {
        // If this game already has an NPC, remove first.
        if (this.npc != null) {
            plugin.getNpcPool().removeNPC(npc.getEntityId());
        }

        String unnamed = ConfigManager.Config.UNNAMED_CROUPIER.asString();
        if (name == null || name.isEmpty()) name = unnamed;

        // Hide npc name if empty unnamed.
        if (name.equalsIgnoreCase(unnamed)) {
            plugin.getHideTeam().addEntry(name);
        }

        WrappedGameProfile profile = new WrappedGameProfile(UUID.randomUUID(), name);

        // Set NPC skin texture (if possible).
        if (texture != null && signature != null) {
            profile.getProperties().put("textures", new WrappedSignedProperty("textures", texture, signature));
        }

        Location npcLocation = getNPCLocation();

        this.npc = NPC.builder()
                .profile(profile)
                .location(npcLocation)
                .lookAtPlayer(false)
                .imitatePlayer(false)
                .spawnCustomizer((npc, player) -> {
                    npc.rotation().queueRotate(npcLocation.getYaw(), npcLocation.getPitch()).send(player);

                    // Set item (ball) in main hand.
                    if (!state.isSpinning() && !state.isEnding()) {
                        npc.equipment().queue(EnumWrappers.ItemSlot.MAINHAND, plugin.getConfigManager().getBall()).send(player);
                    }

                    // Show skin layers.
                    npc.metadata().queue(MetadataModifier.EntityMetadata.SKIN_LAYERS, true).send(player);
                })
                .build(plugin.getNpcPool());

        // Fix looking direction.
        this.npc.rotation().queueRotate(npcLocation.getYaw(), npcLocation.getPitch()).send();
    }

    public void playSound(String sound) {
        for (Player player : players.keySet()) {
            XSound.matchXSound(sound).ifPresent(temp -> temp.play(player));
        }
    }

    public void broadcast(String message) {
        for (Player player : players.keySet()) {
            player.sendMessage(message);
        }
    }

    public void broadcast(List<String> messages) {
        for (String message : messages) {
            broadcast(message);
        }
    }

    public boolean isFull() {
        return players.size() == maxPlayers;
    }

    public boolean canJoin() {
        return state.isIdle() || state.isStarting();
    }

    public void add(Player player) {
        // The player may still be in the game if prison rule is enabled.
        if (!isPlaying(player)) {
            // Add player to the game and sit.
            players.put(player, new Bet(this));
            sitPlayer(player, true);
        }

        // Can be greater than 0 when prison rule is enabled, since players aren't removed from the game.
        if (players.size() >= minPlayers && (startingTask == null || startingTask.isCancelled())) {
            // Start starting task.
            setStartingTask(new Starting(plugin, this).runTaskTimer(plugin, 20L, 20L));
        }

        joinHologram.hideTo(player);
        updateJoinHologram(false);

        spinHologram.showTo(player);

        // Add player to the collision team to prevent collisions.
        plugin.getCollisionTeam().addEntry(player.getName());
    }

    public void remove(Player player, boolean isRestart) {
        // If the game is being restarted, the players are cleared in restart() iterator.
        if (!isRestart) {
            players.remove(player).remove();

            // Players are removed with an iterator in @restart, we don't need to send a message to every player about it if restarting.
            broadcast(MessageManager.Message.LEAVE.asString()
                    .replace("%player%", player.getName())
                    .replace("%playing%", String.valueOf(players.size()))
                    .replace("%max%", String.valueOf(maxPlayers)));
        }

        // No need to call restart() again if the game is being restarted.
        if (players.isEmpty() && !isRestart) {
            restart();
        }

        // Remove player from chair (if sitting).
        if (isSittingOn(player)) kickPlayer(player);

        if (state.isIdle() || state.isStarting()) {
            joinHologram.showTo(player);
            updateJoinHologram(false);
        }

        // Hide spin hologram to the player.
        spinHologram.hideTo(player);

        // Remove player from the collision team.
        plugin.getCollisionTeam().removeEntry(player.getName());
    }

    public void updateJoinHologram(boolean isReload) {
        List<String> lines = ConfigManager.Config.JOIN_HOLOGRAM.asList();

        // Fill join hologram.
        if (isReload || lines.size() != joinHologram.size()) {
            // Remove lines (if any).
            joinHologram.destroy();

            for (String line : lines) {
                joinHologram.addLines(replaceJoinHologramLines(line));
            }
            return;
        }

        // Update hologram lines.
        for (int i = 0; i < lines.size(); i++) {
            joinHologram.setLine(i, replaceJoinHologramLines(lines.get(i)));
        }
    }

    private String replaceJoinHologramLines(String line) {
        return line
                .replace("%name%", name.replace("_", " "))
                .replace("%playing%", String.valueOf(size()))
                .replace("%max%", String.valueOf(maxPlayers))
                .replace("%type%", type.getName());
    }

    /**
     * Remove the player from its chair.
     */
    public void kickPlayer(Player player) {
        int sittingOn = getSittingOn(player);

        // Remove player from chair.
        ArmorStand sitting = getChair(sittingOn);
        if (sitting != null) sitting.removePassenger(player);
    }

    private void cancelTasks(BukkitTask... tasks) {
        for (BukkitTask task : tasks) {
            if (task != null && !task.isCancelled()) task.cancel();
        }
    }

    public int size() {
        return players.size();
    }

    public boolean isPlaying(Player player) {
        return players.containsKey(player);
    }

    private boolean isChairInUse(int chair) {
        ArmorStand stand = getChair(chair);
        return stand == null || !stand.getPassengers().isEmpty();
    }

    private boolean isChairAvailable() {
        for (int chair : CHAIRS) {
            if (!isChairInUse(chair)) return true;
        }
        return false;
    }

    public boolean isSittingOn(Player player) {
        for (int chair : CHAIRS) {
            if (isSittingOn(player, chair)) return true;
        }
        return false;
    }

    private boolean isSittingOn(Player player, int chair) {
        ArmorStand stand = getChair(chair);
        return stand != null && stand.getPassengers().contains(player);
    }

    public int getSittingOn(Player player) {
        for (int chair : CHAIRS) {
            if (isSittingOn(player, chair)) return chair;
        }
        return -1;
    }

    public void sitPlayer(Player player, boolean toTheRight) {
        // If not chair available.
        if (!isChairAvailable()) return;

        if (toTheRight && !isSittingOn(player)) {
            for (int chair : CHAIRS) {
                ArmorStand stand = getChair(chair);
                if (stand == null || !stand.getPassengers().isEmpty()) continue;

                stand.addPassenger(player);
                break;
            }
            return;
        }

        // Shouldn't happen.
        if (!isSittingOn(player)) return;

        int sittingOn = getSittingOn(player);

        ArmorStand sitting = getChair(sittingOn);
        if (sitting != null) sitting.removePassenger(player);

        int ordinal = Lang3Utils.indexOf(CHAIRS, sittingOn);

        ArmorStand stand;

        do {
            if (toTheRight) {
                ordinal++;
                if (ordinal > CHAIRS.length - 1) ordinal = 0;
            } else {
                ordinal--;
                if (ordinal < 0) ordinal = CHAIRS.length - 1;
            }

            stand = getChair(CHAIRS[ordinal]);
        } while (stand == null || !stand.getPassengers().isEmpty());

        fixChairCamera(player, stand, temp -> {
            // Play move from chair sound at player location.
            XSound.play(player.getLocation(), ConfigManager.Config.SOUND_SWAP_CHAIR.asString());
            temp.addPassenger(player);
        });
    }

    private void fixChairCamera(Player player, ArmorStand stand, Consumer<ArmorStand> andThen) {
        if (!ConfigManager.Config.FIX_CHAIR_CAMERA.asBoolean()) {
            andThen.accept(stand);
            return;
        }

        // Add a bit of offset.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            player.teleport(stand.getLocation().clone().add(0.0d, 0.25d, 0.0d));
            andThen.accept(stand);
        });
    }

    public void moveChip(Player player, boolean toTheRight) {
        // If not slot available, return.
        if (!isSlotAvailable()) return;

        // If the player didn't select a chip from the GUI yet, return.
        if (!players.get(player).hasChip()) return;

        if (toTheRight && !players.get(player).hasSlot()) {
            for (Slot slot : Slot.values(this)) {
                if (alreadySelected(slot)) continue;

                // Spawn hologram and chip (if not spawned).
                players.get(player).handle(player, slot);
                if (ReflectionUtils.MINOR_NUMBER == 17) players.get(player).handle(player, slot);

                break;
            }
            return;
        }

        int ordinal = Lang3Utils.indexOf(Slot.values(this), players.get(player).getSlot());

        Slot slot;

        do {
            if (toTheRight) {
                ordinal++;
                if (ordinal > Slot.values(this).length - 1) ordinal = 0;
            } else {
                ordinal--;
                if (ordinal < 0) ordinal = Slot.values(this).length - 1;
            }

            slot = Slot.values(this)[ordinal];
        } while (alreadySelected(slot));

        // Teleport hologram and chip.
        players.get(player).handle(player, slot);
    }

    public void checkWinner() {
        spawnBottle();

        Map<Player, WinType> winners = new HashMap<>();

        for (Player player : players.keySet()) {
            Slot slot = players.get(player).getSlot();
            Bet bet = players.get(player);

            // Check for single numbers or slots with more than 1 number.
            if (slot == winner || slot.contains(winner)) {
                winners.put(player, WinType.NORMAL);
                continue;
            }

            // If the checks above didn't make it, check for rules.

            // Partage.
            if (isRuleEnabled(GameRule.LA_PARTAGE) && winner.isZero() && slot.applyForRules()) {
                winners.put(player, WinType.LA_PARTAGE);
                continue;
            }

            // Prison.
            if (isRuleEnabled(GameRule.EN_PRISON) && winner.isZero() && slot.applyForRules() && bet.isEnPrison()) {
                winners.put(player, WinType.EN_PRISON);

                // Remove prison state to prevent re-starting the game over and over.
                bet.setWasEnPrison(true);
                continue;
            }

            // Surrender.
            if (type.isAmerican() && isRuleEnabled(GameRule.SURRENDER) && winner.isAnyZero() && slot.applyForRules()) {
                winners.put(player, WinType.SURRENDER);
            }
        }

        RouletteEndEvent endEvent = new RouletteEndEvent(this, winners, winner);
        plugin.getServer().getPluginManager().callEvent(endEvent);

        // Set all winners bet as winner.
        winners.keySet().forEach(player -> players.get(player).setWon(true));

        // Send money to the account of the game.
        if (accountGiveTo != null) {
            double total = 0;

            OfflinePlayer giveTo = Bukkit.getOfflinePlayer(accountGiveTo);

            for (Player player : players.keySet()) {
                // Don't take money from the player who won.
                if (winners.containsKey(player)) continue;

                // Don't take money from the same player.
                if (giveTo.getUniqueId().equals(player.getUniqueId())) continue;

                Chip chip = players.get(player).getChip();

                EconomyResponse response = plugin.getEconomy().depositPlayer(giveTo, chip.getPrice());
                if (!response.transactionSuccess()) {
                    plugin.getLogger().warning(String.format("It wasn't possible to deposit $%s to %s.", chip.getPrice(), giveTo.getName()));
                    continue;
                }

                total += chip.getPrice();
            }

            if (giveTo.isOnline() && total > 0) {
                String totalMoney = String.valueOf(total);
                plugin.getMessageManager().send(giveTo.getPlayer(), MessageManager.Message.RECEIVED, message -> message
                        .replace("%money%", totalMoney)
                        .replace("%name%", name.replace("_", " ")));
            }
        }

        if (winners.isEmpty()) {
            broadcast(MessageManager.Message.NO_WINNER.asString().replace("%winner%", PluginUtils.getSlotName(winner)));
            broadcast(MessageManager.Message.RESTART.asString());
            restartRunnable();
            return;
        }

        String[] names = winners.entrySet().stream().map(entry -> {
            if (entry.getValue().isNormalWin()) return entry.getKey().getName();
            return entry.getKey().getName() + " (" + entry.getValue().getFormatName() + ")";
        }).toArray(String[]::new);

        broadcast(plugin.getMessageManager().getRandomNPCMessage(npc, "winner"));
        broadcast(MessageManager.Message.WINNERS.asList().stream().map(message -> message
                .replace("%amount%", String.valueOf(names.length))
                .replace("%winners%", Arrays.toString(names))
                .replace("%winner%", PluginUtils.getSlotName(winner))).collect(Collectors.toList()));

        for (Player winner : winners.keySet()) {
            Chip chip = players.get(winner).getChip();
            Slot slot = players.get(winner).getSlot();
            WinType winType = winners.get(winner);

            double price;
            if (winType.isNormalWin()) {
                price = chip.getPrice() * slot.getMultiplier(this);
            } else if (winType.isLaPartageWin() || winType.isSurrenderWin()) {
                // Half money if partage.
                price = chip.getPrice() / 2;
            } else {
                // Original money.
                price = chip.getPrice();
            }

            EconomyResponse response = plugin.getEconomy().depositPlayer(winner, price);
            if (!response.transactionSuccess()) {
                plugin.getLogger().warning(String.format("It wasn't possible to deposit $%s to %s.", chip.getPrice(), winner.getName()));
                continue;
            }

            if (winType.isNormalWin()) {
                plugin.getMessageManager().send(winner, MessageManager.Message.PRICE, message -> message
                        .replace("%amount%", PluginUtils.format(price))
                        .replace("%multiplier%", String.valueOf(slot.getMultiplier(this))));
            } else if (winType.isLaPartageWin()) {
                plugin.getMessageManager().send(winner, MessageManager.Message.LA_PARTAGE);
            } else if (winType.isEnPrisonWin()) {
                plugin.getMessageManager().send(winner, MessageManager.Message.EN_PRISON);
            } else {
                plugin.getMessageManager().send(winner, MessageManager.Message.SURRENDER);
            }

            Winner win = plugin.getWinnerManager().getByUniqueId(winner.getUniqueId());
            if (win == null) {
                win = new Winner(winner.getUniqueId());
            }

            Winner.WinnerData winnerData = new Winner.WinnerData(
                    name,
                    -1,
                    price,
                    System.currentTimeMillis(),
                    slot,
                    this.winner,
                    winType,
                    chip.getPrice());

            Map.Entry<Winner.WinnerData, ItemStack> entry;
            if (ConfigManager.Config.MAP_IMAGE_ENABLED.asBoolean() && (entry = plugin.getWinnerManager().render(winner.getName(), winnerData)) != null) {
                winnerData.setMapId(entry.getKey().getMapId());

                // Add map to inventory.
                ItemStack item = entry.getValue();
                winner.getInventory().addItem(item);
            }

            // Add win data.
            win.add(winnerData);

            // Save data to file.
            plugin.getWinnerManager().saveWinner(win);
        }

        if (ConfigManager.Config.RESTART_FIREWORKS.asInt() == 0) {
            broadcast(MessageManager.Message.RESTART.asString());
            restartRunnable();
            return;
        }

        new BukkitRunnable() {
            int amount = 0;

            @Override
            public void run() {
                if (amount == ConfigManager.Config.RESTART_FIREWORKS.asInt()) {
                    restart();
                    cancel();
                }
                spawnFirework(joinHologram.getLocation().clone().add(0.0d, 3.5d, 0.0d));
                amount++;
            }
        }.runTaskTimer(plugin, 0L, plugin.getConfigManager().getPeriod());

        broadcast(MessageManager.Message.RESTART.asString());
    }

    private void spawnBottle() {
        // Where to spawn the bottle.
        Location baseLocation = npc.getLocation().clone();

        double angle = Math.toRadians(90.0d);

        StandSettings oneSettings = new StandSettings();
        oneSettings.setSmall(true);
        oneSettings.setInvisible(true);
        oneSettings.setMainHand(XMaterial.EXPERIENCE_BOTTLE.parseItem());
        oneSettings.setRightArmPose(new EulerAngle(angle, 0.0d, 0.0d));

        // First part.
        selectedOne = new PacketStand(baseLocation, oneSettings);

        // Offset for the second part.
        Vector offset = new Vector(-0.32d, 0.0d, -0.24d);
        offset = PluginUtils.offsetVector(
                offset,
                model.getLocation().getYaw(),
                model.getLocation().getPitch());

        StandSettings twoSettings = oneSettings.clone();
        twoSettings.setRightArmPose(new EulerAngle(angle, angle, 0.0d));

        // Second part.
        selectedTwo = new PacketStand(baseLocation.clone().add(offset), twoSettings);

        // Where to teleport the bottle.
        Location finalLocation = model.getLocations().get(winner.name()).getKey()
                .clone()
                .add(PluginUtils.offsetVector(
                        new Vector(0.2525d, 0.0d, 0.5375d),
                        model.getLocation().getYaw(),
                        model.getLocation().getPitch()));

        // Add a bit of offset in the Y axis if there's a bet placed.
        if (alreadySelected(winner)) finalLocation.add(0.0d, 0.135d, 0.0d);

        // Teleport.
        selectedOne.teleport(finalLocation);
        selectedTwo.teleport(finalLocation.add(offset));
    }

    public void restartRunnable() {
        new BukkitRunnable() {
            int time = 0;

            @Override
            public void run() {
                if (time == ConfigManager.Config.RESTART_TIME.asInt()) {
                    restart();
                    cancel();
                }
                time++;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void spawnFirework(Location location) {
        Preconditions.checkNotNull(location.getWorld());
        ThreadLocalRandom random = ThreadLocalRandom.current();

        Firework firework = location.getWorld().spawn(location.clone().subtract(0.0d, 0.5d, 0.0d), Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();

        firework.setMetadata("isRoulette", new FixedMetadataValue(plugin, true));

        FireworkEffect.Builder builder = FireworkEffect.builder()
                .flicker(true)
                .trail(true)
                .withColor(PluginUtils.COLORS[random.nextInt(PluginUtils.COLORS.length)])
                .withFade(PluginUtils.COLORS[random.nextInt(PluginUtils.COLORS.length)]);

        FireworkEffect.Type[] types = FireworkEffect.Type.values();
        builder.with(types[random.nextInt(types.length)]);

        meta.addEffect(builder.build());
        meta.setPower(random.nextInt(1, 5));
        firework.setFireworkMeta(meta);

        if (ConfigManager.Config.INSTANT_EXPLODE.asBoolean()) {
            plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, firework::detonate, 1L);
        }
    }

    public void restart() {
        // Set game state to idle.
        setState(GameState.IDLE);

        // Hide ball.
        PacketStand ball = model.getByName("BALL");
        if (ball != null) ball.setEquipment(null, PacketStand.ItemSlot.HEAD);

        // Show ball in NPC hand.
        npc.equipment().queue(EnumWrappers.ItemSlot.MAINHAND, plugin.getConfigManager().getBall()).send();

        if (selectedOne != null) {
            selectedOne.destroy();
            selectedOne = null;
        }

        if (selectedTwo != null) {
            selectedTwo.destroy();
            selectedTwo = null;
        }

        Iterator<Map.Entry<Player, Bet>> iterator = players.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Player, Bet> entry = iterator.next();

            Player player = entry.getKey();
            Bet bet = entry.getValue();

            // Remove player only if prison rule isn't enabled (or the selected slot doesn't apply for that rule).
            if (!isRuleEnabled(GameRule.EN_PRISON) || !bet.getSlot().applyForRules() || bet.isWasEnPrison() || bet.isWon()) {
                // Remove hologram and chip.
                bet.remove();

                // Remove player.
                iterator.remove();
                remove(player, true);
                continue;
            }

            // Set the bet in prison and re-add player.
            bet.setEnPrison(true);
            add(player);
        }

        if (players.isEmpty()) {
            // Cancel tasks.
            cancelTasks(startingTask, selectingTask, spinningTask);
        }

        // Show join hologram to every player if hidden and update.
        joinHologram.setVisibleByDefault(true);
        if (plugin.isEnabled()) updateJoinHologram(false);

        // If the spin hologram has any lines, destroy it.
        if (spinHologram.size() > 0) {
            spinHologram.destroy();
        }
    }

    public boolean isSlotAvailable() {
        for (Slot slot : Slot.values(this)) {
            if (alreadySelected(slot)) continue;
            return true;
        }
        return false;
    }

    public boolean alreadySelected(Slot slot) {
        for (Player player : players.keySet()) {
            if (players.get(player).getSlot() == slot) return true;
        }
        return false;
    }

    public void remove() {
        // First, restart game.
        restart();

        // Remove model.
        model.kill();

        chairs.values().forEach(Entity::remove);
        chairs.clear();

        // Remove join hologram.
        joinHologram.destroy();

        // Remove spin hologram.
        spinHologram.destroy();

        // Remove croupier.
        plugin.getNpcPool().getNpcMap().remove(npc.getEntityId());
        for (Player seeing : npc.getSeeingPlayers()) {
            npc.visibility()
                    .queuePlayerListChange(EnumWrappers.PlayerInfoAction.REMOVE_PLAYER)
                    .queueDestroy()
                    .send(seeing);
            npc.removeSeeingPlayer(seeing);
        }
    }

    public UUID getModelId() {
        return model.getModelUniqueId();
    }

    public String getNPCName() {
        return npc.getProfile().getName().equalsIgnoreCase("") ? null : npc.getProfile().getName();
    }

    public Location getLocation() {
        return model.getLocation();
    }

    public boolean isRuleEnabled(GameRule rule) {
        return rules.getOrDefault(rule, false);
    }

    public void setLimitPlayers(int minPlayers, int maxPlayers) {
        // Set minimum amount of players required to start the game.
        this.minPlayers = (minPlayers < 1) ? 1 : Math.min(minPlayers, 10);

        // Set maximum amount of players.
        this.maxPlayers = (maxPlayers < minPlayers) ? minPlayers : Math.min(maxPlayers, 10);
    }
}