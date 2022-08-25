package me.matsubara.roulette.game.state;

import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.gui.ChipGUI;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public final class JoinSelecting extends BukkitRunnable {

    private final Game game;
    private final Player player;

    public JoinSelecting(Game game, Player player) {
        this.game = game;
        this.player = player;
    }

    @Override
    public void run() {
        new ChipGUI(game, player);
    }
}