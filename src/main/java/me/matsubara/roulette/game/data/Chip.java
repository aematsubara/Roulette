package me.matsubara.roulette.game.data;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public record Chip(String name, @Nullable String displayName, @Nullable List<String> lore, String url, double price) {

    public Chip(String name, String url, double price) {
        this(name, null, null, url, price);
    }
}