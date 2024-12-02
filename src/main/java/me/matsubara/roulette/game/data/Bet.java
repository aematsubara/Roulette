package me.matsubara.roulette.game.data;

import com.cryptomorin.xseries.XSound;
import lombok.Getter;
import lombok.Setter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.file.Config;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.hologram.Hologram;
import me.matsubara.roulette.model.Model;
import me.matsubara.roulette.model.stand.ModelLocation;
import me.matsubara.roulette.model.stand.PacketStand;
import me.matsubara.roulette.model.stand.StandSettings;
import me.matsubara.roulette.model.stand.data.ItemSlot;
import me.matsubara.roulette.util.GlowingEntities;
import me.matsubara.roulette.util.PluginUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.IntStream;

@Getter
@Setter
public final class Bet {

    // Plugin instance.
    private final RoulettePlugin plugin;

    // Game instance.
    private final Game game;

    // The owner of this bet.
    private final Player owner;

    // Hologram for displaying bet.
    private Hologram hologram;

    // Chip selected.
    private Chip chip;

    // Slot selected.
    private Slot slot;
    private Slot temp;

    // Stand showing chip.
    private PacketStand stand;

    // If this bet is in prison.
    private boolean isEnPrison;

    // If this bet won.
    private boolean won = false;

    // Bet offsets.
    private Pair<Axis, double[]> offset;
    private int offsetIndex = -1;

    // If this bet resulted in a win, then it will be saved here.
    private WinData winData;

    // COnfig values.
    private final XSound.Record selectSound = XSound.parse(Config.SOUND_SELECT.asString());
    private final @SuppressWarnings("unchecked") List<String> selectHologramLines = Config.SELECT_HOLOGRAM.getValue(List.class).stream()
            .map(object -> PluginUtils.translate((String) object))
            .toList();

    private static final BiFunction<String, Bet, String> FORMAT_HOLOGRAM = new BiFunction<>() {
        @Override
        public @NotNull String apply(@NotNull String line, @NotNull Bet bet) {
            Chip chip = bet.getChip();
            Slot slot = bet.getSlot();

            double originalMoney = chip.price();
            double expectedMoney = bet.getPlugin().getExpectedMoney(
                    originalMoney,
                    slot,
                    WinData.WinType.NORMAL);

            return line
                    .replace("%player%", bet.getOwner().getName())
                    .replace("%bet%", PluginUtils.getSlotName(slot))
                    .replace("%money%", PluginUtils.format(originalMoney))
                    .replace("%win-money%", PluginUtils.format(expectedMoney));
        }
    };

    public Bet(@NotNull Game game, Player owner) {
        this.plugin = game.getPlugin();
        this.game = game;
        this.owner = owner;
    }

    public boolean hasHologram() {
        return hologram != null;
    }

    public boolean hasChip() {
        return chip != null;
    }

    public boolean hasSlot() {
        return slot != null;
    }

    public boolean hasStand() {
        return stand != null;
    }

    public void handle(@NotNull Slot slot) {
        this.temp = this.slot;
        this.slot = slot;

        this.offset = slot.getOffsets(game.getType().isEuropean());

        double[] offsets = offset.getRight();
        Axis axis = offset.getLeft();

        if (temp != slot) {
            int[] validOffsets = IntStream.range(0, offsets.length).toArray();

            for (Bet bet : game.getAllBets()) {
                if (bet.equals(this)) continue;

                Player owner = bet.getOwner();
                if (owner.equals(this.owner)) continue;

                int offset = bet.getOffsetIndex();

                if (bet.getSlot() == slot && offset != -1) {
                    validOffsets = ArrayUtils.remove(validOffsets, offset);
                }

                offsetIndex = validOffsets[0];
            }

            if (offsetIndex == -1) offsetIndex = 0;
        }

        Model model = game.getModel();

        ModelLocation temp = model.getLocationByName(slot.name());
        if (temp == null) return;

        Location modelLocation = model.getLocation();
        Location where = temp.getLocation().clone().add(PluginUtils.offsetVector(
                new Vector(
                        axis == Axis.X ? offsets[offsetIndex] : 0.0d,
                        0.0d,
                        axis == Axis.Z ? offsets[offsetIndex] : 0.0d),
                modelLocation.getYaw(),
                modelLocation.getPitch()));

        setHologram(slot, where);
        setStand(slot, where);
    }

    private void setHologram(Slot slot, @NotNull Location where) {
        this.slot = slot;

        // Where to spawn/teleport this hologram (with an offset, centered at the top of the chip).
        Location modelLocation = game.getModel().getLocation();
        Location finalWhere = where.clone().add(PluginUtils.offsetVector(
                new Vector(
                        0.055d,
                        0.275d,
                        0.257d),
                modelLocation.getYaw(),
                modelLocation.getPitch()));

        // Play move chip sound at hologram location.
        game.playSound(finalWhere, selectSound);

        // No need to create another hologram.
        if (!hasHologram()) {
            // Creates a personal hologram at the location of the selected slot.
            this.hologram = new Hologram(game, finalWhere);
            this.hologram.setVisibleByDefault(false);
            this.hologram.showTo(owner);

            // Add lines.
            for (String line : selectHologramLines) {
                hologram.addLines(FORMAT_HOLOGRAM.apply(line, this));
            }
            return;
        }

        hologram.teleport(finalWhere);

        // Update lines.
        for (int i = 0; i < selectHologramLines.size(); i++) {
            hologram.setLine(i, FORMAT_HOLOGRAM.apply(selectHologramLines.get(i), this));
        }
    }

    private void setStand(Slot slot, Location where) {
        this.slot = slot;

        Set<Player> to = game.getSeeingPlayers();

        // No need to create another stand.
        if (!hasStand()) {
            // Spawn stand with its settings.
            ModelLocation temp = game.getModel().getLocationByName(slot.name());
            if (temp == null) return;

            StandSettings settings = temp.getSettings().clone();
            settings.setMarker(true);
            settings.getEquipment().put(ItemSlot.MAINHAND, PluginUtils.createHead(chip.url()));

            to.forEach((this.stand = new PacketStand(plugin, where, settings))::spawn);
        } else {
            // Teleport.
            stand.teleport(to, where);
        }

        updateStandGlow(owner);
    }

    public void updateStandGlow(Player player) {
        GlowingEntities glowing = plugin.getGlowingEntities();
        if (glowing == null || !hasStand()) return;

        try {
            glowing.setGlowing(
                    stand.getId(),
                    stand.getUniqueId().toString(),
                    player,
                    game.getGlowColor(player));

            // After sending the metadata to the player we have to disable the glow in the settings.
            StandSettings settings = stand.getSettings();
            settings.setGlow(true);
            stand.sendMetadata(player);
            settings.setGlow(false);
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
        }
    }

    public void removeStandGlow(Player player) {
        GlowingEntities glowing = plugin.getGlowingEntities();
        if (glowing == null || !hasStand()) return;

        try {
            glowing.unsetGlowing(stand.getId(), player);
            stand.getSettings().setGlow(false);
            stand.sendMetadata(player);
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
        }
    }

    public void hide() {
        removeStandGlow(owner);
        if (hasHologram()) hologram.hideTo(owner);
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

    public void setWon() {
        if (winData != null) won = true;
    }
}