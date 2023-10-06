package me.matsubara.roulette.game.state;

import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.GameState;
import me.matsubara.roulette.game.data.Bet;
import me.matsubara.roulette.gui.ChipGUI;
import me.matsubara.roulette.manager.ConfigManager;
import me.matsubara.roulette.manager.MessageManager;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public final class Selecting extends BukkitRunnable {

    private final RoulettePlugin plugin;
    private final Game game;

    private int seconds;

    public Selecting(@NotNull RoulettePlugin plugin, @NotNull Game game) {
        this.plugin = plugin;
        this.game = game;
        this.seconds = ConfigManager.Config.COUNTDOWN_SELECTING.asInt();

        game.setState(GameState.SELECTING);

        game.broadcast(plugin.getMessageManager().getRandomNPCMessage(game.getNpc(), "bets"));

        for (Map.Entry<Player, Bet> entry : game.getPlayers().entrySet()) {
            entry.getKey().sendMessage(entry.getValue().isEnPrison() ?
                    MessageManager.Message.BET_IN_PRISON.asString() :
                    MessageManager.Message.SELECT_BET.asString());
        }

        // Hide the join hologram for every player.
        game.getJoinHologram().setVisibleByDefault(false);

        for (Map.Entry<Player, Bet> entry : game.getPlayers().entrySet()) {
            // If prison rule is enabled, the player may already have a chip, no need to open GUI.
            Bet bet = entry.getValue();
            if (bet.hasChip()) {
                // Show hologram again.
                bet.getHologram().showTo(entry.getKey());
                continue;
            }

            // Open chip menu.
            new ChipGUI(game, entry.getKey());
        }
    }

    @Override
    public void run() {
        if (seconds == 0) {
            // Start spinning task.
            game.setSpinningTask(new Spinning(plugin, game).runTaskTimer(plugin, 1L, 1L));
            cancel();
            return;
        }

        if (seconds % 5 == 0 || seconds == 3 || seconds == 2 || seconds == 1) {
            // Play countdown sound.
            game.playSound(ConfigManager.Config.SOUND_COUNTDOWN.asString());

            // Send countdown.
            game.broadcast(MessageManager.Message.SPINNING.asString().replace("%seconds%", String.valueOf(seconds)));
        }
        seconds--;
    }
}