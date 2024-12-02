package me.matsubara.roulette.hook;

import me.matsubara.roulette.RoulettePlugin;

public interface RExtension<T> {

    T init(RoulettePlugin plugin);

    default void onEnable(@SuppressWarnings("unused") RoulettePlugin plugin) {
        // This method should be used to register event listeners.
    }
}