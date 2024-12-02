package me.matsubara.roulette.animation;

import lombok.Getter;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.model.stand.PacketStand;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

@Getter
public final class MoneyAnimation extends BukkitRunnable {

    private final Game game;
    private final PacketStand moneySlot;
    private final Set<Player> seeing;

    private boolean goUp;
    private int count;
    private int toFinish;

    public MoneyAnimation(@NotNull Game game) {
        this.game = game;
        this.moneySlot = game.getModel().getStandByName("MONEY_SLOT");
        this.seeing = game.getSeeingPlayers();

        this.goUp = true;
        this.count = 0;
        this.toFinish = 0;

        game.setMoneyAnimation(this);
        runTaskTimerAsynchronously(game.getPlugin(), 1L, 1L);
    }

    @Override
    public void run() {
        if (count == 10) {
            count = 0;
            goUp = !goUp;
            toFinish++;
            if (toFinish == 4) {
                game.setMoneyAnimation(null);
                cancel();
                return;
            }
        }

        Location to = moneySlot.getLocation().add(0.0d, goUp ? 0.01d : -0.01d, 0.0d);
        moneySlot.teleport(seeing, to);

        count++;
    }
}