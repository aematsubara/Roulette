package me.matsubara.roulette.game.data;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Getter
public final class Chip {

    private final String name;
    private final String url;
    private final double price;
    private final String displayName;
    private final List<String> lore;

    public Chip(String name, String url, double price) {
        this(name, null, null, url, price);
    }

    public Chip(String name, @Nullable String displayName, @Nullable List<String> lore, String url, double price) {
        this.name = name;
        this.displayName = displayName;
        this.lore = lore;
        this.url = url;
        this.price = price;
    }
}