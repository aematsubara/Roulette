package me.matsubara.roulette.file.config;

import lombok.Getter;
import lombok.Setter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.util.PluginUtils;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.NumberConversions;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
public class ConfigValue {

    private final RoulettePlugin plugin = JavaPlugin.getPlugin(RoulettePlugin.class);
    private final String path;
    private Object value;

    public static final Set<ConfigValue> ALL_VALUES = new HashSet<>();

    public ConfigValue(@NotNull String path) {
        this.path = path;
        reloadValue();
        ALL_VALUES.add(this);
    }

    public void reloadValue() {
        value = plugin.getConfig().get(path);
    }

    public <T> T getValue(@NotNull Class<T> type) {
        return type.cast(value);
    }

    public <T> T getValue(@NotNull Class<T> type, T defaultValue) {
        return type.cast(value != null ? value : defaultValue);
    }

    public String asString() {
        return getValue(String.class);
    }

    public String asString(String defaultString) {
        return getValue(String.class, defaultString);
    }

    public @NotNull String asStringTranslated() {
        return PluginUtils.translate(asString());
    }

    public boolean asBool() {
        return getValue(Boolean.class);
    }

    public int asInt() {
        return NumberConversions.toInt(value);
    }

    public double asDouble() {
        return NumberConversions.toDouble(value);
    }

    public long asLong() {
        return NumberConversions.toLong(value);
    }
}