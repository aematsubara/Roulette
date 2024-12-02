package me.matsubara.roulette.npc;

import lombok.Getter;
import me.matsubara.roulette.RoulettePlugin;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class NPCPool implements Listener {

    private final RoulettePlugin plugin;
    private final Map<Integer, NPC> npcMap = new ConcurrentHashMap<>();

    public NPCPool(RoulettePlugin plugin) {
        this.plugin = plugin;
        Server server = this.plugin.getServer();
        server.getPluginManager().registerEvents(this, plugin);
        server.getScheduler().runTaskTimerAsynchronously(plugin, new NPCTick(this), 1L, 1L);
    }

    protected void takeCareOf(NPC npc) {
        npcMap.put(npc.getEntityId(), npc);
    }

    public Optional<NPC> getNPC(int entityId) {
        return Optional.ofNullable(npcMap.get(entityId));
    }

    public void removeNPC(int entityId) {
        getNPC(entityId).ifPresent(npc -> {
            npcMap.remove(entityId);
            npc.getSeeingPlayers().forEach(npc::hide);
        });
    }

    @EventHandler
    public void handleRespawn(@NotNull PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        npcMap.values().stream()
                .filter(npc -> npc.isShownFor(player))
                .forEach(npc -> npc.hide(player));
    }

    @EventHandler
    public void handleQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();

        npcMap.values().stream()
                .filter(npc -> npc.isShownFor(player))
                .forEach(npc -> npc.removeSeeingPlayer(player));
    }
}