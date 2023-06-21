package me.matsubara.roulette.hologram;

import lombok.Getter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.model.stand.PacketStand;
import me.matsubara.roulette.model.stand.StandSettings;
import me.matsubara.roulette.util.PluginUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public final class Hologram {

    // Plugin instance.
    private final RoulettePlugin plugin;

    // Text of this hologram.
    private final List<String> lines;

    // Stands of this hologram.
    private final List<PacketStand> stands;

    // Location of this hologram.
    private Location location;

    // Players seeing this hologram.
    private Map<String, Boolean> visibility;

    // If this hologram is visible by default.
    private boolean visibleByDefault;

    // Task used for rainbow color.
    private int taskId;

    // Space between lines.
    private static final double LINE_DISTANCE = 0.23d;

    // Rainbow colors.
    private static final String[] RAINBOW = arrayToStrings(
            ChatColor.RED,
            ChatColor.GOLD,
            ChatColor.YELLOW,
            ChatColor.GREEN,
            ChatColor.AQUA,
            ChatColor.LIGHT_PURPLE);

    public Hologram(RoulettePlugin plugin, Location location) {
        this.plugin = plugin;
        this.lines = new ArrayList<>();
        this.stands = new ArrayList<>();
        this.location = location;
        this.visibleByDefault = true;
    }

    public void setVisibleByDefault(boolean visibleByDefault) {
        if (this.visibleByDefault == visibleByDefault) return;

        boolean oldVisibleByDefault = this.visibleByDefault;
        this.visibleByDefault = visibleByDefault;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (visibility == null) continue;
            if (visibility.containsKey(player.getName())) continue;

            if (oldVisibleByDefault) {
                destroyPackets(player);
            } else {
                showPackets(player);
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
        boolean wasVisible = isVisibleTo(player);

        if (visibility == null) visibility = new ConcurrentHashMap<>();
        visibility.put(player.getName(), true);

        if (!wasVisible) showPackets(player);
    }

    public void hideTo(Player player) {
        boolean wasVisible = isVisibleTo(player);

        if (visibility == null) visibility = new ConcurrentHashMap<>();
        visibility.put(player.getName(), false);

        if (wasVisible) destroyPackets(player);
    }

    public boolean isVisibleTo(Player player) {
        if (visibility != null) {
            Boolean value = visibility.get(player.getName());
            if (value != null) return value;
        }
        return visibleByDefault;
    }

    public void update(List<String> lines) {
        for (int i = 0; i < stands.size(); i++) {
            PacketStand stand = stands.get(i);
            if (i > lines.size() - 1) stand.destroy();
        }

        Location current = location.clone().add(0.0d, (LINE_DISTANCE * lines.size()) - 1.97d, 0.0d);

        for (int i = 0; i < lines.size(); i++) {
            String text = PluginUtils.translate(lines.get(i));

            if (i >= stands.size()) {
                // Create new one.
                StandSettings settings = new StandSettings();
                settings.setCustomName(text);
                settings.setCustomNameVisible(true);
                settings.setInvisible(true);

                // Create packet stand, but don't show to all players.
                PacketStand stand = new PacketStand(current, settings, false);

                // Spawn packet stand to players who can see this hologram.
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (isVisibleTo(player)) stand.spawn(player);
                }

                stands.add(stand);
            } else {
                // Update.
                stands.get(i).teleport(current);
                stands.get(i).setCustomName(text);
                stands.get(i).updateMetadata();
            }

            current.subtract(0.0d, LINE_DISTANCE, 0.0d);
        }

    }

    public void addLines(String... texts) {
        // Cancel task before updating.
        cancelTask();

        // Add lines to list.
        lines.addAll(Arrays.asList(texts));

        // Check if task should start, otherwise, update normally.
        checkForTask();
    }

    public void setLine(int index, String text) {
        // Cancel task before updating.
        cancelTask();

        // Update line in list.
        lines.set(index, text);

        // Check if task should start, otherwise, update normally.
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

        if (visibility != null) {
            visibility.clear();
            visibility = null;
        }
    }

    public int size() {
        return lines.size();
    }

    private void checkForTask() {
        // Check if the task should be started.
        boolean startTask = false;
        for (String line : lines) {
            if (line.contains("&u")) {
                startTask = true;
                break;
            }
        }

        if (!startTask) {
            // Update normally.
            update(lines);
            return;
        }

        // Start task.
        taskId = new BukkitRunnable() {
            private int index;

            @Override
            public void run() {
                String result = RAINBOW[index];

                index++;
                if (index == RAINBOW.length) index = 0;

                List<String> copy = new ArrayList<>(lines);
                copy.replaceAll(line -> line.replace("&u", result));
                update(copy);
            }
        }.runTaskTimer(plugin, 0L, 5L).getTaskId();
    }

    private void cancelTask() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    @SuppressWarnings("SameParameterValue")
    private static String[] arrayToStrings(Object... array) {
        String[] result = new String[array.length];
        for (int i = 0; i < array.length; i++) {
            result[i] = array[i] != null ? array[i].toString() : null;
        }
        return result;
    }
}