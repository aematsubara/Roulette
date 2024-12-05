package me.matsubara.roulette.listener.protocol;

import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerInput;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientSteerVehicle;
import lombok.Getter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.file.Config;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.GameRule;
import me.matsubara.roulette.game.GameState;
import me.matsubara.roulette.game.data.Bet;
import me.matsubara.roulette.game.data.PlayerInput;
import me.matsubara.roulette.gui.BetsGUI;
import me.matsubara.roulette.gui.ConfirmGUI;
import me.matsubara.roulette.manager.GameManager;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SteerVehicle extends SimplePacketListenerAbstract {

    private final RoulettePlugin plugin;
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private final @Getter Map<UUID, PlayerInput> input = new ConcurrentHashMap<>();

    public SteerVehicle(RoulettePlugin plugin) {
        super(PacketListenerPriority.HIGHEST);
        this.plugin = plugin;
    }

    public PlayerInput getInput(@NotNull Player player) {
        return input.getOrDefault(player.getUniqueId(), PlayerInput.ZERO);
    }

    public void removeInput(@NotNull Player player) {
        input.remove(player.getUniqueId());
    }

    @Override
    public void onPacketPlayReceive(@NotNull PacketPlayReceiveEvent event) {
        PacketType.Play.Client type = event.getPacketType();

        if (type != PacketType.Play.Client.STEER_VEHICLE
                && (!GameManager.MODERN_APPROACH || type != PacketType.Play.Client.PLAYER_INPUT)) return;

        if (!(event.getPlayer() instanceof Player player)) return;

        PlayerInput input = createInput(event, type);
        this.input.put(player.getUniqueId(), input);

        if (GameManager.MODERN_APPROACH) return;

        handle(player, null, input);
    }

    private @NotNull PlayerInput createInput(@NotNull PacketPlayReceiveEvent event, PacketType.Play.Client type) {
        if (type == PacketType.Play.Client.STEER_VEHICLE) {
            WrapperPlayClientSteerVehicle wrapper = new WrapperPlayClientSteerVehicle(event);
            return new PlayerInput(
                    wrapper.getSideways(),
                    wrapper.getForward(),
                    wrapper.isJump(),
                    wrapper.isUnmount());
        }

        WrapperPlayClientPlayerInput wrapper = new WrapperPlayClientPlayerInput(event);
        return new PlayerInput(
                wrapper.isLeft() ? 0.98f : wrapper.isRight() ? -0.98f : 0.0f,
                wrapper.isForward() ? 0.98f : wrapper.isBackward() ? -0.98f : 0.0f,
                wrapper.isJump(),
                wrapper.isShift());
    }

    public void handle(Player player, @Nullable Game game, @NotNull PlayerInput input) {
        // Nothing changed?
        if (input.forward() == 0.0f
                && input.sideways() == 0.0f
                && !input.jump()
                && !input.dismount()) return;

        Game temp = game != null ? game : plugin.getGameManager().getGameByPlayer(player);
        if (temp == null) return;

        float forward = input.forward(), sideways = input.sideways();
        boolean jump = input.jump(), dismount = input.dismount();

        // Handle shift.
        if (dismount) {
            // Open leave confirms gui to the player sync.
            runTask(() -> new ConfirmGUI(temp, player, ConfirmGUI.ConfirmType.LEAVE));
            return;
        }

        // The player is in cooldown.
        boolean cooldown = this.cooldown.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis();
        if (cooldown) return;

        // For some reason, the player still has no bets.
        List<Bet> bets = temp.getBets(player);
        if (bets.isEmpty()) return;

        GameState state = temp.getState();
        boolean prison = bets.stream().anyMatch(Bet::isEnPrison);

        // Move the current bet.
        Bet bet = temp.getSelectedBet(player);
        if (bet == null) return;

        // Handle chip movement (up/down/left/right).
        if ((forward != 0.0f || sideways != 0.0f) && canMoveChip(player, temp, bet)) {
            temp.moveDirectionalChip(player, forward, sideways);
        }

        // Handle chair movement (left/right).
        if (sideways != 0.0f && canSwapChair(player, temp, prison)) {
            runTask(() -> temp.sitPlayer(player, sideways < 0.0f));
        }

        // Handle jump.
        if (jump
                && state.isSelecting()
                && !temp.isDone(player)
                && !bets.isEmpty()
                && (bets.size() > 1 || bets.get(0).hasChip())) {
            // Only allow opening the bet GUI if the player doesn't have any bet in prison
            // and already has a bet with a chip.
            runTask(() -> new BetsGUI(temp, player));
        }

        // Put player in cooldown.
        long interval = Math.max(200L, Config.MOVE_INTERVAL.asLong());
        this.cooldown.put(player.getUniqueId(), System.currentTimeMillis() + interval);
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
        return Config.SWAP_CHAIR.asBool() || player.hasPermission("roulette.swapchair");
    }
}