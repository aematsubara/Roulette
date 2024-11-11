package me.matsubara.roulette.manager;

import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.animation.DabAnimation;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.data.Bet;
import me.matsubara.roulette.hologram.Hologram;
import me.matsubara.roulette.model.Model;
import me.matsubara.roulette.model.stand.PacketStand;
import me.matsubara.roulette.model.stand.animator.ArmorStandAnimator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class StandManager implements Listener {

    private final RoulettePlugin plugin;

    private static final double BUKKIT_VIEW_DISTANCE = Math.pow(Bukkit.getViewDistance() << 4, 2);

    public StandManager(RoulettePlugin plugin) {
        this.plugin = plugin;
        this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        handleStandRender(player, player.getLocation(), HandleCause.SPAWN);
    }

    @EventHandler
    public void onPlayerTeleport(@NotNull PlayerTeleportEvent event) {
        handleMovementEvent(event, HandleCause.TELEPORT);
    }

    @EventHandler
    public void onPlayerMove(@NotNull PlayerMoveEvent event) {
        handleMovementEvent(event, HandleCause.OTHER);
    }

    private void handleMovementEvent(@NotNull PlayerMoveEvent event, HandleCause cause) {
        Location to = event.getTo();
        if (to == null) return;

        // Only handle renders if the player moved at least 1 block.
        Location from = event.getFrom();
        if (to.getBlockX() == from.getBlockX()
                && to.getBlockY() == from.getBlockY()
                && to.getBlockZ() == from.getBlockZ()) return;

        Player player = event.getPlayer();
        handleStandRender(player, to, cause);
    }

    @EventHandler
    public void onPlayerChangedWorld(@NotNull PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        handleStandRender(player, player.getLocation(), HandleCause.SPAWN);
    }

    public double getRenderDistance() {
        double distance = ConfigManager.Config.RENDER_DISTANCE.asDouble();
        return Math.min(distance * distance, BUKKIT_VIEW_DISTANCE);
    }

    public boolean isInRange(@NotNull Location location, @NotNull Location check) {
        return Objects.equals(location.getWorld(), check.getWorld())
                && location.distanceSquared(check) <= getRenderDistance();
    }

    private void handleStandRender(Player player, Location location, HandleCause cause) {
        for (Game game : plugin.getGameManager().getGames()) {
            Model model = game.getModel();

            Set<UUID> outOfRange = model.getOutOfRange();

            boolean ignored = outOfRange.contains(player.getUniqueId());
            boolean show = isInRange(model.getLocation(), location);
            boolean spawn = cause == HandleCause.SPAWN || (cause == HandleCause.TELEPORT && !show);

            if (show && (ignored || spawn)) {
                outOfRange.remove(player.getUniqueId());
            } else if (!show) {
                outOfRange.add(player.getUniqueId());
            }

            // Show/hide model stands.
            handleStandRender(player, model.getStands(), show, spawn, ignored);

            // Show/hide holograms stands.
            for (Bet bet : game.getAllBets()) {

                // Show/hide chip stand.
                if (bet.hasStand()) {
                    handleStandRender(player, bet.getStand(), show, spawn, ignored);
                }

                // Show/hide hologram.
                if (bet.hasHologram() && bet.getHologram().isVisibleTo(player)) {
                    handleStandRender(player, bet.getHologram().getStands(), show, spawn, ignored);
                }
            }

            // Show/hide join hologram stands.
            Hologram join = game.getJoinHologram();
            handleStandRender(player, join.getStands(), show && join.isVisibleTo(player), spawn, ignored);

            // Show/hide spin hologram stands (only if the player is playing this game).
            if (game.isPlaying(player)) {
                handleStandRender(player, game.getSpinHologram().getStands(), show, spawn, ignored);
            }

            // Show/hide dab animation stands.
            DabAnimation animation = game.getDabAnimation();
            if (animation == null) continue;

            for (ArmorStandAnimator animator : animation.getAnimators().keySet()) {
                handleStandRender(player, animator.getStand(), show, spawn, ignored);
            }
        }
    }

    private void handleStandRender(Player player, @NotNull Collection<PacketStand> stands, boolean show, boolean spawn, boolean ignored) {
        for (PacketStand stand : stands) {
            handleStandRender(player, stand, show, spawn, ignored);
        }
    }

    private void handleStandRender(Player player, PacketStand stand, boolean show, boolean spawn, boolean ignored) {
        if (show && (ignored || spawn)) {
            stand.spawn(player, true);
        } else if (!show) {
            stand.destroy(player);
        }
    }

    public enum HandleCause {
        SPAWN,
        TELEPORT,
        OTHER
    }
}