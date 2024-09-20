package me.matsubara.roulette.game.state;

import lombok.Getter;
import lombok.Setter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.GameState;
import me.matsubara.roulette.game.data.Bet;
import me.matsubara.roulette.gui.ChipGUI;
import me.matsubara.roulette.manager.ConfigManager;
import me.matsubara.roulette.manager.MessageManager;
import me.matsubara.roulette.npc.NPC;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Getter
@Setter
public final class Selecting extends BukkitRunnable {

    private final RoulettePlugin plugin;
    private final Game game;

    private int seconds;

    public Selecting(@NotNull RoulettePlugin plugin, @NotNull Game game) {
        this.plugin = plugin;
        this.game = game;
        this.seconds = ConfigManager.Config.COUNTDOWN_SELECTING.asInt();

        game.setState(GameState.SELECTING);

        NPC npc = game.getNpc();
        npc.lookAtDefaultLocation();

        MessageManager messageManager = plugin.getMessageManager();
        game.npcBroadcast(MessageManager.Message.BETS);

        // Hide the join hologram for every player.
        game.getJoinHologram().setVisibleByDefault(false);

        // Open chips GUI if necessary.
        for (Player player : game.getPlayers()) {
            List<Bet> bets = game.getBets(player);

            // If the player has at least 1 bet in prison, send 1 reminder message.
            // Players can't make new bets when they have bets in prison.
            if (bets.stream().anyMatch(Bet::isEnPrison)) {
                messageManager.send(player, MessageManager.Message.BET_IN_PRISON);
                continue;
            }

            // Set reminder message.
            messageManager.send(player, MessageManager.Message.SELECT_BET);

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
        if (seconds == 0) {
            startSpinningTask();
            cancel();
            return;
        }

        if (seconds % 5 == 0 || seconds <= 3) {
            // Play countdown sound.
            game.playSound(ConfigManager.Config.SOUND_COUNTDOWN.asString());

            // Send countdown.
            game.broadcast(MessageManager.Message.SPINNING, line -> line.replace("%seconds%", String.valueOf(seconds)));
        }
        seconds--;
    }
}