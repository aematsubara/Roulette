package me.matsubara.roulette.manager;

import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.util.PluginUtils;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class ConfigManager {

    private final RoulettePlugin plugin;

    public ConfigManager(RoulettePlugin plugin) {
        this.plugin = plugin;
    }

    public int getRenderDistance() {
        return plugin.getConfig().getInt("render-distance", 64);
    }

    public @NotNull ItemStack getBall() {
        return new ItemStack(PluginUtils.getOrDefault(Material.class, Config.CROUPIER_BALL.asString(), Material.AIR));
    }

    public long getPeriod() {
        return (long) (((double) Config.RESTART_TIME.asInt() / Config.RESTART_FIREWORKS.asInt()) * 20L);
    }

    public @NotNull String getColumnOrDozen(String type, int index) {
        return PluginUtils.translate(plugin.getConfig().getString("slots." + type + "." + index));
    }

    public enum Config {
        SWAP_CHAIR("swap-chair"),
        INSTANT_EXPLODE("instant-explode"),
        FIX_CHAIR_CAMERA("fix-chair-camera"),
        HIT_ON_GAME("hit-on-game"),
        KEEP_SEAT("keep-seat"),
        DATE_FORMAT("date-format"),
        MOVE_INTERVAL("move-interval"),
        CROUPIER_BALL("croupier-ball"),
        COUNTDOWN_WAITING("countdown.waiting"),
        COUNTDOWN_SELECTING("countdown.selecting.base"),
        COUNTDOWN_SELECTING_EXTRA("countdown.selecting.extra"),
        COUNTDOWN_SELECTING_MAX("countdown.selecting.max"),
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
        TYPE_EUROPEAN("variable-text.types.european"),
        TYPE_AMERICAN("variable-text.types.american"),
        JOIN_HOLOGRAM("join-hologram"),
        SELECT_HOLOGRAM("select-hologram"),
        STATE_ENABLED("variable-text.state.enabled"),
        STATE_DISABLED("variable-text.state.disabled"),
        SESSION_RESULT_MENU_TITLE("session-result-menu.title"),
        SESSIONS_MENU_TITLE("sessions-menu.title"),
        BETS_MENU_TITLE("bets-menu.title"),
        CHIP_MENU_TITLE("chip-menu.title"),
        CONFIRM_MENU_TITLE("confirmation-menu.title"),
        CROUPIER_MENU_TITLE("croupier-menu.title"),
        GAME_CHIP_MENU_TITLE("game-chip-menu.title"),
        GAME_MENU_TITLE("game-menu.title"),
        TABLE_MENU_TITLE("table-menu.title"),
        ONLY_AMERICAN("variable-text.only-american"),
        UNNAMED_CROUPIER("variable-text.unnamed-croupier"),
        CUSTOM_WIN_MULTIPLIER_ENABLED("custom-win-multiplier.enabled"),
        MONEY_ABBREVIATION_FORMAT_ENABLED("money-abbreviation-format.enabled"),
        DAB_ANIMATION_ENABLED("dab-animation.enabled"),
        DAB_ANIMATION_AMOUNT("dab-animation.settings.amount"),
        DAB_ANIMATION_RADIUS("dab-animation.settings.radius"),
        DAB_ANIMATION_RAINBOW_EFFECT_SPEED("dab-animation.rainbow-effect.speed"),
        DAB_ANIMATION_RAINBOW_EFFECT_GLOWING("dab-animation.rainbow-effect.glowing"),
        NPC_LOOK_AND_INVITE_ENABLED("npc-look-and-invite.enabled"),
        NPC_LOOK_AND_INVITE_RANGE("npc-look-and-invite.range");

        private final RoulettePlugin plugin = JavaPlugin.getPlugin(RoulettePlugin.class);
        private final String path;

        Config(String path) {
            this.path = path;
        }

        public String asStringRaw() {
            return plugin.getConfig().getString(path);
        }

        public @NotNull String asString() {
            return PluginUtils.translate(asStringRaw());
        }

        public @NotNull List<String> asListRaw() {
            return plugin.getConfig().getStringList(path);
        }

        public @NotNull List<String> asList() {
            return PluginUtils.translate(asListRaw());
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

        public double asDouble() {
            return plugin.getConfig().getDouble(path);
        }
    }
}