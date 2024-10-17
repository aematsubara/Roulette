package me.matsubara.roulette.game;

import com.cryptomorin.xseries.reflection.XReflection;
import com.github.retrooper.packetevents.protocol.entity.pose.EntityPose;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.animation.DabAnimation;
import me.matsubara.roulette.animation.MoneyAnimation;
import me.matsubara.roulette.event.RouletteEndEvent;
import me.matsubara.roulette.game.data.*;
import me.matsubara.roulette.game.state.Selecting;
import me.matsubara.roulette.game.state.Spinning;
import me.matsubara.roulette.game.state.Starting;
import me.matsubara.roulette.gui.RouletteGUI;
import me.matsubara.roulette.hologram.Hologram;
import me.matsubara.roulette.listener.npc.NPCSpawn;
import me.matsubara.roulette.manager.ChipManager;
import me.matsubara.roulette.manager.ConfigManager;
import me.matsubara.roulette.manager.MessageManager;
import me.matsubara.roulette.manager.data.DataManager;
import me.matsubara.roulette.manager.data.MapRecord;
import me.matsubara.roulette.manager.data.RouletteSession;
import me.matsubara.roulette.model.Model;
import me.matsubara.roulette.model.stand.PacketStand;
import me.matsubara.roulette.model.stand.StandSettings;
import me.matsubara.roulette.npc.NPC;
import me.matsubara.roulette.npc.modifier.MetadataModifier;
import me.matsubara.roulette.util.ParrotUtils;
import me.matsubara.roulette.util.PluginUtils;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.function.TriConsumer;
import org.apache.commons.lang3.function.TriFunction;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.Metadatable;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

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

    // The mininum number of players required in this game.
    private int minPlayers;

    // The maximum number of players allowed in this game.
    private int maxPlayers;

    // Players of this game and their bets.
    @Getter(AccessLevel.NONE)
    private final Multimap<Player, Bet> players = Multimaps.synchronizedMultimap(MultimapBuilder
            .hashKeys()
            .arrayListValues()
            .build());

    // The selected bet of a player.
    @Getter(AccessLevel.NONE)
    private final Map<Player, Integer> playerCurrentBet = new ConcurrentHashMap<>();

    // The glow color of a player's bet.
    @Getter(AccessLevel.NONE)
    private final Map<Player, ChatColor> playerGlowColor = new ConcurrentHashMap<>();

    // Disabled chips in this game.
    private final List<String> chipsDisabled = new ArrayList<>();

    // The players that are ready to play.
    @Getter(AccessLevel.NONE)
    private final Set<Player> playersDone = new HashSet<>();

    // The slots disabled in this game.
    private final List<Slot> disabledSlots;

    // Rules applied in this game.
    private final Map<GameRule, Boolean> rules = new EnumMap<>(GameRule.class);

    // Chairs of the game.
    private final Map<String, ArmorStand> chairs = new ConcurrentHashMap<>();

    // The main hologram of this game, used to join.
    private final Hologram joinHologram;

    // The spinning hologram.
    private final Hologram spinHologram;

    // The type of this game.
    private final GameType type;

    // The current state of this game.
    private GameState state;

    // The unique id of the player who'll receive the money from the bets.
    private UUID accountGiveTo;

    // Unique id of the player who created this game.
    private final UUID owner;

    // Time before starting the game.
    private int startTime;

    // If bet-all is allowed in this game.
    private boolean betAllEnabled;

    // Parrot data.
    private boolean parrotEnabled;
    private boolean parrotSounds;
    private Parrot.Variant parrotVariant;
    private ParrotUtils.ParrotShoulder parrotShoulder;
    private Object parrotNBT;

    // Tasks.
    private Starting starting;
    private Selecting selecting;
    private Spinning spinning;

    // Animations.
    private DabAnimation dabAnimation;
    private MoneyAnimation moneyAnimation;

    // The current slot selected in this game.
    private Slot winner;

    // Stands used for showing winner chip.
    private PacketStand selectedOne, selectedTwo;

    // Players being tranfered to another chair.
    private final Set<UUID> transfers = new HashSet<>();

    // Chairs of this game, range goes from 0 to max number of chairs; 10 in this case.
    private static final int[] CHAIRS = Model.CHAIR_SECOND_LAYER;

    // Adam textures.
    private static final TextureProperty ADAM_TEXTURES = new TextureProperty("textures",
            "eyJ0aW1lc3RhbXAiOjE1ODgwNjg2NjE4NDIsInByb2ZpbGVJZCI6IjMzZWJkMzJiYjMzOTRhZDlhYzY3MGM5NmM1NDliYTdlIiwicHJvZmlsZU5hbWUiOiJEYW5ub0JhbmFubm9YRCIsInNpZ25hdHVyZVJlcXVpcmVkIjp0cnVlLCJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzEzNzA5NzI0OWM0ZGNiOGU2YzY1ZjBlN2U3NTc3YmI3NzRjNWZjMjc0MTFhMjkwYWM4MGVkZmRmODFlNjk3YiJ9fX0=",
            "ShX80ZOUh6r67Qq+r8dvFkN7kqEUaUIB0JMdWTYFc0HZk/tqGvkRtExLgak5AWDA1Y2ruleJdCIE6eB851jhmKJG7zi9Zvzcfysb513MY14p2RdL8ZqX5NcC+0Qds2h/0ePlHD/uE3He+Kx43vs4GPl/SfciwlNjlURCeVpJ3MzRhUastaVwFFOFECNacY6HsT9Q6vEr7hLv9wLPvo5DpDU6FvS4v8KLlSGlgTpnayX61cQSeQyHbqabgBglTocp2NFs9YFjVbvq5WtLbsra5GLK+s+43/fN5NP4yBFAr08ZFu22YMeL6w51fDAPwZ2Gk4HunoPQMrhrRQkDN3RBSjALZyASHqVa49BKcJ+RNw08fLcSBYfUUZXDQabWHcqOVOlvE/5kusshcvbR86BWgYQfh5ObjGCt3P5fJ/1Dx3xNb6UKWOjl86ufkPfAhhPeUqYj/l6IAhm849oNl8q+r6nR2A641ibZySk7ZOX+Rr4lh67SgDIy1dPy2VQyHoHDIT4Joq3RNQZR+TwGRWd33EbakM6apDMMcuTxVm8lXMgYP89rBWNeEDsYbJ6L+NsypRfRfCgzap14bQ5vLZisXP1txcMUoUPv7KWJZ1CGmAI0VeODSTEZN73J0icWoniGZE74Eqvf+JGrHMF5keELN6IgQ1CIkMZO7OhxBqgi0o0=");

    // We want ListenMode to ignore our entities.
    private static final BiConsumer<JavaPlugin, Metadatable> LISTEN_MODE_IGNORE = (plugin, living) -> living.setMetadata("RemoveGlow", new FixedMetadataValue(plugin, true));

    // All valid colors for glowing, along with their texture url.
    private static final Map<ChatColor, String> GLOW_COLOR_URL = new LinkedHashMap<>();

    // Lore replacer for some messages.
    private static final TriFunction<Game, Bet, String, String> LORE_REPLACER = (game, bet, line) -> {
        Chip chip = bet.getChip();
        Slot slot = bet.getSlot();

        String numbers = slot.isDoubleZero() ? "[00]" : Arrays.toString(slot.getInts());

        double price = chip.price();
        WinData winData = bet.getWinData();
        WinData.WinType winType = winData != null ?
                winData.winType() : bet.isEnPrison() ?
                WinData.WinType.EN_PRISON :
                WinData.WinType.NORMAL;

        RoulettePlugin plugin = game.getPlugin();

        return line
                .replace("%bet%", PluginUtils.getSlotName(slot))
                .replace("%numbers%", numbers.substring(1, numbers.length() - 1)) // Remove brackets.
                .replace("%chance%", slot.getChance(game.getType().isEuropean()))
                .replace("%multiplier%", String.valueOf(slot.getMultiplier(plugin)))
                .replace("%money%", PluginUtils.format(price))
                .replace("%win-money%", PluginUtils.format(plugin.getExpectedMoney(price, slot, winType)))
                .replace("%rule%", plugin.formatWinType(winType));
    };

    // All valid colors for glowing.
    public static final ChatColor[] GLOW_COLORS;

    static {
        GLOW_COLOR_URL.put(ChatColor.BLACK, "2a52d579afe2fdf7b8ecfa746cd016150d96beb75009bb2733ade15d487c42a1");
        GLOW_COLOR_URL.put(ChatColor.DARK_BLUE, "fe4c1b36e5d8e2fa6d55134753eefb2f52302d20f4dac554b1afe5711b93cc");
        GLOW_COLOR_URL.put(ChatColor.DARK_GREEN, "eb879f5764385ed6bb90755bb041574882e2f41ab9323576016cfbe7f16397a");
        GLOW_COLOR_URL.put(ChatColor.DARK_AQUA, "d5bbd4a69d208dd25dd95ad4b0f5c7c4b2e0d626161bb1ebf3bcc7e88fd4a960");
        GLOW_COLOR_URL.put(ChatColor.DARK_RED, "97d0b9b3c419d3e321397bedc6dcd649e51cc2fa36b883b02f4da39582cdff1b");
        GLOW_COLOR_URL.put(ChatColor.DARK_PURPLE, "b09fa999c27a947a0aa5d4478da26ab0f189f180a7fb1ec8adcef6df76879");
        GLOW_COLOR_URL.put(ChatColor.GOLD, "c8e44023e11eeb5b293d086351e29e6ffaec01b768dc1460b1be54b809bd6dbf");
        GLOW_COLOR_URL.put(ChatColor.GRAY, "1b9c45d6c7cd0116436c31ed4d8dc825de03e806edb64e9a67f540b8aaae85");
        GLOW_COLOR_URL.put(ChatColor.DARK_GRAY, "b2554dda80ea64b18bc375b81ce1ed1907fc81aea6b1cf3c4f7ad3144389f64c");
        GLOW_COLOR_URL.put(ChatColor.BLUE, "3b5106b060eaf398217349f3cfb4f2c7c4fd9a0b0307a17eba6af7889be0fbe6");
        GLOW_COLOR_URL.put(ChatColor.GREEN, "ac01f6796eb63d0e8a759281d037f7b3843090f9a456a74f786d049065c914c7");
        GLOW_COLOR_URL.put(ChatColor.AQUA, "4548789b968c70ec9d1de272d0bb93a70134f2c0e60acb75e8d455a1650f3977");
        GLOW_COLOR_URL.put(ChatColor.RED, "3c4d7a3bc3de833d3032e85a0bf6f2bef7687862b3c6bc40ce731064f615dd9d");
        GLOW_COLOR_URL.put(ChatColor.LIGHT_PURPLE, "205c17650e5d747010e8b69a6f2363fd11eb93f81c6ce99bf03895cefb92baa");
        GLOW_COLOR_URL.put(ChatColor.YELLOW, "200bf4bf14c8699c0f9209ca79fe18253e901e9ec3876a2ba095da052f69eba7");
        GLOW_COLOR_URL.put(ChatColor.WHITE, "1884d5dabe073e28e6b7eb166ff61247905c79f838b6f5752e7ad406091eeaf3");
        GLOW_COLORS = GLOW_COLOR_URL.keySet().toArray(ChatColor[]::new);
    }

    public Game(
            RoulettePlugin plugin,
            String name,
            @Nullable String npcName,
            @Nullable String npcTexture,
            @Nullable String npcSignature,
            @NotNull Model model,
            int minPlayers,
            int maxPlayers,
            GameType type,
            UUID owner,
            int startTime,
            boolean betAllEnabled,
            @Nullable UUID accountGiveTo,
            @Nullable EnumMap<GameRule, Boolean> rules,
            boolean parrotEnabled,
            boolean parrotSounds,
            @Nullable Parrot.Variant parrotVariant,
            @Nullable ParrotUtils.ParrotShoulder parrotShoulder,
            @Nullable List<String> chipsDisabled) {
        this.plugin = plugin;
        this.name = name;
        this.model = model;
        this.owner = owner;

        // Initialize parrot data before spawning NPC.
        this.parrotEnabled = parrotEnabled;
        this.parrotSounds = parrotSounds;
        this.parrotVariant = parrotVariant != null ? parrotVariant : PluginUtils.getRandomFromEnum(Parrot.Variant.class);
        this.parrotShoulder = parrotShoulder != null ? parrotShoulder : PluginUtils.getRandomFromEnum(ParrotUtils.ParrotShoulder.class);

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

        if (rules != null && !rules.isEmpty()) this.rules.putAll(rules);
        if (chipsDisabled != null && !chipsDisabled.isEmpty()) this.chipsDisabled.addAll(chipsDisabled);

        this.type = type;
        this.state = GameState.IDLE;
        this.startTime = startTime;
        this.betAllEnabled = betAllEnabled;
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

        // Spawn chairs.
        handleChairs();
    }

    public void handleChairs() {
        for (int chair : CHAIRS) {
            String key = "CHAIR_" + chair;

            ArmorStand temp = chairs.get(key);
            if (temp != null && temp.isValid()) continue;

            if (temp != null) {
                temp.eject();
                temp.remove();
            }

            chairs.put(key, spawnChairStand(key));
        }
    }

    public @Nullable ArmorStand getChair(int chair) {
        String key = "CHAIR_" + chair;

        ArmorStand stand = chairs.get(key);
        if (stand == null || !stand.isValid()) return null;

        return stand;
    }

    private @Nullable ArmorStand spawnChairStand(String name) {
        PacketStand stand = model.getByName(name);
        if (stand == null) return null;

        World world = getLocation().getWorld();
        if (world == null) return null;

        // Fix visual issue since 1.20.2.
        Location standLocation = stand.getLocation().clone();
        if (XReflection.supports(20, 2)) standLocation.subtract(0.0d, 0.3d, 0.0d);

        return world.spawn(standLocation, ArmorStand.class, bukkit -> {
            StandSettings settings = stand.getSettings();

            bukkit.setInvisible(settings.isInvisible());
            bukkit.setSmall(settings.isSmall());
            bukkit.setBasePlate(settings.isBasePlate());
            bukkit.setArms(settings.isArms());
            bukkit.setFireTicks(settings.isFire() ? Integer.MAX_VALUE : 0);
            bukkit.setMarker(settings.isMarker());
            bukkit.setPersistent(false);
            bukkit.setGravity(false);

            // Set poses.
            bukkit.setHeadPose(settings.getHeadPose());
            bukkit.setBodyPose(settings.getBodyPose());
            bukkit.setLeftArmPose(settings.getLeftArmPose());
            bukkit.setRightArmPose(settings.getRightArmPose());
            bukkit.setLeftLegPose(settings.getLeftLegPose());
            bukkit.setRightLegPose(settings.getRightLegPose());

            // Hide hearts.
            AttributeInstance attribute = bukkit.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (attribute != null) attribute.setBaseValue(1);

            LISTEN_MODE_IGNORE.accept(plugin, bukkit);
        });
    }

    private @NotNull Location generateNPCLocation() {
        Location location = getLocation().clone();
        location.add(PluginUtils.offsetVector(new Vector(-3, 0, 1), location.getYaw(), location.getPitch()));
        return location.setDirection(PluginUtils.getDirection(PluginUtils.getFace(location.getYaw(), false).getOppositeFace()));
    }

    public boolean hasNPCTexture() {
        return npc.getProfile().getTextureProperties().stream().anyMatch(this::isCustomNPCTexture);
    }

    public boolean isCustomNPCTexture(@NotNull TextureProperty property) {
        return property.getName().equals("textures") && isCustomNPCTexture(property.getValue(), property.getSignature());
    }

    public boolean isCustomNPCTexture(String texture, String signature) {
        return texture != null && !texture.equals(ADAM_TEXTURES.getValue()) && signature != null && !signature.equals(ADAM_TEXTURES.getSignature());
    }

    public @Nullable String getNPCTexture() {
        return npc.getProfile().getTextureProperties().stream()
                .findFirst()
                .map(TextureProperty::getValue)
                .orElse(ADAM_TEXTURES.getValue());
    }

    public @Nullable String getNpcTextureAsURL() {
        String texture = getNPCTexture();
        return texture != null ? PluginUtils.getURLFromTexture(texture) : null;
    }

    public @Nullable String getNPCSignature() {
        return npc.getProfile().getTextureProperties().stream()
                .findFirst()
                .map(TextureProperty::getSignature)
                .orElse(ADAM_TEXTURES.getSignature());
    }

    public void setNPC(@Nullable String name, @Nullable String texture, @Nullable String signature) {
        // If this game already has an NPC, remove first.
        if (npc != null) {
            plugin.getNpcPool().removeNPC(npc.getEntityId());
        }

        String unnamed = ConfigManager.Config.UNNAMED_CROUPIER.asString();
        if (name == null || name.isEmpty()) name = unnamed;

        // Hide npc name if empty unnamed.
        if (name.equalsIgnoreCase(unnamed)) {
            plugin.getHideTeam().addEntry(name);
        }

        UserProfile profile = new UserProfile(UUID.randomUUID(), name);

        // Set NPC skin texture (if possible).
        TextureProperty textures = isCustomNPCTexture(texture, signature) ? new TextureProperty("textures", texture, signature) : ADAM_TEXTURES;
        profile.setTextureProperties(List.of(textures));

        Location npcLocation = generateNPCLocation();

        npc = NPC.builder()
                .profile(profile)
                .location(npcLocation)
                .spawnCustomizer(new NPCSpawn(this))
                .entityId(SpigotReflectionUtil.generateEntityId())
                .game(this)
                .build(plugin.getNpcPool());

        npc.lookAtDefaultLocation();
    }

    public void playSound(String sound) {
        for (Player player : getPlayers()) {
            playSound(sound, player);
        }
    }

    public void playSound(String sound, Player player) {
        Sound toPlay = PluginUtils.getOrNull(Sound.class, sound);
        if (toPlay != null) player.playSound(player.getLocation(), toPlay, 1.0f, 1.0f);
    }

    public void broadcast(MessageManager.Message message) {
        broadcast(message, null);
    }

    public void broadcast(MessageManager.Message message, @Nullable UnaryOperator<String> operator) {
        broadcast(message, operator, null);
    }

    public void broadcast(MessageManager.Message message, @Nullable UnaryOperator<String> operator, @Nullable Player except) {
        for (Player player : getPlayers()) {
            if (player.equals(except)) continue;
            plugin.getMessageManager().send(player, message, operator);
        }
    }

    public void npcBroadcast(MessageManager.Message message) {
        for (Player player : getPlayers()) {
            plugin.getMessageManager().sendNPCMessage(player, this, message);
        }
    }

    public int size() {
        return getPlayers().size();
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public boolean isFull() {
        return size() == maxPlayers;
    }

    public boolean isPlaying(Player player) {
        return players.containsKey(player);
    }

    public boolean canJoin() {
        return state.isIdle() || state.isStarting();
    }

    public void add(Player player, int sitAt) {
        // The player may still be in the game if prison rule is enabled.
        if (!isPlaying(player)) {
            // Add player to the game and sit.
            addEmptyBetAndSelect(player);

            if (sitAt != -1) {
                // At this point, this won't be null.
                ArmorStand stand = getChair(sitAt);
                if (stand != null) fixChairCameraAndSit(player, stand);
            } else {
                sitPlayer(player, true, true);
            }
        }

        // Remove from the done set.
        setNotReady(player);

        // Can be greater than 0 when prison rule is enabled, since players aren't removed from the game.
        if (size() >= minPlayers && (starting == null || starting.isCancelled())) {
            // Start a starting task.
            starting = new Starting(plugin, this);
            starting.runTaskTimer(plugin, 20L, 20L);
        }

        joinHologram.hideTo(player);
        updateJoinHologram(false);

        spinHologram.showTo(player);
    }

    public void tryToReturnMoney(Player player, @Nullable Bet bet) {
        if (!state.isSelecting()) return;

        // Ignore prison bets.
        double price = bet != null ?
                (bet.hasChip() && !bet.isEnPrison() ? bet.getChip().price() : 0.0d) : // Single chip.
                getBets(player).stream()
                        .filter(temp -> temp.hasChip() && !temp.isEnPrison())
                        .mapToDouble(temp -> temp.getChip().price())
                        .sum(); // Multiple chips.

        if (price == 0.0d || !plugin.deposit(player, price)) return;

        sendMoneyReceivedMessage(player, price);
    }

    public void sendMoneyReceivedMessage(Player player, double money) {
        plugin.getMessageManager().send(player, MessageManager.Message.RECEIVED, message -> message
                .replace("%money%", PluginUtils.format(money))
                .replace("%name%", name.replace("_", " ")));
    }

    public void removeCompletely(Player player) {
        // Remove the player from the game, completely (will restart the game).
        remove(player, null, false);
    }

    public void removePartially(Player player) {
        removePartially(player, null);
    }

    public void removePartially(Player player, @Nullable Bet bet) {
        // Remove a single bet of the player (the player may still be in the game).
        remove(player, bet, true);
    }

    @ApiStatus.Internal
    public void remove(Player player, @Nullable Bet bet, boolean iteratorSource) {
        // If the player quits with a bet placed but the wheel didn't start spinning, then we want to return the money.
        tryToReturnMoney(player, bet);

        // Add player to FOV again to prevent sending invite messages again.
        npc.setInsideFOV(player);

        // Remove from the done set.
        setNotReady(player);

        // If the game is being restarted, the players are removed in restart() iterator.
        if (!iteratorSource) {
            players.removeAll(player).forEach(Bet::remove);

            // Players are removed with an iterator in @restart, we don't need to send a message to every player about it if restarting.
            broadcast(MessageManager.Message.LEAVE, line -> line
                    .replace("%player-name%", player.getName())
                    .replace("%playing%", String.valueOf(size()))
                    .replace("%max%", String.valueOf(maxPlayers)));
        }

        // No need to call restart() again if the game is being restarted.
        if (isEmpty() && !iteratorSource) {
            restart();
        }

        // If at this point, the player still on the game, then that's because only one bet of the player was removed.
        if (isPlaying(player)) return;

        // Remove the player from the chair (if sitting).
        if (isSittingOn(player)
                && player.getVehicle() instanceof ArmorStand chair
                && chairs.containsValue(chair)) {
            player.leaveVehicle();
        }

        if (state.isIdle() || state.isStarting()) {
            joinHologram.showTo(player);
            updateJoinHologram(false);
        }

        // Hide spin hologram to the player.
        spinHologram.hideTo(player);
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

    private @NotNull String replaceJoinHologramLines(@NotNull String line) {
        ChipManager chipManager = plugin.getChipManager();
        return line
                .replace("%name%", name.replace("_", " "))
                .replace("%playing%", String.valueOf(size()))
                .replace("%max%", String.valueOf(maxPlayers))
                .replace("%type%", type.getName())
                .replace("%min-amount%", PluginUtils.format(chipManager.getMinAmount(this)))
                .replace("%max-amount%", PluginUtils.format(chipManager.getMaxAmount(this)));
    }

    private void cancelTasks(BukkitRunnable... runnables) {
        for (BukkitRunnable runnable : runnables) {
            if (runnable != null && !runnable.isCancelled()) {
                runnable.cancel();
            }
        }
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
        return getSittingOn(player) != -1;
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
        sitPlayer(player, toTheRight, false);
    }

    public void sitPlayer(Player player, boolean right, boolean isAdd) {
        // If not a chair available.
        if (!isChairAvailable()) return;

        if (right && !isSittingOn(player)) {
            // Find an available chair to the right or left from the first chair.
            // We use the last chair as the current since we want to start checking from the first chair.
            ArmorStand chair = getAvailableChair(player, CHAIRS[CHAIRS.length - 1], true);

            // Sit player.
            fixChairCameraAndSit(player, chair);
            return;
        }

        // Shouldn't happen.
        if (!isSittingOn(player)) return;

        int sittingOn = getSittingOn(player);

        // Add to transfer BEFORE dismounting.
        if (!isAdd) transfers.add(player.getUniqueId());

        // Now we want to dismount the player.
        player.leaveVehicle();

        // Find an available chair to the right or left from the current chair.
        ArmorStand chair = getAvailableChair(player, sittingOn, right);

        // Finally, sit player.
        fixChairCameraAndSit(player, chair);
    }

    private void fixChairCameraAndSit(Player player, ArmorStand stand) {
        if (ConfigManager.Config.FIX_CHAIR_CAMERA.asBool()) {
            // Add a bit of offset.
            player.teleport(stand.getLocation().clone().add(0.0d, 0.25d, 0.0d));
        }

        // Play move from chair sound at player location.
        Sound swapChairSound = PluginUtils.getOrNull(Sound.class, ConfigManager.Config.SOUND_SWAP_CHAIR.asString());
        if (swapChairSound != null) {
            player.getWorld().playSound(player.getLocation(), swapChairSound, 1.0f, 1.0f);
        }

        stand.addPassenger(player);
    }

    public void moveDirectionalChip(Player player, float forward, float sideways) {
        // If not slot available, return.
        if (!isSlotAvailable(player)) return;

        Bet bet = getSelectedBet(player);
        if (bet == null || !bet.hasChip() || !bet.hasSlot()) return;

        boolean up = forward > 0.0f, down = forward < 0.0f;
        boolean left = sideways > 0.0f, right = sideways < 0.0f;

        int chair = ArrayUtils.indexOf(CHAIRS, getSittingOn(player)) + 1;
        if (chair == 0) return;

        // Fix orientation based on the player chair.
        boolean first = chair < 5, middle = chair == 5 || chair == 6;
        boolean xUp = first ? up : middle ? right : down;
        boolean xDown = first ? down : middle ? left : up;
        boolean xLeft = first ? left : middle ? up : right;
        boolean xRight = first ? right : middle ? down : left;

        Slot currentSlot = bet.getSlot();
        Slot slot = currentSlot;
        do {
            // Order of execution = up, down, left, right
            PluginUtils.SlotHolder holder = PluginUtils.moveFromSlot(this, slot,
                    xUp,
                    xDown,
                    xLeft,
                    xRight);

            slot = holder == null ? null : holder.getSlot();
            if (slot != null
                    && alreadySelected(player, slot)
                    && holder instanceof PluginUtils.SlotHolderPair pair) {
                slot = pair.getSimilar();
            }

            if (slot == null) break; // If this is null, then we can't move to that side.
        } while (alreadySelected(player, slot));

        // Teleport hologram and chip.
        if (slot != null && currentSlot != slot) {
            bet.handle(slot);
        }
    }

    @ApiStatus.Internal
    public void firstChipMove(Player player) {
        // If not slot available, return.
        if (!isSlotAvailable(player)) return;

        // If the player didn't select a chip from the GUI yet, return.
        Bet bet = getSelectedBet(player);
        if (bet == null || !bet.hasChip() || bet.hasSlot()) return;

        Slot[] slots = Slot.values(this);

        // Find an available slot to the right or left from the first slot.
        // We use the last slot as the current since we want to start checking from the first slot.
        Slot slot = getAvailableSlot(player, slots[slots.length - 1]);

        // Spawn hologram and chip.
        bet.handle(slot);
    }

    private ArmorStand getAvailableChair(Player player, int currentChair, boolean right) {
        return PluginUtils.getAvailable(player,
                ArrayUtils.toObject(CHAIRS),
                currentChair,
                (temp, value) -> {
                    ArmorStand chair = getChair(value);
                    return chair == null || !chair.getPassengers().isEmpty();
                },
                right,
                this::getChair);
    }

    private Slot getAvailableSlot(Player player, Slot currentSlot) {
        Slot[] slots = Slot.values(this);
        return PluginUtils.getAvailable(player,
                slots,
                currentSlot,
                this::alreadySelected,
                true,
                Function.identity());
    }

    public void checkWinner() {
        spawnBottle();

        for (Player player : getPlayers()) {
            List<Bet> bets = getBets(player);
            if (bets.isEmpty()) continue;

            for (int i = 0; i < bets.size(); i++) {
                Bet bet = bets.get(i);

                Slot slot = bet.getSlot();

                // Check for single numbers or slots with more than 1 number.
                if (slot == winner || slot.contains(winner)) {
                    boolean prisonWin = isRuleEnabled(GameRule.EN_PRISON) && slot.applyForRules() && bet.isEnPrison();
                    bet.setWinData(new WinData(player, i, prisonWin ? WinData.WinType.EN_PRISON : WinData.WinType.NORMAL));
                    continue;
                }

                // If the checks above didn't make it, check for rules.

                // Partage.
                if (isRuleEnabled(GameRule.LA_PARTAGE) && winner.isZero() && slot.applyForRules()) {
                    bet.setWinData(new WinData(player, i, WinData.WinType.LA_PARTAGE));
                    continue;
                }

                // Surrender.
                if (type.isAmerican() && isRuleEnabled(GameRule.SURRENDER) && winner.isAnyZero() && slot.applyForRules()) {
                    bet.setWinData(new WinData(player, i, WinData.WinType.SURRENDER));
                }
            }
        }

        // Players that won at least once of their bets.
        Set<Player> winners = players.entries().stream()
                .filter(entry -> entry.getValue().getWinData() != null)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        RouletteEndEvent endEvent = new RouletteEndEvent(this, winners, winner);
        plugin.getServer().getPluginManager().callEvent(endEvent);

        // Set all winner bets as winner (a check is inside of Bet#setWon).
        getAllBets().forEach(Bet::setWon);

        // Save session to the database.
        DataManager dataManager = plugin.getDataManager();
        RouletteSession session = dataManager.saveSession(UUID.randomUUID(), name, players.entries(), winner, System.currentTimeMillis());

        // Create and save maps to the database (if enabled).
        if (ConfigManager.Config.MAP_IMAGE_ENABLED.asBool()) {
            List<MapRecord> maps = new ArrayList<>();

            for (Player winner : winners) {
                if (!winner.isValid() || !winner.isOnline()) continue;

                UUID winnerUUID = winner.getUniqueId();

                Map.Entry<Integer, ItemStack> entry = plugin.getWinnerManager().render(winnerUUID, session);
                if (entry == null) continue;

                winner.getInventory().addItem(entry.getValue());

                // Even if the player won more than 1 bet, we need to save only 1 map.
                maps.add(new MapRecord(entry.getKey(), winnerUUID, session.sessionUUID()));
            }

            if (!maps.isEmpty()) {
                dataManager.saveMaps(maps);
            }
        }

        // Send money to the account of the game.
        if (accountGiveTo != null) {
            OfflinePlayer giveTo = Bukkit.getOfflinePlayer(accountGiveTo);
            for (Player player : getPlayers()) {
                // Don't take money from the same player.
                if (giveTo.getUniqueId().equals(player.getUniqueId())) continue;

                // Sum the money from all the bets.
                double price = getBets(player).stream()
                        .filter(bet -> bet.hasChip() && bet.getWinData() == null) // Don't take money from winning bets.
                        .mapToDouble(bet -> bet.getChip().price())
                        .sum();
                if (price == 0.0d
                        || !plugin.deposit(giveTo, price)
                        || !giveTo.isOnline()) continue;

                sendMoneyReceivedMessage(giveTo.getPlayer(), price);
            }
        }

        if (winners.isEmpty()) {
            broadcast(MessageManager.Message.NO_WINNER, line -> line.replace("%winner-slot%", PluginUtils.getSlotName(winner)));
            getPlayers().forEach(this::sendPersonalBets);
            broadcast(MessageManager.Message.RESTART);
            remindBetInPrison();
            restartRunnable();
            return;
        }

        String[] names = getAllBets().stream().filter(bet -> bet.getWinData() != null)
                .map(bet -> bet.getWinData().player().getName())
                .distinct()
                .toArray(String[]::new);

        npcBroadcast(MessageManager.Message.WINNER);

        for (Player player : getPlayers()) {
            // Send all winners.
            sendBetsMessage(
                    player,
                    MessageManager.Message.ALL_WINNERS,
                    "%winner%",
                    line -> line
                            .replace("%amount%", String.valueOf(names.length))
                            .replace("%winner-slot%", PluginUtils.getSlotName(winner)),
                    (playing, lore, indexOf) -> sendWinners(playing, winners, lore, indexOf));
            sendPersonalBets(player);
        }

        broadcast(MessageManager.Message.RESTART);
        remindBetInPrison();

        // Transfer the winning money to the players who won.
        for (Player winner : winners) {
            List<Bet> bets = getBets(winner);

            // Sum the money from all the winning bets.
            double price = bets.stream()
                    .filter(bet -> bet.hasChip() && bet.getWinData() != null)
                    .mapToDouble(plugin::getExpectedMoney)
                    .sum();

            // Give all the winning money in a single transaction.
            plugin.deposit(winner, price);
        }

        // Start dab animation for the player who won more money.
        handleDabAnimation();

        if (ConfigManager.Config.RESTART_FIREWORKS.asInt() == 0) {
            restartRunnable();
            return;
        }

        long period = plugin.getConfigManager().getPeriod();
        new BukkitRunnable() {

            private int amount = 0;
            private final Location fireworkLocation = joinHologram.getLocation()
                    .clone()
                    .add(0.0d, 3.0d, 0.0d);

            @Override
            public void run() {
                if (amount == ConfigManager.Config.RESTART_FIREWORKS.asInt()) {
                    restart();
                    cancel();
                }

                spawnFirework(fireworkLocation);
                amount++;
            }
        }.runTaskTimer(plugin, period, period);
    }

    private void handleDabAnimation() {
        if (!ConfigManager.Config.DAB_ANIMATION_ENABLED.asBool()) return;

        // Start dab animation for the player who won more money and was a single bet.
        Optional<Bet> max = getAllBets().stream()
                .filter(bet -> bet.isWon() && bet.getSlot().isSingleInclusive())
                .max((first, second) -> Double.compare(
                        plugin.getExpectedMoney(first),
                        plugin.getExpectedMoney(second)));
        max.ifPresent(bet -> new DabAnimation(this, bet.getOwner(), getLocation().clone()));
    }

    private void sendPersonalBets(Player player) {
        List<Bet> bets = getBets(player);

        double total = bets.stream()
                .filter(bet -> bet.hasChip() && bet.getWinData() != null)
                .mapToDouble(plugin::getExpectedMoney)
                .sum();

        double totalLost = bets.stream()
                .filter(bet -> bet.hasChip() && bet.getWinData() == null)
                .mapToDouble(bet -> bet.getChip().price())
                .sum();

        sendBets(
                player,
                MessageManager.Message.YOUR_WINNING_BETS,
                MessageManager.Message.WINNING_BET_HOVER,
                line -> line
                        .replace("%total-money%", PluginUtils.format(total))
                        .replace("%lost-money%", PluginUtils.format(totalLost)),
                true);
    }

    @SuppressWarnings("SameParameterValue")
    public void sendBetsMessage(Player player,
                                @NotNull MessageManager.Message message,
                                String variable,
                                UnaryOperator<String> loreReplacer,
                                TriConsumer<Player, List<String>, Integer> midAction) {
        List<String> lore = message.asList();
        if (lore.isEmpty()) return;
        else lore.replaceAll(loreReplacer);

        int indexOf = -1;
        for (int i = 0; i < lore.size(); i++) {
            if (lore.get(i).contains(variable)) {
                indexOf = i;
                break;
            }
        }

        if (indexOf == -1) {
            lore.forEach(player::sendMessage);
            return;
        }

        if (indexOf > 0) {
            for (String line : lore.subList(0, indexOf)) {
                player.sendMessage(line);
            }
        }

        midAction.accept(player, lore, indexOf);

        if (lore.size() > 1 && indexOf < lore.size() - 1) {
            for (String line : lore.subList(indexOf + 1, lore.size())) {
                player.sendMessage(line);
            }
        }
    }

    private void sendWinners(Player player, @NotNull Set<Player> winners, List<String> lore, int indexOf) {
        for (Player winner : winners) {
            // Build the hover message.
            StringBuilder builder = new StringBuilder();
            List<Bet> bets = getBets(winner);

            List<String> hoverLines = MessageManager.Message.WINNER_HOVER.asList();
            for (int i = 0; i < hoverLines.size(); i++) {

                double total = bets.stream()
                        .filter(bet -> bet.hasChip() && bet.getWinData() != null)
                        .mapToDouble(plugin::getExpectedMoney)
                        .sum();

                int count = (int) bets.stream()
                        .filter(bet -> bet.hasChip() && bet.getWinData() != null)
                        .count();

                builder.append(hoverLines.get(i)
                        .replace("%money%", PluginUtils.format(total))
                        .replace("%bets%", String.valueOf(count)));

                if (i != hoverLines.size() - 1) builder.append("\n");
            }

            String text = lore.get(indexOf).replace("%winner%", winner.getName());
            HoverEvent hoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(builder.toString()));

            BaseComponent[] components = TextComponent.fromLegacyText(text);
            for (BaseComponent component : components) {
                component.setHoverEvent(hoverEvent);
            }

            player.spigot().sendMessage(components);
        }
    }

    private void remindBetInPrison() {
        players.entries().stream()
                .filter(entry -> betAppliesForPrison(entry.getValue(), false))
                .map(Map.Entry::getKey)
                .forEach(player -> plugin.getMessageManager().send(player, MessageManager.Message.PRISON_REMINDER));
    }

    private void spawnBottle() {
        // Where to spawn the bottle.
        Location baseLocation = npc.getLocation().clone();

        double angle = Math.toRadians(90.0d);

        StandSettings oneSettings = new StandSettings();
        oneSettings.setSmall(true);
        oneSettings.setInvisible(true);
        oneSettings.getEquipment().put(PacketStand.ItemSlot.MAINHAND, new ItemStack(Material.EXPERIENCE_BOTTLE));
        oneSettings.setRightArmPose(new EulerAngle(angle, 0.0d, 0.0d));

        // First part.
        selectedOne = new PacketStand(baseLocation, oneSettings, true);

        Location modelLocation = getLocation();
        float yaw = modelLocation.getYaw(), pitch = modelLocation.getPitch();

        // Offset for the second part.
        Vector offset = PluginUtils.offsetVector(new Vector(-0.32d, 0.0d, -0.24d), yaw, pitch);

        StandSettings twoSettings = oneSettings.clone();
        twoSettings.setRightArmPose(new EulerAngle(angle, angle, 0.0d));

        // Second part.
        selectedTwo = new PacketStand(baseLocation.clone().add(offset), twoSettings, true);

        // Where to teleport the bottle.
        PacketStand temp = model.getByName(winner.name());
        if (temp == null) return;

        Location bottleLocation = temp.getLocation().clone();

        // Offset depending on the number of bets in the winner slot.
        Vector slotOffset = getWinnerSlotOffset();
        if (slotOffset != null) bottleLocation.add(PluginUtils.offsetVector(slotOffset, yaw, pitch));

        // offset from the slot location.
        bottleLocation.add(PluginUtils.offsetVector(new Vector(0.2525d, 0.0d, 0.5375d), yaw, pitch));

        // Add a bit of offset in the Y axis if there's a bet placed.
        if (slotOffset != null) bottleLocation.add(0.0d, 0.135d, 0.0d);

        // Teleport.
        selectedOne.teleport(bottleLocation);
        selectedTwo.teleport(bottleLocation.add(offset));
    }

    private @Nullable Vector getWinnerSlotOffset() {
        Pair<Axis, double[]> offset = winner.getOffsets(type.isEuropean());

        double[] offsets = offset.getRight();
        Axis axis = offset.getLeft();

        // If no bet in the winning slot, place it at the table.
        List<Bet> betsInWinnerSlot = getAllBets().stream()
                .filter(bet -> bet.getSlot() == winner)
                .toList();
        if (betsInWinnerSlot.isEmpty()) return null;

        Bet randomBet = betsInWinnerSlot.get(PluginUtils.RANDOM.nextInt(0, betsInWinnerSlot.size()));
        int offsetIndex = randomBet.getOffsetIndex();

        return new Vector(axis == Axis.X ? offsets[offsetIndex] : 0.0d, 0.0d, axis == Axis.Z ? offsets[offsetIndex] : 0.0d);
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

    private void spawnFirework(@NotNull Location location) {
        World world = location.getWorld();
        Preconditions.checkNotNull(world);

        Random random = PluginUtils.RANDOM;

        // We want the fireworks to have the same yaw as the NPC,
        // so firework effects like creeper have the right rotation.
        Location npcLocation = npc.getLocation();
        location.setYaw(npcLocation.getYaw());
        location.setPitch(npcLocation.getPitch());

        Firework firework = world.spawn(
                location,
                Firework.class,
                temp -> LISTEN_MODE_IGNORE.accept(plugin, temp));
        FireworkMeta meta = firework.getFireworkMeta();

        firework.setMetadata("isRoulette", new FixedMetadataValue(plugin, true));

        FireworkEffect.Builder builder = FireworkEffect.builder()
                .flicker(true)
                .trail(true)
                .withColor(PluginUtils.getRandomColor())
                .withFade(PluginUtils.getRandomColor());

        builder.with(PluginUtils.getRandomFromEnum(FireworkEffect.Type.class));

        meta.addEffect(builder.build());
        meta.setPower(random.nextInt(1, 5));
        firework.setFireworkMeta(meta);

        if (ConfigManager.Config.INSTANT_EXPLODE.asBool()) {
            plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, firework::detonate, 1L);
        }
    }

    public void restart() {
        restart(false);
    }

    public void restart(boolean forceRemove) {
        // Set game state to idle.
        setState(GameState.IDLE);

        // Hide the ball.
        PacketStand ball = model.getByName("BALL");
        if (ball != null) ball.setEquipment(new ItemStack(Material.AIR), PacketStand.ItemSlot.HEAD);

        // Stand the NPC.
        npc.metadata().queue(MetadataModifier.EntityMetadata.POSE, EntityPose.STANDING).send();

        // Show the ball in the NPC hand.
        npc.equipment().queue(EquipmentSlot.MAIN_HAND, plugin.getConfigManager().getBall()).send();

        if (selectedOne != null) {
            selectedOne.destroy();
            selectedOne = null;
        }

        if (selectedTwo != null) {
            selectedTwo.destroy();
            selectedTwo = null;
        }

        Set<Player> reAdd = new LinkedHashSet<>();

        Iterator<Map.Entry<Player, Bet>> iterator = players.entries().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Player, Bet> entry = iterator.next();

            Player player = entry.getKey();
            Bet bet = entry.getValue();

            // Set the bet in prison and re-add player, keeping the bet.
            if (betAppliesForPrison(bet, false)) {
                bet.setEnPrison(true);
                add(player, -1);
                continue;
            }

            // Remove hologram and chip.
            bet.remove();

            // Remove bet entry.
            iterator.remove();

            // If the player left his seat or doesn't have enough money, don't count him for the next game.
            if (!forceRemove && ConfigManager.Config.KEEP_SEAT.asBool() && isSittingOn(player)) {
                // Keep player, put the bet manually since it's not set in add().
                reAdd.add(player);
            } else {
                // Remove player.
                removePartially(player, bet);
            }
        }

        // We need to re-add the players here to prevent concurrent issues.
        for (Player player : reAdd) {
            // No need to add an empty bet, the player is in prison.
            if (hasBetsInPrison(player)) continue;

            // The player is broke, remove him.
            if (!plugin.getChipManager().hasEnoughMoney(this, player)) {
                removePartially(player);
                continue;
            }

            addEmptyBetAndSelect(player);
            add(player, -1);
        }

        // Select the last chip for the players remaining and set as done.
        getPlayers().forEach(player -> {
            selectLast(player);
            if (hasBetsInPrison(player)) {
                setDone(player);
            }
        });

        if (isEmpty()) {
            // Cancel tasks (except for MoneyAnimation).
            cancelTasks(starting, selecting, spinning, dabAnimation);
        }

        // Show join hologram to every player if hidden and update.
        joinHologram.setVisibleByDefault(true);
        if (plugin.isEnabled()) {
            Predicate<Player> showAgain = Predicates.not(joinHologram::isVisibleTo).and(Predicates.not(this::isSittingOn));
            Bukkit.getOnlinePlayers().stream()
                    .filter(showAgain)
                    .forEach(joinHologram::showTo);
            updateJoinHologram(false);
        }

        // If the spin hologram has any lines, destroy it.
        if (spinHologram.size() > 0) {
            spinHologram.destroy();
        }

        // Show holograms for the players that are left in the table.
        for (Player player : getPlayers()) {
            spinHologram.showTo(player);
        }
    }

    private boolean hasBetsInPrison(Player player) {
        return getBets(player).stream().anyMatch(bet -> betAppliesForPrison(bet, true));
    }

    public boolean betAppliesForPrison(Bet bet, boolean ignorePrevious) {
        return isRuleEnabled(GameRule.EN_PRISON)
                && bet.hasSlot()
                && bet.getSlot().applyForRules()
                && !bet.isWon()
                && (ignorePrevious || !bet.isEnPrison())
                && winner.isZero();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isSlotAvailable(Player player) {
        for (Slot slot : Slot.values(this)) {
            if (alreadySelected(player, slot)) continue;
            return true;
        }
        return false;
    }

    public boolean alreadySelected(Player player, Slot slot) {
        // Outside have some limitations; you can't make a bet in (RED/BLACK) at the same time,
        // the same goes for (EVEN/ODD), (LOW/HIGH), all COLUMN and all DOZENS.
        // Also, you can't make more than 1 bet on the same slot.
        SlotType conflictType = SlotType.hasConflict(this, player, slot);
        if (conflictType != null) {
            return true;
        }

        // You can't make more than 1 bet on the same slot.
        if (getBets(player).stream()
                .anyMatch(bet -> slot == bet.getSlot())) {
            // If the player already selected this slot (another bet),
            // then we don't want the player to select this again.
            return true;
        }

        // All bet slots have a maximum of bets allowed at the same time.
        int count = 0;
        for (Player playing : getPlayers()) {
            count += (int) getBets(playing).stream().
                    filter(bet -> bet.getSlot() == slot)
                    .count();
        }
        return count == slot.getMaxBets(type.isEuropean());
    }

    public void remove() {
        if (plugin.isEnabled()) {
            getPlayers().forEach(player -> plugin.getMessageManager().send(player, MessageManager.Message.GAME_STOPPED));
        }

        // First, restart the game.
        restart(true);

        // Close GUIs related to this game.
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory inventory = player.getOpenInventory().getTopInventory();
            if (!(inventory.getHolder() instanceof RouletteGUI gui)) continue;
            if (equals(gui.getGame())) player.closeInventory();
        }

        // Remove model.
        model.kill();

        chairs.values().forEach(Entity::remove);
        chairs.clear();

        // Remove join hologram.
        joinHologram.destroy();

        // Remove spin hologram.
        spinHologram.destroy();

        // Play parrot death sound.
        playParrotDeathSound();

        // Remove croupier.
        plugin.getNpcPool().removeNPC(npc.getEntityId());
    }

    private void playParrotDeathSound() {
        if (!parrotEnabled || !parrotSounds) return;

        Location npcLocation = npc.getLocation();
        World world = npcLocation.getWorld();
        if (world != null) world.playSound(npcLocation, Sound.ENTITY_PARROT_DEATH, 1.0f, 1.0f);
    }

    public UUID getModelId() {
        return model.getModelUniqueId();
    }

    public @Nullable String getNPCName() {
        return npc.getProfile().getName().equalsIgnoreCase("") ? null : npc.getProfile().getName();
    }

    public Location getLocation() {
        return model.getLocation();
    }

    public boolean isRuleEnabled(GameRule rule) {
        return rules.getOrDefault(rule, false);
    }

    public void setLimitPlayers(int minPlayers, int maxPlayers) {
        this.minPlayers = minPlayers < 1 ? 1 : minPlayers > maxPlayers ? Math.min(Math.max(1, maxPlayers), 10) : minPlayers;
        this.maxPlayers = maxPlayers > 10 ? 10 : Math.max(maxPlayers, this.minPlayers);
    }

    public String getGlowColorURL(ChatColor color) {
        return GLOW_COLOR_URL.get(color);
    }

    public void changeGlowColor(Player player, boolean next) {
        playerGlowColor.put(player, PluginUtils.getNextOrPrevious(
                GLOW_COLORS,
                getGlowColor(player).ordinal(),
                next));
    }

    public ChatColor getGlowColor(Player player) {
        return playerGlowColor.getOrDefault(player, ChatColor.WHITE);
    }

    public void removeBet(Player player, int betIndex) {
        if (betIndex < 0) return;

        List<Bet> bets = getBets(player);
        if (bets.isEmpty() || betIndex > bets.size() - 1) return;

        bets.remove(betIndex);
    }

    public int getSelectedBetIndex(Player player) {
        return playerCurrentBet.getOrDefault(player, -1);
    }

    public void addEmptyBetAndSelect(Player player) {
        players.put(player, new Bet(this, player));
        selectLast(player);
    }

    public void selectLast(Player player) {
        playerCurrentBet.put(player, getBets(player).size() - 1);
    }

    public void selectBet(Player player, int betIndex) {
        if (betIndex < 0) return;

        List<Bet> bets = getBets(player);
        if (bets.isEmpty() || betIndex > bets.size() - 1) return;

        playerCurrentBet.put(player, betIndex);
    }

    public @Nullable Bet getSelectedBet(Player player) {
        int index = getSelectedBetIndex(player);
        if (index < 0) return null;

        List<Bet> bets = getBets(player);
        if (bets.isEmpty() || index > bets.size() - 1) return null;

        return bets.get(index);
    }

    public @NotNull List<Bet> getBets(Player player) {
        return (List<Bet>) players.get(player);
    }

    public @NotNull Set<Player> getPlayers() {
        return players.keySet();
    }

    public @NotNull Collection<Bet> getAllBets() {
        return players.values();
    }

    public void removeSleepingPlayers() {
        MessageManager messages = plugin.getMessageManager();

        // Check if the players selected a chip.
        Iterator<Map.Entry<Player, Bet>> iterator = players.entries().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Player, Bet> entry = iterator.next();

            // If somehow player is null (maybe disconnected), continue. This should NEVER happen.
            Player player = entry.getKey();
            if (player == null || !player.isOnline()) continue;

            // If the player didn't select a chip, close inventory and remove from the game.
            // This is only possible if the player never selected his first bet chip.
            Bet bet = entry.getValue();
            if (bet.hasChip()) continue;

            iterator.remove();

            // At this point, the state is SPINNING so the original money won't be returned.
            removePartially(player, bet);

            messages.send(player, MessageManager.Message.OUT_OF_TIME);

            // If the player still has a menu open, we must close it.
            closeOpenMenu(player);
        }
    }

    public void closeOpenMenu(@NotNull Player player) {
        InventoryHolder holder = player.getOpenInventory().getTopInventory().getHolder();
        if (holder instanceof RouletteGUI) player.closeInventory();
    }

    public boolean isDone(Player player) {
        return playersDone.contains(player);
    }

    public void setDone(Player player) {
        playersDone.add(player);
    }

    public void setNotReady(Player player) {
        playersDone.remove(player);
    }

    public boolean isChipDisabled(@NotNull Chip chip) {
        return chipsDisabled.contains(chip.name());
    }

    public void enableChip(@NotNull Chip chip) {
        chipsDisabled.remove(chip.name());
    }

    public void disableChip(@NotNull Chip chip) {
        chipsDisabled.add(chip.name());
    }

    public void sendBets(Player player,
                         MessageManager.Message message,
                         MessageManager.Message hover,
                         UnaryOperator<String> loreReplacer,
                         boolean winOnly) {
        // Send bets message formatted with hover messages.
        sendBetsMessage(player,
                message,
                "%bet%",
                loreReplacer,
                (temp, lines, index) -> sendBets(temp, lines, index, hover, winOnly));
    }

    private void sendBets(Player player, List<String> lore, int indexOf, MessageManager.Message hover, boolean winOnly) {
        List<Bet> bets = winOnly ? getBets(player)
                .stream()
                .filter(Bet::isWon)
                .toList() : getBets(player);

        if (bets.isEmpty()) {
            player.sendMessage(lore.get(indexOf).replace("%bet%", MessageManager.Message.NO_WINNING_BETS.asString()));
            return;
        }

        for (Bet bet : bets) {
            List<String> hoverLines = hover.asList();

            // Build the hover message.
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < hoverLines.size(); i++) {
                builder.append(LORE_REPLACER.apply(this, bet, hoverLines.get(i)));
                if (i != hoverLines.size() - 1) builder.append("\n");
            }

            String text = lore.get(indexOf).replace("%bet%", PluginUtils.getSlotName(bet.getSlot()));
            HoverEvent hoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(builder.toString()));

            BaseComponent[] components = TextComponent.fromLegacyText(text);
            for (BaseComponent component : components) {
                component.setHoverEvent(hoverEvent);
            }

            player.spigot().sendMessage(components);
        }
    }
}