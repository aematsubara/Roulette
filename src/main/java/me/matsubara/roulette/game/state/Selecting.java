package me.matsubara.roulette.game.state;

import com.cryptomorin.xseries.XSound;
import com.cryptomorin.xseries.particles.ParticleDisplay;
import com.cryptomorin.xseries.particles.XParticle;
import lombok.Getter;
import lombok.Setter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.file.Config;
import me.matsubara.roulette.file.Messages;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.GameState;
import me.matsubara.roulette.game.data.Bet;
import me.matsubara.roulette.game.data.PlayerInput;
import me.matsubara.roulette.game.data.Slot;
import me.matsubara.roulette.gui.ChipGUI;
import me.matsubara.roulette.model.stand.ModelLocation;
import me.matsubara.roulette.util.PluginUtils;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

@Getter
@Setter
public final class Selecting extends BukkitRunnable {

    private final RoulettePlugin plugin;
    private final Game game;
    private final Slot[] slots;

    private final boolean experimental = Config.EXPERIMENTAL.asBool();
    private final XSound.Record countdownSound = XSound.parse(Config.SOUND_COUNTDOWN.asString());

    private int ticks;

    private static final Color MIXED_COLOR = Color.ORANGE;
    private static final Vector PARTICLES_OFFSET = new Vector(-0.08d, 0.0d, 0.05d);
    private static final float PARTICLES_SIZE = 0.25f;
    private static final double PARTICLES_HEIGHT = 0.45d;
    private static final double PARTICLES_RATE = 0.005d;
    private static final double PARTICLES_X_LENGTH = 0.28d;
    private static final double PARTICLES_Z_LENGTH = 0.425d;

    private static final Vector SLOT_OFFSET = new Vector(0.18d, 0.0d, 0.265d);
    private static final double SLOT_HEIGHT = 0.45d;
    private static final float CHAIR_FOV = 85.0f;

    public Selecting(@NotNull RoulettePlugin plugin, @NotNull Game game) {
        this.plugin = plugin;
        this.game = game;
        this.slots = Slot.values(game);
        this.ticks = Config.COUNTDOWN_SELECTING.asInt() * 20;

        game.setState(GameState.SELECTING);
        game.lookAtFace(game.getPlayersNPCFace());
        game.npcBroadcast(Messages.Message.BETS);

        // Hide the join hologram for every player.
        game.getJoinHologram().setVisibleByDefault(false);

        Messages messages = plugin.getMessages();

        // Open chips GUI if necessary.
        for (Player player : game.getPlayers()) {
            List<Bet> bets = game.getBets(player);

            // If the player has at least 1 bet in prison, send 1 reminder message.
            // Players can't make new bets when they have bets in prison.
            if (bets.stream().anyMatch(Bet::isEnPrison)) {
                messages.send(player, Messages.Message.BET_IN_PRISON);
                continue;
            }

            // Set reminder message.
            messages.send(player, Messages.Message.SELECT_BET);

            // Open the chip menu.
            new ChipGUI(game, player, false);
        }
    }

    public void startSpinningTask() {
        Spinning spinning = new Spinning(plugin, game);
        game.setSpinning(spinning);
        spinning.runTaskTimer(plugin, 1L, 1L);
    }

    @Override
    public void run() {
        if (ticks == 0) {
            startSpinningTask();
            cancel();
            return;
        }

        if (experimental && ticks % 5 == 0) {
            for (Player player : game.getPlayers()) {
                if (game.isDone(player)) continue;

                showCurrentBetNumbers(player);

                PlayerInput input = plugin.getSteerVehicle().getInput(player);
                if (!input.sprint()) continue;

                Slot target = findTargetSlot(player);
                if (target != null) game.moveChipFreely(player, target);
            }
        }

        if (ticks % 100 == 0 || (ticks <= 60 && ticks % 20 == 0)) {
            // Play countdown sound.
            game.playSound(countdownSound);

            // Send countdown.
            game.broadcast(Messages.Message.SPINNING, line -> line.replace("%seconds%", String.valueOf(ticks / 20)));
        }

        ticks--;
    }

    private void showCurrentBetNumbers(Player player) {
        Bet bet = game.getSelectedBet(player);
        if (bet == null || !bet.hasChip()) return;

        Slot parent = bet.getSlot();
        if (parent == null) return;

        Color color = switch (parent.getColor()) {
            case BLACK -> Color.BLACK;
            case RED -> Color.RED;
            case GREEN -> Color.GREEN;
            default -> MIXED_COLOR;
        };

        if (parent.isSingleInclusive()) {
            handleParticles(player, parent, color);
            return;
        }

        for (int number : parent.getChilds()) {
            Slot child = PluginUtils.getOrNull(Slot.class, "SLOT_" + number);
            if (child != null) handleParticles(player, child, color);
        }
    }

    // I'm bad at math, so this is my best attempt at creating a square with particles...
    // Maybe this has to be done async?
    private void handleParticles(Player player, @NotNull Slot slot, Color color) {
        ModelLocation temp = game.getModel().getLocationByName(slot.name());
        if (temp == null) return;

        Location location = temp.getLocation();

        Location start = location.clone()
                .add(PluginUtils.offsetVector(PARTICLES_OFFSET, location.getYaw(), location.getPitch()))
                .add(0.0d, PARTICLES_HEIGHT, 0.0d);

        for (int i = 0; i < 4; i++) {
            double length = i % 2 != 0 ? PARTICLES_X_LENGTH : PARTICLES_Z_LENGTH;
            start = drawLine(player, start, color, length);
            start.setYaw((start.getYaw() - 90.0f) % 360);
        }
    }

    private @NotNull Location drawLine(Player player, @NotNull Location location, Color color, double limit) {
        Location start = location.clone();

        for (double i = 0.0d; i < limit; i += PARTICLES_RATE) {
            Vector direction = start.getDirection().multiply(i);
            start.add(direction);

            ParticleDisplay.of(XParticle.DUST)
                    .withColor(color, PARTICLES_SIZE)
                    .onlyVisibleTo(player)
                    .spawn(start);

            start.subtract(direction);
        }

        return start.add(start.getDirection().multiply(limit));
    }

    // Credits to blablubbabc!
    private Slot findTargetSlot(@NotNull Player player) {
        double minDot = Double.MIN_VALUE;
        Slot closest = null;

        for (Slot slot : slots) {
            ModelLocation temp = game.getModel().getLocationByName(slot.name());
            if (temp == null) continue;

            Location target = temp.getLocation().clone().add(0.0d, SLOT_HEIGHT, 0.0d);
            target.add(PluginUtils.offsetVector(SLOT_OFFSET, target.getYaw(), target.getPitch()));

            Location playerLocation = player.getEyeLocation();

            // The target must be in front of the chair.
            double xDifference = target.getX() - playerLocation.getX();
            double zDifference = target.getZ() - playerLocation.getZ();

            float yaw = (float) (-Math.atan2(xDifference, zDifference) / Math.PI * 180.0d);
            float angleDifference = Math.abs(((yaw < 0 ? yaw + 360 : yaw) - playerLocation.getYaw() + 540) % 360 - 180);

            // Target is out of the FOV of the chair.
            if (angleDifference > CHAIR_FOV) continue;

            Vector direction = playerLocation.getDirection().normalize();
            Vector targetDirection = target.toVector().subtract(playerLocation.toVector()).normalize();

            double dot = targetDirection.dot(direction);
            if (dot > 0.97d && dot > minDot) {
                minDot = dot;
                closest = slot;
            }
        }

        return closest;
    }
}