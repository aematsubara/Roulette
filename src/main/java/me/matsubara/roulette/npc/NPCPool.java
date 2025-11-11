package me.matsubara.roulette.npc;

import com.cryptomorin.xseries.XSound;
import lombok.Getter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.file.Messages;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.GameState;
import me.matsubara.roulette.util.ParrotUtils;
import me.matsubara.roulette.util.PluginUtils;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class NPCPool implements Listener, Runnable {

    private final RoulettePlugin plugin;
    private final Map<Integer, NPC> npcMap = new ConcurrentHashMap<>();

    private static final float FOV_YAW = 85.0f;
    private static final float FOV_PITCH = 45.0f;

    public NPCPool(RoulettePlugin plugin) {
        this.plugin = plugin;
        Server server = this.plugin.getServer();
        server.getPluginManager().registerEvents(this, plugin);
        server.getScheduler().runTaskTimerAsynchronously(plugin, this, 1L, 1L);
    }

    @Override
    public void run() {
        for (NPC npc : npcMap.values()) {
            Game game = npc.getGame();
            Location location = npc.getLocation();

            World world = location.getWorld();
            if (world == null) continue;

            for (Player player : List.copyOf(world.getPlayers())) {
                handleVisibility(player, player.getLocation(), npc);
            }

            boolean playParrotSound = game.isParrotEnabled()
                    && game.isParrotSounds()
                    && PluginUtils.RANDOM.nextInt(100) == 0;

            if (!playParrotSound) continue;

            Set<Player> seeing = npc.getSeeingPlayers();
            if (seeing.isEmpty()) continue;

            XSound sound = ParrotUtils.getAmbient(world);
            Location at = location.clone().add(0.0d, 0.75d, 0.0d);

            // Play parrot sounds for the players seeing this NPC.
            sound.record()
                    .withVolume(0.7f)
                    .withPitch(ParrotUtils.getPitch())
                    .soundPlayer()
                    .forPlayers(seeing)
                    .atLocation(at)
                    .play();
        }
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

    public void handleVisibility(Player player, Location playerLocation, @NotNull NPC npc) {
        Location npcLocation = npc.getLocation();

        World world = npcLocation.getWorld();
        if (world == null) return;

        if (!world.equals(playerLocation.getWorld())
                || !world.isChunkLoaded(npcLocation.getBlockX() >> 4, npcLocation.getBlockZ() >> 4)) {
            // Hide NPC if the NPC isn't in the same world of the player or the NPC isn't on a loaded chunk.
            if (npc.isShownFor(player)) npc.hide(player);
            return;
        }

        boolean inRange = plugin.getStandManager().isInRange(npcLocation, playerLocation);

        if (!inRange && npc.isShownFor(player)) {
            npc.hide(player);
        } else if (inRange && !npc.isShownFor(player)) {
            npc.show(player, plugin);
        }

        if (!handleFOV(npc, player, playerLocation)) {
            npc.removeFOV(player);
        }
    }

    private boolean handleFOV(@NotNull NPC npc, Player player, Location playerLocation) {
        if (!npc.isShownFor(player)) return false;

        Game game = npc.getGame();

        // Not enabled.
        NPC.NPCAction action = game.getNpcAction();
        if (action == null || action == NPC.NPCAction.NONE) return false;

        // Only rotate when the game isn't started.
        GameState state = game.getState();
        if (!state.isIdle() && !state.isStarting()) return false;

        // This player is playing a game.
        if (plugin.getGameManager().isPlaying(player)) return false;

        Location npcLocation = npc.getLocation();
        float npcYaw = npcLocation.getYaw();

        double xDifference = playerLocation.getX() - npcLocation.getX();
        double yDifference = playerLocation.getY() - npcLocation.getY();
        double zDifference = playerLocation.getZ() - npcLocation.getZ();

        double distance = Math.sqrt(Math.pow(xDifference, 2) + Math.pow(yDifference, 2) + Math.pow(zDifference, 2));

        float yaw = (float) (-Math.atan2(xDifference, zDifference) / Math.PI * 180.0d) % 360;
        float pitch = (float) (-Math.asin(yDifference / distance) / Math.PI * 180.0d);

        float angleDifference = Math.abs((yaw - npcYaw + 540) % 360 - 180);

        // Player is out of the FOV of the croupier.
        boolean fov = game.isNpcActionFOV();
        if (fov && (angleDifference > FOV_YAW || Math.abs(pitch) > FOV_PITCH)) return false;

        // The player must be around X blocks from the table (not the NPC).
        double renderDistance = Math.min(Math.pow(game.getNpcDistance(), 2), plugin.getStandManager().getRenderDistance());

        if (game.getLocation().distanceSquared(playerLocation) > renderDistance) return false;

        // Send an invitation message to the player the first time they enter the FOV.
        if (action.isInvite() && !npc.isInsideFOV(player) && notInvitedYet(player)) {
            plugin.getMessages().sendNPCMessage(player, game, Messages.Message.INVITE);
        }

        if (action.isLook()) {
            npc.rotation().queueBodyRotation(yaw, pitch).send(player);
        }

        npc.setInsideFOV(player);
        return true;
    }

    public boolean notInvitedYet(Player player) {
        for (Game game : plugin.getGameManager().getGames()) {
            if (game.getNpc().isInsideFOV(player)) return false;
        }
        return true;
    }

    @EventHandler
    public void onPlayerTeleport(@NotNull PlayerTeleportEvent event) {
        handleEventVisibility(event);
    }

    @EventHandler
    public void onPlayerChangedWorld(@NotNull PlayerChangedWorldEvent event) {
        handleEventVisibility(event);
    }

    @EventHandler
    public void handleRespawn(@NotNull PlayerRespawnEvent event) {
        handleEventVisibility(event);
    }

    private void handleEventVisibility(@NotNull PlayerEvent event) {
        Player player = event.getPlayer();

        Location location = Objects.requireNonNullElse(
                event instanceof PlayerTeleportEvent teleport ? teleport.getTo() : null,
                player.getLocation());

        for (NPC npc : npcMap.values()) {
            handleVisibility(player, location, npc);
        }
    }

    @EventHandler
    public void handleQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();

        npcMap.values().stream()
                .filter(npc -> npc.isShownFor(player))
                .forEach(npc -> npc.removeSeeingPlayer(player));
    }
}