package me.matsubara.roulette.game.data;

import lombok.Getter;
import lombok.Setter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.hologram.Hologram;
import me.matsubara.roulette.manager.ConfigManager;
import me.matsubara.roulette.model.Model;
import me.matsubara.roulette.model.stand.PacketStand;
import me.matsubara.roulette.model.stand.StandSettings;
import me.matsubara.roulette.util.GlowingEntities;
import me.matsubara.roulette.util.PluginUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.List;
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

    BiFunction<String, Bet, String> FORMAT_HOLOGRAM = new BiFunction<>() {
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

        PacketStand stand = model.getByName(slot.name());
        if (stand == null) return;

        Location modelLocation = model.getLocation();
        Location where = stand.getLocation().clone().add(PluginUtils.offsetVector(
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

        // Play move chip sound at hologram location (sync to prevent issues).
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Sound selectSound = PluginUtils.getOrNull(Sound.class, ConfigManager.Config.SOUND_SELECT.asString());
            if (selectSound != null) owner.getWorld().playSound(finalWhere, selectSound, 1.0f, 1.0f);
        });

        // No need to create another hologram.
        if (!hasHologram()) {
            // Creates a personal hologram at the location of the selected slot.
            this.hologram = new Hologram(plugin, finalWhere);
            this.hologram.setVisibleByDefault(false);
            this.hologram.showTo(owner);

            // Add lines.
            for (String line : ConfigManager.Config.SELECT_HOLOGRAM.asList()) {
                hologram.addLines(FORMAT_HOLOGRAM.apply(line, this));
            }
            return;
        }

        hologram.teleport(finalWhere);

        // Update lines.
        List<String> lines = ConfigManager.Config.SELECT_HOLOGRAM.asList();
        for (int i = 0; i < lines.size(); i++) {
            hologram.setLine(i, FORMAT_HOLOGRAM.apply(lines.get(i), this));
        }
    }

    private void setStand(Slot slot, Location where) {
        this.slot = slot;

        // No need to create another stand.
        if (!hasStand()) {
            // Spawn stand with its settings.
            PacketStand stand = game.getModel().getByName(slot.name());
            if (stand == null) return;

            StandSettings settings = stand.getSettings().clone();
            settings.setMarker(true);
            settings.getEquipment().put(PacketStand.ItemSlot.MAINHAND, PluginUtils.createHead(chip.url()));

            this.stand = new PacketStand(where, settings, true);

        } else {
            // Teleport.
            stand.teleport(where);
        }

        updateStandGlow(owner);
    }

    public void updateStandGlow(Player player) {
        GlowingEntities glowing = plugin.getGlowingEntities();
        if (glowing == null || !hasStand()) return;

        try {
            glowing.setGlowing(
                    stand.getEntityId(),
                    stand.getEntityUniqueId().toString(),
                    player,
                    game.getGlowColor(player));
            stand.updateMetadata(player);
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
        }
    }

    public void removeStandGlow(Player player) {
        GlowingEntities glowing = plugin.getGlowingEntities();
        if (glowing == null || !hasStand()) return;

        try {
            glowing.unsetGlowing(stand.getEntityId(), player);
            stand.updateMetadata(player);
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
        }
    }

    public void hide() {
        removeStandGlow(owner);
        hologram.hideTo(owner);
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