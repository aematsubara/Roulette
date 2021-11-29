package me.matsubara.roulette.game.data;

import com.cryptomorin.xseries.XSound;
import me.matsubara.roulette.manager.ConfigManager;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.hologram.Hologram;
import me.matsubara.roulette.model.stand.StandSettings;
import me.matsubara.roulette.model.stand.PacketStand;
import me.matsubara.roulette.util.PluginUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public final class Bet {

    // Game instance.
    private final Game game;

    // Hologram for displaying bet.
    private Hologram hologram;

    // Chip selected.
    private Chip chip;

    // Slot selected.
    private Slot slot;

    // Stand showing chip.
    private PacketStand stand;

    // If this bet is in prison.
    private boolean isEnPrison;

    // If this bet already was in prison.
    private boolean wasEnPrison;

    // If this bet won.
    private boolean won;

    public Bet(Game game) {
        this.game = game;
        this.isEnPrison = false;
        this.wasEnPrison = false;
        this.won = false;
    }

    /**
     * Whether the player has a hologram.
     */
    public boolean hasHologram() {
        return hologram != null;
    }

    /**
     * Whether the player has chosen a chip.
     */
    public boolean hasChip() {
        return chip != null;
    }

    /**
     * Whether the player has chosen a slot.
     */
    public boolean hasSlot() {
        return slot != null;
    }

    /**
     * Whether the player has a personal hologram for showing the chip.
     */
    public boolean hasStand() {
        return stand != null;
    }

    public Game getGame() {
        return game;
    }

    public Hologram getHologram() {
        return hologram;
    }

    private void setHologram(Player player, Slot slot) {
        this.slot = slot;

        // Hologram offset, centered at the top of the chip.
        Vector offset = PluginUtils.offsetVector(
                new Vector(
                        0.055d,
                        0.275d,
                        0.257d),
                game.getModel().getLocation().getYaw(),
                game.getModel().getLocation().getPitch());

        // Where to spawn/teleport this hologram.
        Location where = game
                .getModel()
                .getLocations()
                .get(slot.name())
                .getKey()
                .clone()
                .add(offset);

        // Play move chip sound at hologram location.
        XSound.play(where, ConfigManager.Config.SOUND_SELECT.asString());

        // No need to create another hologram.
        if (!hasHologram()) {
            // Creates a personal hologram at the location of the selected slot.
            this.hologram = new Hologram(game.getPlugin(), where);
            this.hologram.setVisibleByDefault(false);
            this.hologram.showTo(player);

            // Add lines.
            for (String line : ConfigManager.Config.SELECT_HOLOGRAM.asList()) {
                hologram.addLines(line
                        .replace("%player%", player.getName())
                        .replace("%bet%", PluginUtils.getSlotName(slot))
                        .replace("%money%", game.getPlugin().getEconomy().format(chip.getPrice())));
            }
        } else {
            hologram.teleport(where);

            // Update lines.
            List<String> lines = ConfigManager.Config.SELECT_HOLOGRAM.asList();
            for (int i = 0; i < lines.size(); i++) {
                hologram.setLine(i, lines.get(i)
                        .replace("%player%", player.getName())
                        .replace("%bet%", PluginUtils.getSlotName(slot))
                        .replace("%money%", game.getPlugin().getEconomy().format(chip.getPrice())));
            }
        }
    }

    public Chip getChip() {
        return chip;
    }

    public void setChip(Chip chip) {
        this.chip = chip;
    }

    public Slot getSlot() {
        return slot;
    }

    public void setSlot(Slot slot) {
        this.slot = slot;
    }

    public PacketStand getStand() {
        return stand;
    }

    public boolean isEnPrison() {
        return isEnPrison;
    }

    public void setEnPrison(boolean enPrison) {
        isEnPrison = enPrison;
    }

    public boolean wasEnPrison() {
        return wasEnPrison;
    }

    public void setWasEnPrison(boolean wasEnPrison) {
        this.wasEnPrison = wasEnPrison;
    }

    public boolean won() {
        return won;
    }

    public void setHasWon(boolean won) {
        this.won = won;
    }

    private void setStand(Slot slot) {
        this.slot = slot;

        Map.Entry<Location, StandSettings> entry = game.getModel().getLocations().get(slot.name());

        // No need to create another stand.
        if (!hasStand()) {
            // Spawn stand with its settings.

            this.stand = new PacketStand(entry.getKey(), entry.getValue());

            // Spawn chip item async.
            Bukkit.getScheduler().runTaskAsynchronously(
                    game.getPlugin(),
                    () -> stand.setEquipment(PluginUtils.createHead(chip.getUrl()), PacketStand.ItemSlot.MAINHAND));
        } else {
            // Teleport.
            this.stand.teleport(entry.getKey());
        }
    }

    public void handle(Player player, Slot slot) {
        setHologram(player, slot);
        setStand(slot);
    }

    public void remove() {
        // Remove hologram.
        if (hasHologram()) {
            hologram.destroy();
            hologram = null;
        }

        // Remove chip.
        if (hasStand()) {
            stand.destroy();
            stand = null;
        }
    }
}