package me.matsubara.roulette.npc;

import com.cryptomorin.xseries.XSound;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.file.Config;
import me.matsubara.roulette.file.Messages;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.GameState;
import me.matsubara.roulette.npc.modifier.RotationModifier;
import me.matsubara.roulette.util.ParrotUtils;
import me.matsubara.roulette.util.PluginUtils;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class NPCTick implements Runnable {

    private final NPCPool pool;
    private final RoulettePlugin plugin;

    private final boolean lookAndInviteEnabled = Config.NPC_LOOK_AND_INVITE_ENABLED.asBool();
    private final double lookAndInviteRange = Math.pow(Config.NPC_LOOK_AND_INVITE_RANGE.asDouble(), 2);

    private static final float CROUPIER_FOV = 85.0f;

    public NPCTick(@NotNull NPCPool pool) {
        this.pool = pool;
        this.plugin = pool.getPlugin();
    }

    @Override
    public void run() {
        for (NPC npc : pool.getNpcMap().values()) {
            Game game = npc.getGame();

            Location npcLocation = npc.getLocation();

            World world = npcLocation.getWorld();
            if (world == null) continue;

            for (Player player : world.getPlayers()) {
                Location playerLocation = player.getLocation();

                if (!world.equals(playerLocation.getWorld())
                        || !world.isChunkLoaded(npcLocation.getBlockX() >> 4, npcLocation.getBlockZ() >> 4)) {
                    // Hide NPC if the NPC isn't in the same world of the player or the NPC isn't on a loaded chunk.
                    if (npc.isShownFor(player)) npc.hide(player);
                    continue;
                }

                boolean inRange = plugin.getStandManager().isInRange(npcLocation, playerLocation);

                if (!inRange && npc.isShownFor(player)) {
                    npc.hide(player);
                } else if (inRange && !npc.isShownFor(player)) {
                    npc.show(player, plugin);
                }

                if (!handleFOV(npc, player)) {
                    npc.removeFOV(player);
                }
            }

            boolean playParrotSound = game.isParrotEnabled()
                    && game.isParrotSounds()
                    && PluginUtils.RANDOM.nextInt(100) == 0;

            if (!playParrotSound) continue;

            Set<Player> seeing = npc.getSeeingPlayers();
            if (seeing.isEmpty()) continue;

            XSound sound = ParrotUtils.getAmbient(world);
            Location at = npcLocation.clone().add(0.0d, 0.75d, 0.0d);

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

    private boolean handleFOV(@NotNull NPC npc, Player player) {
        if (!lookAndInviteEnabled) return false;
        if (!npc.isShownFor(player)) return false;

        Game game = npc.getGame();

        // Only rotate when the game isn't started.
        GameState state = game.getState();
        if (!state.isIdle() && !state.isStarting()) return false;

        // This player is playing a game.
        if (plugin.getGameManager().isPlaying(player)) return false;

        // Only look at the nearest player if the player is in front of the NPC.
        Location target = player.getLocation();
        RotationModifier rotation = npc.rotation().queueLookAt(target);

        // Laziest way to get the yaw from the packet...
        PacketWrapper<? extends PacketWrapper<?>> packet = rotation.getPacketContainers().get(0).provide(npc, player);
        if (!(packet instanceof WrapperPlayServerEntityHeadLook look)) return false;

        float npcYaw = npc.getLocation().getYaw();
        float angleDifference = Math.abs((look.getHeadYaw() - npcYaw + 540) % 360 - 180);

        // Player is out of the FOV of the croupier.
        if (angleDifference > CROUPIER_FOV) return false;

        // The player must be around X blocks from the table (not the NPC).
        double renderDistance = Math.min(lookAndInviteRange, plugin.getStandManager().getRenderDistance());

        if (game.getLocation().distanceSquared(target) > renderDistance) return false;

        // Send an invitation message to the player the first time they enter the FOV.
        if (game.isInvitePlayers() && !npc.isInsideFOV(player) && notInvitedYet(player)) {
            plugin.getMessages().sendNPCMessage(player, game, Messages.Message.INVITE);
        }

        rotation.send(player);
        npc.setInsideFOV(player);
        return true;
    }

    public boolean notInvitedYet(Player player) {
        for (Game game : plugin.getGameManager().getGames()) {
            if (game.getNpc().isInsideFOV(player)) return false;
        }
        return true;
    }
}