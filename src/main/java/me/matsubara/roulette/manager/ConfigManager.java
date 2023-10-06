package me.matsubara.roulette.manager;

import com.cryptomorin.xseries.XMaterial;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.util.ItemBuilder;
import me.matsubara.roulette.util.PluginUtils;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class ConfigManager {

    private final RoulettePlugin plugin;

    public ConfigManager(RoulettePlugin plugin) {
        this.plugin = plugin;
    }

    public int getRenderDistance() {
        return plugin.getConfig().getInt("render-distance", 64);
    }

    public ItemStack getBall() {
        return XMaterial.matchXMaterial(Config.CROUPIER_BALL.asString()).map(XMaterial::parseItem).orElse(null);
    }

    public long getPeriod() {
        return (long) (((double) Config.RESTART_TIME.asInt() / Config.RESTART_FIREWORKS.asInt()) * 20L);
    }

    public String getColumnOrDozen(String type, int index) {
        return PluginUtils.translate(plugin.getConfig().getString("slots." + type + "." + index));
    }

    public @NotNull String getAccountDisplayName(String name) {
        return getDisplayName("game-menu", "account").replace("%player%", name);
    }

    public @NotNull List<String> getAccountLore() {
        return getLore("game-menu", "account");
    }

    public @NotNull String getStartTimeDisplayName(int time) {
        return getDisplayName("game-menu", "start-time").replace("%seconds%", String.valueOf(time));
    }

    public @NotNull String getChipDisplayName(double price) {
        return getDisplayName("shop", "chip").replace("%money%", PluginUtils.format(price));
    }

    public @NotNull List<String> getChipLore() {
        return getLore("shop", "chip");
    }

    public @Nullable ItemStack getItem(String gui, String type, @Nullable String money) {
        Material material = XMaterial.matchXMaterial(getMaterial(gui, type)).map(XMaterial::parseMaterial).orElse(null);
        if (material == null) return null;

        String displayName = (money != null) ? getDisplayName(gui, type).replace("%money%", money) : getDisplayName(gui, type);

        ItemBuilder builder;
        if (isSkull(gui, type, material)) {
            builder = new ItemBuilder(getUrl(gui, type), true).setDisplayName(displayName).setLore(getLore(gui, type));
        } else {
            builder = new ItemBuilder(material).setDisplayName(displayName).setLore(getLore(gui, type));
        }
        if (hasAttributes(gui, type)) builder.addItemFlags(getAttributes(gui, type));
        return builder.build();
    }

    private boolean isSkull(String gui, String type, Material material) {
        return plugin.getConfig().get(gui + "." + type + ".url") != null && material == XMaterial.PLAYER_HEAD.parseMaterial();
    }

    private boolean hasAttributes(String gui, String path) {
        return plugin.getConfig().contains(gui + "." + path + ".attributes", false);
    }

    private ItemFlag @NotNull [] getAttributes(String gui, String path) {
        return plugin.getConfig().getStringList(gui + "." + path + ".attributes").stream().map(this::getAttribute).toArray(ItemFlag[]::new);
    }

    private @Nullable ItemFlag getAttribute(String attribute) {
        try {
            return ItemFlag.valueOf(attribute);
        } catch (IllegalArgumentException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    public boolean hasUrl(String gui, String path) {
        return plugin.getConfig().contains(gui + "." + path + ".url", false);
    }

    public String getUrl(String gui, String path) {
        return plugin.getConfig().getString(gui + "." + path + ".url");
    }

    private String getMaterial(String gui, String path) {
        return plugin.getConfig().getString(gui + "." + path + ".material");
    }

    public String getDisplayName(String gui, String path) {
        return PluginUtils.translate(plugin.getConfig().getString(gui + "." + path + ".display-name"));
    }

    public @NotNull List<String> getLore(String gui, String path) {
        return PluginUtils.translate(plugin.getConfig().getStringList(gui + "." + path + ".lore"));
    }

    public enum Config {
        SWAP_CHAIR("swap-chair"),
        INSTANT_EXPLODE("instant-explode"),
        FIX_CHAIR_CAMERA("fix-chair-camera"),
        HIT_ON_GAME("hit-on-game"),
        KEEP_SEAT("keep-seat"),
        LEAVE_CONFIRM("leave-confirm"),
        MOVE_INTERVAL("move-interval"),
        CROUPIER_BALL("croupier-ball.material"),
        CROUPIER_BALL_SPEED("croupier-ball.speed"),
        COUNTDOWN_WAITING("countdown.waiting"),
        COUNTDOWN_SELECTING("countdown.selecting"),
        COUNTDOWN_SORTING("countdown.sorting"),
        RESTART_TIME("restart.time"),
        RESTART_FIREWORKS("restart.fireworks"),
        SOUND_CLICK("sounds.click"),
        SOUND_COUNTDOWN("sounds.countdown"),
        SOUND_SPINNING("sounds.spinning"),
        SOUND_SWAP_CHAIR("sounds.swap-chair"),
        SOUND_SELECT("sounds.select"),
        DISABLED_SLOTS("disabled-slots"),
        MAP_IMAGE_ENABLED("map-image.enabled"),
        MAP_IMAGE_DATE_FORMAT("map-image.date-format"),
        MAP_IMAGE_TEXT("map-image.text"),
        MAP_IMAGE_ITEM_DISPLAY_NAME("map-image.item.display-name"),
        MAP_IMAGE_ITEM_LORE("map-image.item.lore"),
        CANCEL_WORD("cancel-word"),
        SPINNING("spin-holograms.spinning"),
        WINNING_NUMBER("spin-holograms.winning-number"),
        SINGLE_ZERO("slots.single.zero"),
        SINGLE_RED("slots.single.red"),
        SINGLE_BLACK("slots.single.black"),
        LOW("slots.other.low"),
        HIGH("slots.other.high"),
        EVEN("slots.other.even"),
        ODD("slots.other.odd"),
        RED("slots.other.red"),
        BLACK("slots.other.black"),
        TYPE_EUROPEAN("types.european"),
        TYPE_AMERICAN("types.american"),
        CONFIRM_GUI_TITLE("confirmation-gui.title"),
        CONFIRM_GUI_CONFIRM("confirmation-gui.confirm"),
        CONFIRM_GUI_CANCEL("confirmation-gui.cancel"),
        JOIN_HOLOGRAM("join-hologram"),
        SELECT_HOLOGRAM("select-hologram"),
        NOT_ENOUGH_MONEY_MATERIAL("not-enough-money.material"),
        NOT_ENOUGH_MONEY_DISPLAY_NAME("not-enough-money.display-name"),
        NOT_ENOUGH_MONEY_LORE("not-enough-money.lore"),
        STATE_ENABLED("state.enabled"),
        STATE_DISABLED("state.disabled"),
        SHOP_TITLE("shop.title"),
        GAME_MENU_TITLE("game-menu.title"),
        ONLY_AMERICAN("only-american"),
        UNNAMED_CROUPIER("unnamed-croupier"),
        CUSTOM_WIN_MULTIPLIER_ENABLED("custom-win-multiplier.enabled"),
        MONEY_ABBREVIATION_FORMAT_ENABLED("money-abbreviation-format.enabled");

        private final RoulettePlugin plugin = JavaPlugin.getPlugin(RoulettePlugin.class);
        private final String path;

        Config(String path) {
            this.path = path;
        }

        public String asString() {
            return PluginUtils.translate(plugin.getConfig().getString(path));
        }

        public @NotNull List<String> asList() {
            return PluginUtils.translate(plugin.getConfig().getStringList(path));
        }

        public boolean asBool() {
            return plugin.getConfig().getBoolean(path);
        }

        public int asInt() {
            return plugin.getConfig().getInt(path);
        }

        public long asLong() {
            return plugin.getConfig().getLong(path);
        }
    }
}