package me.matsubara.roulette.manager;

import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.data.Bet;
import me.matsubara.roulette.model.stand.PacketStand;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public final class StandManager implements Listener {

    private final RoulettePlugin plugin;

    public StandManager(RoulettePlugin plugin) {
        this.plugin = plugin;
        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        handleStandRender(player, player.getLocation(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(@NotNull PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) return;

        // Only handle renders if the player moved at least 1 block.
        Location from = event.getFrom();
        if (to.getBlockX() == from.getBlockX()
                && to.getBlockY() == from.getBlockY()
                && to.getBlockZ() == from.getBlockZ()) return;

        Player player = event.getPlayer();
        handleStandRender(player, to, false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChangedWorld(@NotNull PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        handleStandRender(player, player.getLocation(), true);
    }

    private void handleStandRender(Player player, Location location, boolean isSpawn) {
        for (Game game : plugin.getGameManager().getGames()) {
            if (!game.getModel().isModelSpawned()) continue;

            boolean shouldShow = true;

            for (PacketStand stand : game.getModel().getStands()) {
                if (!stand.isInRange(location)) shouldShow = false;
            }

            // Show/hide model stands.
            handleStandRender(player, game.getModel().getStands(), shouldShow, isSpawn);

            // Show/hide holograms stands.
            for (Bet bet : game.getPlayers().values()) {

                // Show/hide chip stand.
                if (bet.hasStand()) {
                    handleStandRender(player, bet.getStand(), shouldShow, isSpawn);
                }

                // Show/hide hologram.
                if (bet.hasHologram() && bet.getHologram().isVisibleTo(player)) {
                    handleStandRender(player, bet.getHologram().getStands(), shouldShow, isSpawn);
                }
            }

            // Show/hide join hologram stands.
            if (game.getJoinHologram().isVisibleTo(player)) {
                handleStandRender(player, game.getJoinHologram().getStands(), shouldShow, isSpawn);
            }

            // Show/hide spin hologram stands (only if the player is playing this game).
            if (game.isPlaying(player)) {
                handleStandRender(player, game.getSpinHologram().getStands(), shouldShow, isSpawn);
            }
        }
    }

    private void handleStandRender(Player player, @NotNull Collection<PacketStand> stands, boolean shouldShow, boolean isSpawn) {
        for (PacketStand stand : stands) {
            handleStandRender(player, stand, shouldShow, isSpawn);
        }
    }

    private void handleStandRender(Player player, PacketStand stand, boolean shouldShow, boolean isSpawn) {
        if (shouldShow) {
            boolean temp = true;
            if ((stand.isIgnored(player) && (temp = stand.getIgnored().get(player.getUniqueId()) != PacketStand.IgnoreReason.HOLOGRAM)) || (isSpawn && temp)) {
                stand.spawn(player);
            }
        } else {
            if (!stand.isIgnored(player)) stand.destroy(player);
        }
    }
}