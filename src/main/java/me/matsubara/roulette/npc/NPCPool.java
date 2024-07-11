package me.matsubara.roulette.npc;

import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.util.ParrotUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class NPCPool implements Listener {

    private final RoulettePlugin plugin;
    private final Map<Integer, NPC> npcMap = new ConcurrentHashMap<>();
    private final int spawnDistance;

    private static final double BUKKIT_VIEW_DISTANCE = Math.pow(Bukkit.getViewDistance() << 4, 2);

    public NPCPool(RoulettePlugin plugin) {
        this.plugin = plugin;
        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
        int renderDistance = plugin.getConfigManager().getRenderDistance();
        this.spawnDistance = renderDistance * renderDistance;
        tick();
    }

    protected void tick() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (NPC npc : npcMap.values()) {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                boolean playParrotSound = random.nextInt(5) == 0;

                for (Player player : Bukkit.getOnlinePlayers()) {
                    Location npcLocation = npc.getLocation();
                    Location playerLocation = player.getLocation();

                    World npcWorld = npcLocation.getWorld();
                    if (npcWorld == null) continue;

                    if (!npcWorld.equals(playerLocation.getWorld())
                            || !npcWorld.isChunkLoaded(npcLocation.getBlockX() >> 4, npcLocation.getBlockZ() >> 4)) {
                        // Hide NPC if the NPC isn't in the same world of the player or the NPC isn't on a loaded chunk.
                        if (npc.isShownFor(player)) npc.hide(player);
                        continue;
                    }

                    boolean inRange = npcLocation.distanceSquared(playerLocation) <= Math.min(spawnDistance, BUKKIT_VIEW_DISTANCE);

                    if (!inRange && npc.isShownFor(player)) {
                        npc.hide(player);
                    } else if (inRange && !npc.isShownFor(player)) {
                        npc.show(player, plugin);
                    }

                    if (playParrotSound && inRange && npc.isShownFor(player)) {
                        if (!ParrotUtils.imitateNearbyMobs(npcLocation)) {
                            npcWorld.playSound(npcLocation,
                                    ParrotUtils.getAmbient(npcWorld),
                                    SoundCategory.PLAYERS,
                                    1.0f,
                                    ParrotUtils.getPitch(random));
                        }
                    }
                }
            }
        }, 30L, 30L);
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