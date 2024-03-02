package me.matsubara.roulette.listener.protocol;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
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

public final class SteerVehicle extends PacketAdapter {

    private final RoulettePlugin plugin;
    private final Map<UUID, Long> steerCooldown = new HashMap<>();

    public SteerVehicle(RoulettePlugin plugin) {
        super(plugin, ListenerPriority.HIGHEST, PacketType.Play.Client.STEER_VEHICLE);
        this.plugin = plugin;
    }

    @Override
    public void onPacketReceiving(@NotNull PacketEvent event) {
        Player player = event.getPlayer();

        // Get required values from the packet.
        StructureModifier<Float> floats = event.getPacket().getFloat();
        float sideways = floats.readSafely(0);

        StructureModifier<Boolean> booleans = event.getPacket().getBooleans();
        boolean jump = booleans.readSafely(0), dismount = booleans.readSafely(1);

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

    private boolean handle(Player player, @NotNull ArmorStand stand, @NotNull Game game, float sideways, boolean jump, boolean dismount, PacketEvent event) {
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
        if (jump && (state.isSelecting() || state.isSpinning())) {
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