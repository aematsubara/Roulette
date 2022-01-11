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

import java.util.Map;

public final class Selecting extends BukkitRunnable {

    private final RoulettePlugin plugin;
    private final Game game;

    private int seconds;

    public Selecting(RoulettePlugin plugin, Game game) {
        this.plugin = plugin;
        this.game = game;
        this.seconds = ConfigManager.Config.COUNTDOWN_SELECTING.asInt();

        game.setState(GameState.SELECTING);

        game.broadcast(plugin.getMessageManager().getRandomNPCMessage(game.getNPC(), "bets"));
        game.broadcast(MessageManager.Message.SELECT_BET.asString());

        // Hide the join hologram for every player.
        game.getJoinHologram().setVisibleByDefault(false);

        for (Map.Entry<Player, Bet> entry : game.getPlayers().entrySet()) {
            // If prison rule is enabled, the player may already have a chip, no need to open gui.
            if (entry.getValue().hasChip()) {
                // Show hologram again.
                entry.getValue().getHologram().showTo(entry.getKey());
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
            game.setSpinningTask(new Spinning(plugin, game)
                    .runTaskTimer(plugin, 1L, 1L));

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