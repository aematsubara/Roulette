package me.matsubara.roulette.listener.protocol;

import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSteerVehicle;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.GameRule;
import me.matsubara.roulette.game.GameState;
import me.matsubara.roulette.game.data.Bet;
import me.matsubara.roulette.gui.BetsGUI;
import me.matsubara.roulette.gui.ConfirmGUI;
import me.matsubara.roulette.manager.ConfigManager;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SteerVehicle extends SimplePacketListenerAbstract {

    private final RoulettePlugin plugin;
    private final Map<UUID, Long> steerCooldown = new HashMap<>();

    public SteerVehicle(RoulettePlugin plugin) {
        super(PacketListenerPriority.HIGHEST);
        this.plugin = plugin;
    }

    @Override
    public void onPacketPlayReceive(@NotNull PacketPlayReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.STEER_VEHICLE) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        WrapperPlayClientSteerVehicle wrapper = new WrapperPlayClientSteerVehicle(event);

        // Get required values from the packet.
        float forward = wrapper.getForward();
        float sideways = wrapper.getSideways();
        boolean jump = wrapper.isJump(), dismount = wrapper.isUnmount();

        // Nothing changed?
        if (forward == 0.0f && sideways == 0.0f && !jump && !dismount) return;

        // Let player dismount if is a passenger of a packet stand.
        models:
        for (Game game : plugin.getGameManager().getGames()) {
            for (ArmorStand stand : game.getChairs().values()) {
                if (handle(player, stand, game, forward, sideways, jump, dismount, event)) break models;
            }
        }
    }

    private boolean handle(Player player, @NotNull ArmorStand stand, @NotNull Game game, float forward, float sideways, boolean jump, boolean dismount, PacketPlayReceiveEvent event) {
        if (!stand.getPassengers().contains(player)) return false;

        // Prevent player dismounting.
        if (dismount) event.setCancelled(true);

        // If the player is in cooldown, return.
        if (steerCooldown.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis()) return true;

        List<Bet> bets = game.getBets(player);
        if (bets.isEmpty()) return true;

        GameState state = game.getState();
        boolean prison = bets.stream().anyMatch(Bet::isEnPrison);

        // Move the current bet.
        Bet bet = game.getSelectedBet(player);
        if (bet == null) return true;

        // Handle chip movement (up/down/left/right).
        if ((forward != 0.0f || sideways != 0.0f) && canMoveChip(player, game, bet)) {
            game.moveDirectionalChip(player, forward, sideways);
        }

        // Handle chair movement (left/right).
        if (sideways != 0.0f && canSwapChair(player, game, prison)) {
            runTask(() -> game.sitPlayer(player, sideways < 0.0f));
        }

        // Handle shift.
        if (dismount) {
            // Open leave confirms gui to the player sync.
            runTask(() -> new ConfirmGUI(game, player, ConfirmGUI.ConfirmType.LEAVE));
        }

        // Handle jump.
        if (jump
                && state.isSelecting()
                && !game.isDone(player)
                && !bets.isEmpty()
                && (bets.size() > 1 || bets.get(0).hasChip())) {
            // Only allow opening the bet GUI if the player doesn't have any bet in prison
            // and already has a bet with a chip.
            runTask(() -> new BetsGUI(game, player));
        }

        // Put player in cooldown.
        long interval = Math.max(200L, ConfigManager.Config.MOVE_INTERVAL.asLong());
        steerCooldown.put(player.getUniqueId(), System.currentTimeMillis() + interval);

        return true;
    }

    private void runTask(Runnable runnable) {
        plugin.getServer().getScheduler().runTask(plugin, runnable);
    }

    private boolean canMoveChip(Player player, @NotNull Game game, Bet bet) {
        return game.getState().isSelecting() && !game.isDone(player) && (!game.isRuleEnabled(GameRule.EN_PRISON) || !bet.isEnPrison());
    }

    private boolean canSwapChair(Player player, @NotNull Game game, boolean prison) {
        return hasSwapChairPermission(player) && (!game.getState().isSelecting() || game.isDone(player) || prison);
    }

    private boolean hasSwapChairPermission(Player player) {
        return ConfigManager.Config.SWAP_CHAIR.asBool() || player.hasPermission("roulette.swapchair");
    }
}