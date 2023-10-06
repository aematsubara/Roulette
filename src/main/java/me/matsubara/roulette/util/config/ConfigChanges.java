package me.matsubara.roulette.util.config;

import com.google.common.collect.ImmutableList;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

@SuppressWarnings("unused")
public record ConfigChanges(Predicate<FileConfiguration> predicate,
                            Consumer<FileConfiguration> consumer,
                            int newVersion) {

    public static @NotNull Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final List<ConfigChanges> changes = new ArrayList<>();

        public Builder addChange(Predicate<FileConfiguration> predicate,
                                 Consumer<FileConfiguration> consumer,
                                 int newVersion) {
            changes.add(new ConfigChanges(predicate, consumer, newVersion));
            return this;
        }

        public List<ConfigChanges> build() {
            return ImmutableList.copyOf(changes);
        }
    }
}