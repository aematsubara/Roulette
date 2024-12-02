package me.matsubara.roulette.hologram;

import lombok.Getter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.model.stand.PacketStand;
import me.matsubara.roulette.model.stand.StandSettings;
import me.matsubara.roulette.util.PluginUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

@Getter
public final class Hologram {

    // The game this hologram belongs to.
    private final Game game;

    // Plugin instance.
    private final RoulettePlugin plugin;

    // Text of this hologram.
    private final List<String> lines = new ArrayList<>();

    // Stands of this hologram.
    private final List<PacketStand> stands = new ArrayList<>();

    // Players seeing this hologram.
    private final Map<UUID, Boolean> visibility = new HashMap<>();

    // Location of this hologram.
    private Location location;

    // If this hologram is visible by default.
    private boolean visibleByDefault = true;

    // Task used for rainbow color.
    private int taskId = -1;

    // Space between lines.
    private static final double LINE_DISTANCE = 0.23d;

    // Rainbow colors.
    private static final String[] RAINBOW = Stream.of(
                    ChatColor.RED,
                    ChatColor.GOLD,
                    ChatColor.YELLOW,
                    ChatColor.GREEN,
                    ChatColor.AQUA,
                    ChatColor.LIGHT_PURPLE)
            .map(ChatColor::toString)
            .toArray(String[]::new);

    public Hologram(@NotNull Game game, Location location) {
        this.game = game;
        this.plugin = game.getPlugin();
        this.location = location;
    }

    public void setVisibleByDefault(boolean visibleByDefault) {
        if (this.visibleByDefault == visibleByDefault) return;

        this.visibleByDefault = visibleByDefault;

        World world = location.getWorld();
        if (world == null) return;

        for (Player player : world.getPlayers()) {
            if (game.equals(plugin.getGameManager().getGameByPlayer(player))) continue;
            if (game.getModel().getOut().contains(player.getUniqueId())) continue;

            if (visibleByDefault) {
                if (isVisibleTo(player, false)) showPackets(player);
            } else {
                destroyPackets(player);
            }
        }
    }

    private void showPackets(Player player) {
        for (PacketStand stand : stands) {
            stand.spawn(player);
        }
    }

    private void destroyPackets(Player player) {
        for (PacketStand stand : stands) {
            stand.destroy(player);
        }
    }

    public void showTo(Player player) {
        boolean visible = isVisibleTo(player);
        visibility.put(player.getUniqueId(), true);
        if (!visible) showPackets(player);
    }

    public void hideTo(Player player) {
        boolean visible = isVisibleTo(player);
        visibility.put(player.getUniqueId(), false);
        if (visible) destroyPackets(player);
    }

    public boolean isVisibleTo(@NotNull Player player) {
        return isVisibleTo(player, visibleByDefault);
    }

    public boolean isVisibleTo(@NotNull Player player, boolean visibleByDefault) {
        Boolean shown = visibility.get(player.getUniqueId());
        return visibleByDefault ? (shown == null || shown) : (shown != null && shown);
    }

    public void update(List<String> lines) {
        for (int i = 0; i < stands.size(); i++) {
            String name = "line-" + (i + 1);
            PacketStand stand = getByName(name);
            if (stand != null && i > lines.size() - 1) stand.destroy();
        }

        Location current = location.clone().add(0.0d, (LINE_DISTANCE * lines.size()) - 1.97d, 0.0d);

        World world = location.getWorld();
        if (world == null) return;

        HashSet<Player> temp = new HashSet<>(world.getPlayers());
        temp.removeIf(player -> !isVisibleTo(player) || !game.getSeeingPlayers().contains(player));
        if (temp.isEmpty()) return;

        for (int i = 0; i < lines.size(); i++) {
            String name = "line-" + (i + 1);
            String text = PluginUtils.translate(lines.get(i));

            if (i >= stands.size()) {
                // Create a new one.
                StandSettings settings = new StandSettings();
                settings.setPartName(name);
                settings.setCustomName(text);
                settings.setCustomNameVisible(true);
                settings.setInvisible(true);

                // Create packet stand, but don't show to all players.
                PacketStand stand = new PacketStand(plugin, current.clone(), settings);

                // Spawn packet stand to players who can see this hologram.
                for (Player player : temp) {
                    if (isVisibleTo(player)) stand.spawn(player);
                }

                stands.add(stand);
            } else {
                // Update.
                PacketStand stand = getByName(name);
                if (stand == null) continue;

                stand.teleport(temp, current.clone());

                stand.getSettings().setCustomName(text);
                stand.sendMetadata(temp);
            }

            current.subtract(0.0d, LINE_DISTANCE, 0.0d);
        }
    }

    public @Nullable PacketStand getByName(String name) {
        for (PacketStand stand : stands) {
            String partName = stand.getSettings().getPartName();
            if (partName != null && partName.equals(name)) return stand;
        }
        return null;
    }

    public void addLines(String... texts) {
        // Cancel task before updating.
        cancelTask();

        // Add lines to the list.
        lines.addAll(Arrays.asList(texts));

        // Check if the task should start, otherwise, update normally.
        checkForTask();
    }

    public void setLine(int index, String text) {
        // Cancel task before updating.
        cancelTask();

        // Update line in the list.
        lines.set(index, text);

        // Check if the task should start, otherwise, update normally.
        checkForTask();
    }

    public void teleport(Location location) {
        this.location = location;
        update(lines);
    }

    public void destroy() {
        cancelTask();

        stands.forEach(PacketStand::destroy);
        stands.clear();

        lines.clear();
        visibility.clear();
    }

    public int size() {
        return lines.size();
    }

    private void checkForTask() {
        // The plugin is disabled.
        if (!plugin.isEnabled()) return;

        // Update normally.
        if (lines.stream().noneMatch(line -> line.contains("&u"))) {
            update(lines);
            return;
        }

        // Start task.
        taskId = new BukkitRunnable() {
            private int index;

            @Override
            public void run() {
                String result = RAINBOW[index];

                if (++index == RAINBOW.length) index = 0;

                List<String> copy = new ArrayList<>(lines);
                copy.replaceAll(line -> line.replace("&u", result));

                update(copy);
            }
        }.runTaskTimerAsynchronously(plugin, 5L, 5L).getTaskId();
    }

    private void cancelTask() {
        if (taskId == -1) return;

        Bukkit.getScheduler().cancelTask(taskId);
        taskId = -1;
    }
}