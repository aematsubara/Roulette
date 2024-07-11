package me.matsubara.roulette.npc;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public interface SpawnCustomizer {

    void handleSpawn(@NotNull NPC npc, @NotNull Player player);
}