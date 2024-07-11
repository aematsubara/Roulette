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
import me.matsubara.roulette.gui.ConfirmGUI;
import me.matsubara.roulette.manager.ConfigManager;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
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
        float sideways = wrapper.getSideways();
        boolean jump = wrapper.isJump(), dismount = wrapper.isUnmount();

        // At the moment, we only care about left/right, space and shift keys.
        if (sideways == 0.0f && !jump && !dismount) return;

        // Let player dismount if is a passenger of a packet stand.
        models:
        for (Game game : plugin.getGameManager().getGames()) {
            for (ArmorStand stand : game.getChairs().values()) {
                if (handle(player, stand, game, sideways, jump, dismount, event)) break models;
            }
        }
    }

    private boolean handle(Player player, @NotNull ArmorStand stand, @NotNull Game game, float sideways, boolean jump, boolean dismount, PacketPlayReceiveEvent event) {
        if (!stand.getPassengers().contains(player)) return false;

        // Prevent player dismounting.
        if (dismount) event.setCancelled(true);

        // If the player is in cooldown, return.
        if (steerCooldown.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis()) return true;

        GameState state = game.getState();

        // If moving left/right and the game is already started, return.
        if (sideways != 0.0f && !state.isIdle() && !state.isStarting() && !state.isSelecting()) {
            return true;
        }

        Bet bet = game.getPlayers().get(player);

        // Left.
        if (sideways > 0.0f) {
            if ((state.isIdle() || state.isStarting()) && canSwapChair(player, game)) {
                runTask(() -> game.sitPlayer(player, false));
            } else {
                // Only allow move chip if the bet isn't in prison.
                if (!game.isRuleEnabled(GameRule.EN_PRISON) || !bet.isEnPrison()) {
                    game.moveChip(player, false);
                }
            }
        }

        // Right.
        if (sideways < 0.0f) {
            if ((state.isIdle() || state.isStarting()) && canSwapChair(player, game)) {
                runTask(() -> game.sitPlayer(player, true));
            } else {
                // Only allow move chip if the bet isn't in prison.
                if (!game.isRuleEnabled(GameRule.EN_PRISON) || !bet.isEnPrison()) {
                    game.moveChip(player, true);
                }
            }
        }

        // Shift.
        if (dismount) {
            if (state.isEnding() && !game.isRuleEnabled(GameRule.EN_PRISON)) {
                // Let player unmount.
                if (player.getVehicle() instanceof ArmorStand chair && game.getChairs().containsValue(chair)) {
                    runTask(player::leaveVehicle);
                }
            } else {
                // Open leave confirms gui to the player sync.
                runTask(() -> new ConfirmGUI(game, player, ConfirmGUI.ConfirmType.LEAVE));
            }
        }

        // Jump.
        if (jump && state.isSelecting()) {
            bet.nextGlowColor();
            bet.updateStandGlow(player);
        }

        // Put player in cooldown.
        long interval = Math.max(200L, ConfigManager.Config.MOVE_INTERVAL.asLong());
        steerCooldown.put(player.getUniqueId(), System.currentTimeMillis() + interval);

        return true;
    }

    private void runTask(Runnable runnable) {
        plugin.getServer().getScheduler().runTask(plugin, runnable);
    }

    private boolean canSwapChair(Player player, @NotNull Game game) {
        return (game.getState().isIdle() || game.getState().isStarting()) && (ConfigManager.Config.SWAP_CHAIR.asBool() || player.hasPermission("roulette.swapchair"));
    }
}