package me.matsubara.roulette.game.data;

import lombok.Getter;
import lombok.Setter;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.hologram.Hologram;
import me.matsubara.roulette.manager.ConfigManager;
import me.matsubara.roulette.model.stand.PacketStand;
import me.matsubara.roulette.model.stand.StandSettings;
import me.matsubara.roulette.util.GlowingEntities;
import me.matsubara.roulette.util.PluginUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Axis;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Getter
@Setter
public final class Bet {

    // Game instance.
    private final Game game;

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
    private boolean won;

    // Bet offsets.
    private Pair<Axis, double[]> offset;
    private int offsetIndex = -1;

    // The index of the glow color to use (default to WHITE).
    private int glowColorIndex = ArrayUtils.indexOf(GLOW_COLORS, ChatColor.WHITE);

    // All valid colors for glowing.
    private static final ChatColor[] GLOW_COLORS = Stream.of(ChatColor.values())
            .filter(ChatColor::isColor)
            .toArray(ChatColor[]::new);

    public Bet(Game game) {
        this.game = game;
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

    public void handle(Player player, @NotNull Slot slot) {
        this.temp = this.slot;
        this.slot = slot;

        this.offset = slot.getOffsets(game.getType().isEuropean());

        double[] offsets = offset.getRight();
        Axis axis = offset.getLeft();

        if (temp != slot) {
            int[] validOffsets = IntStream.range(0, offsets.length).toArray();

            for (Map.Entry<Player, Bet> entry : game.getPlayers().entrySet()) {
                Player playing = entry.getKey();
                if (playing.equals(player)) continue;

                Bet bet = entry.getValue();
                int offset = bet.getOffsetIndex();

                if (bet.getSlot() == slot && offset != -1) {
                    validOffsets = ArrayUtils.remove(validOffsets, offset);
                }

                offsetIndex = validOffsets[0];
            }

            if (offsetIndex == -1) offsetIndex = 0;
        }

        PacketStand stand = game.getModel().getByName(slot.name());
        if (stand == null) return;

        Location where = stand.getLocation().clone().add(PluginUtils.offsetVector(
                new Vector(
                        axis == Axis.X ? offsets[offsetIndex] : 0.0d,
                        0.0d,
                        axis == Axis.Z ? offsets[offsetIndex] : 0.0d),
                game.getModel().getLocation().getYaw(),
                game.getModel().getLocation().getPitch()));

        setHologram(player, slot, where);
        setStand(player, slot, where);
    }

    private void setHologram(Player player, Slot slot, @NotNull Location where) {
        this.slot = slot;

        // Where to spawn/teleport this hologram (with an offset, centered at the top of the chip).
        Location finalWhere = where.clone().add(PluginUtils.offsetVector(
                new Vector(
                        0.055d,
                        0.275d,
                        0.257d),
                game.getModel().getLocation().getYaw(),
                game.getModel().getLocation().getPitch()));

        // Play move chip sound at hologram location (sync to prevent issues).
        game.getPlugin().getServer().getScheduler().runTask(game.getPlugin(), () -> {
            Sound selectSound = PluginUtils.getOrNull(Sound.class, ConfigManager.Config.SOUND_SELECT.asString());
            if (selectSound != null) player.getWorld().playSound(finalWhere, selectSound, 1.0f, 1.0f);
        });

        // No need to create another hologram.
        if (!hasHologram()) {
            // Creates a personal hologram at the location of the selected slot.
            this.hologram = new Hologram(game.getPlugin(), finalWhere);
            this.hologram.setVisibleByDefault(false);
            this.hologram.showTo(player);

            // Add lines.
            for (String line : ConfigManager.Config.SELECT_HOLOGRAM.asList()) {
                hologram.addLines(line
                        .replace("%player%", player.getName())
                        .replace("%bet%", PluginUtils.getSlotName(slot))
                        .replace("%money%", PluginUtils.format(chip.getPrice())));
            }
            return;
        }

        hologram.teleport(finalWhere);

        // Update lines.
        List<String> lines = ConfigManager.Config.SELECT_HOLOGRAM.asList();
        for (int i = 0; i < lines.size(); i++) {
            hologram.setLine(i, lines.get(i)
                    .replace("%player%", player.getName())
                    .replace("%bet%", PluginUtils.getSlotName(slot))
                    .replace("%money%", PluginUtils.format(chip.getPrice())));
        }
    }

    private void setStand(Player player, Slot slot, Location where) {
        this.slot = slot;

        // No need to create another stand.
        if (!hasStand()) {
            // Spawn stand with its settings.
            PacketStand stand = game.getModel().getByName(slot.name());
            if (stand == null) return;

            StandSettings settings = stand.getSettings();
            settings.setMarker(true);
            settings.getEquipment().put(PacketStand.ItemSlot.MAINHAND, PluginUtils.createHead(chip.getUrl()));

            this.stand = new PacketStand(where, settings, true, game.getPlugin().getConfigManager().getRenderDistance());

        } else {
            // Teleport.
            stand.teleport(where);
        }

        updateStandGlow(player);
    }

    public void updateStandGlow(Player player) {
        GlowingEntities glowing = game.getPlugin().getGlowingEntities();
        if (glowing == null) return;

        if (!hasStand()) return;

        try {
            glowing.setGlowing(
                    stand.getEntityId(),
                    stand.getEntityUniqueId().toString(),
                    player,
                    GLOW_COLORS[glowColorIndex]);
            stand.updateMetadata();
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
        }
    }

    public void nextGlowColor() {
        if (!hasStand()) return;

        glowColorIndex++;
        if (glowColorIndex > GLOW_COLORS.length - 1) glowColorIndex = 0;
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