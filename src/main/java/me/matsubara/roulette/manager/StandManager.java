package me.matsubara.roulette.manager;

import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.data.Bet;
import me.matsubara.roulette.stand.PacketStand;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Collection;

public final class StandManager implements Listener {

    private final RoulettePlugin plugin;

    public StandManager(RoulettePlugin plugin) {
        this.plugin = plugin;
        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        handleStandRender(player, player.getLocation(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;

        Player player = event.getPlayer();
        handleStandRender(player, event.getTo(), false);
    }

    @SuppressWarnings("ConstantConditions")
    private void handleStandRender(Player player, Location location, boolean isSpawn) {
        for (Game game : plugin.getGameManager().getGames()) {
            if (!game.getModel().isModelSpawned()) continue;

            // If the player isn't in the same world of this game, continue.
            if (!game.getModel().getLocation().getWorld().equals(player.getWorld())) continue;

            boolean shouldShow = true;

            for (PacketStand stand : game.getModel().getStands().values()) {
                if (!stand.isInRange(location)) shouldShow = false;
            }

            // Show/hide model stands.
            handleStandRender(player, game.getModel().getStands().values(), shouldShow, isSpawn);

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

    private void handleStandRender(Player player, Collection<PacketStand> stands, boolean shouldShow, boolean isSpawn) {
        for (PacketStand stand : stands) {
            handleStandRender(player, stand, shouldShow, isSpawn);
        }
    }

    private void handleStandRender(Player player, PacketStand stand, boolean shouldShow, boolean isSpawn) {
        if (shouldShow) {
            if (stand.isIgnored(player) || isSpawn) stand.spawn(player);
        } else {
            if (!stand.isIgnored(player)) stand.destroy(player);
        }
    }
}