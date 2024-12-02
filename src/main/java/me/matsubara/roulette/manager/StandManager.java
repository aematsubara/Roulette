package me.matsubara.roulette.manager;

import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.animation.DabAnimation;
import me.matsubara.roulette.animation.MoneyAnimation;
import me.matsubara.roulette.file.Config;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.data.Bet;
import me.matsubara.roulette.hologram.Hologram;
import me.matsubara.roulette.model.Model;
import me.matsubara.roulette.model.stand.PacketStand;
import me.matsubara.roulette.model.stand.animator.ArmorStandAnimator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class StandManager implements Listener, Runnable {

    private final RoulettePlugin plugin;

    private static final double BUKKIT_VIEW_DISTANCE = Math.pow(Bukkit.getViewDistance() << 4, 2);

    public StandManager(RoulettePlugin plugin) {
        this.plugin = plugin;
        Server server = this.plugin.getServer();
        server.getPluginManager().registerEvents(this, plugin);
        server.getScheduler().runTaskTimerAsynchronously(plugin, this, 20L, 20L);
    }

    @Override
    public void run() {
        // Here we will handle the visibility of the table to the players in the world.
        // This approach should be much better than doing it in PlayerMoveEvent.
        for (Game game : plugin.getGameManager().getGames()) {
            World world = game.getLocation().getWorld();
            if (world == null) continue;

            for (Player player : world.getPlayers()) {
                handleStandRender(game, player, player.getLocation(), HandleCause.MOVE);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        for (Game game : plugin.getGameManager().getGames()) {
            game.getModel().getOut().remove(uuid);
        }
    }

    @EventHandler
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        handleStandRender(player, player.getLocation());
    }

    @EventHandler
    public void onPlayerChangedWorld(@NotNull PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        handleStandRender(player, player.getLocation());
    }

    public double getRenderDistance() {
        double distance = Config.RENDER_DISTANCE.asDouble();
        return Math.min(distance * distance, BUKKIT_VIEW_DISTANCE);
    }

    public boolean isInRange(@NotNull Location location, @NotNull Location check) {
        return Objects.equals(location.getWorld(), check.getWorld())
                && location.distanceSquared(check) <= getRenderDistance();
    }

    private void handleStandRender(Player player, Location location) {
        for (Game game : plugin.getGameManager().getGames()) {
            handleStandRender(game, player, location, HandleCause.SPAWN);
        }
    }

    public void handleStandRender(@NotNull Game game, @NotNull Player player, Location location, HandleCause cause) {
        Model model = game.getModel();
        Set<UUID> out = model.getOut();
        UUID playerUUID = player.getUniqueId();

        // The table is in another world, there is no need to send packets.
        if (!Objects.equals(player.getWorld(), model.getLocation().getWorld())) {
            out.add(playerUUID);
            return;
        }

        boolean range = isInRange(model.getLocation(), location);
        boolean ignored = out.contains(playerUUID);
        boolean spawn = cause == HandleCause.SPAWN;

        boolean show = range && (ignored || spawn);
        boolean destroy = !range && !ignored;
        if (!show && !destroy) return;

        if (show) {
            out.remove(playerUUID);
        } else {
            out.add(playerUUID);
            if (spawn) return;
        }

        // Spawn the entire model in another thread.
        plugin.getPool().execute(() -> {
            // Show/hide model stands.
            handleStandRender(player, model.getStands(), show);

            // Show/hide holograms stands.
            for (Bet bet : game.getAllBets()) {

                // Show/hide chip stand.
                if (bet.hasStand()) {
                    handleStandRender(player, bet.getStand(), show);
                }

                // Show/hide hologram.
                if (bet.hasHologram() && bet.getHologram().isVisibleTo(player)) {
                    handleStandRender(player, bet.getHologram().getStands(), show);
                }
            }

            // Show/hide join hologram stands.
            Hologram join = game.getJoinHologram();
            handleStandRender(player, join.getStands(), show && join.isVisibleTo(player));

            // Show/hide spin hologram stands (only if the player is playing this game).
            if (game.isPlaying(player)) {
                handleStandRender(player, game.getSpinHologram().getStands(), show);
            }

            // Show/hide selected stands.
            PacketStand selectedOne = game.getSelectedOne();
            PacketStand selectedTwo = game.getSelectedTwo();
            if (selectedOne != null) handleStandRender(player, selectedOne, show);
            if (selectedTwo != null) handleStandRender(player, selectedTwo, show);

            // Show/hide money animation stands.
            MoneyAnimation money = game.getMoneyAnimation();
            if (money != null && money.getSeeing().contains(player)) {
                handleStandRender(player, money.getMoneySlot(), show);
            }

            // Show/hide dab animation stands.
            DabAnimation dab = game.getDabAnimation();
            if (dab == null || !dab.getSeeing().contains(player)) return;

            for (ArmorStandAnimator animator : dab.getAnimators().keySet()) {
                handleStandRender(player, animator.getStand(), show);
            }
        });
    }

    private void handleStandRender(Player player, @NotNull Collection<PacketStand> stands, boolean show) {
        for (PacketStand stand : stands) {
            handleStandRender(player, stand, show);
        }
    }

    private void handleStandRender(Player player, PacketStand stand, boolean show) {
        if (show) {
            stand.spawn(player);
        } else {
            stand.destroy(player);
        }
    }

    public enum HandleCause {
        SPAWN,
        TELEPORT,
        MOVE
    }
}