package me.matsubara.roulette.game.state;


import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.manager.ConfigManager;
import me.matsubara.roulette.manager.MessageManager;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.GameState;
import org.bukkit.scheduler.BukkitRunnable;

public final class Starting extends BukkitRunnable {

    private final RoulettePlugin plugin;
    private final Game game;

    private int seconds;

    public Starting(RoulettePlugin plugin, Game game) {
        this.plugin = plugin;
        this.game = game;

        this.seconds = game.getStartTime();
        game.setState(GameState.STARTING);
    }

    @Override
    public void run() {
        if (seconds == 0) {
            // Start selecting task.
            game.setSelectingTask(new Selecting(plugin, game)
                    .runTaskTimer(plugin, 20L, 20L));

            cancel();
            return;
        }
        if (seconds % 5 == 0 || seconds == 3 || seconds == 2 || seconds == 1) {
            // Play countdown sound.
            game.playSound(ConfigManager.Config.SOUND_COUNTDOWN.asString());

            // Send countdown.
            game.broadcast(MessageManager.Message.STARTING.asString().replace("%seconds%", String.valueOf(seconds)));
        }
        seconds--;
    }
}