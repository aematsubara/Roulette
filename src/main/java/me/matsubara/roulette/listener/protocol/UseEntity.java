package me.matsubara.roulette.listener.protocol;

import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.event.PlayerRouletteEnterEvent;
import me.matsubara.roulette.file.Messages;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.hook.EssXExtension;
import me.matsubara.roulette.model.Model;
import me.matsubara.roulette.model.stand.PacketStand;
import org.apache.commons.lang3.ArrayUtils;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

public final class UseEntity extends SimplePacketListenerAbstract {

    private final RoulettePlugin plugin;

    public UseEntity(RoulettePlugin plugin) {
        super(PacketListenerPriority.HIGHEST);
        this.plugin = plugin;
    }

    @Override
    public void onPacketPlayReceive(@NotNull PacketPlayReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        WrapperPlayClientInteractEntity wrapper = new WrapperPlayClientInteractEntity(event);

        // We only want to handle interactions IF the player right-clicks any armor stand.
        WrapperPlayClientInteractEntity.InteractAction action = wrapper.getAction();
        if (action != WrapperPlayClientInteractEntity.InteractAction.INTERACT_AT) return;

        int entityId = wrapper.getEntityId();

        for (Game game : plugin.getGameManager().getGames()) {
            handleInteract(game, player, entityId, game.getModel().getStands(), game.getJoinHologram().getStands());
        }
    }

    @SafeVarargs
    private void handleInteract(Game game, Player player, int entityId, List<PacketStand>... collections) {
        stands:
        for (List<PacketStand> collection : collections) {
            for (PacketStand stand : collection) {
                if (handle(game, player, entityId, stand)) break stands;
            }
        }
    }

    private boolean handle(Game game, Player player, int entityId, @NotNull PacketStand stand) {
        if (stand.getId() != entityId) return false;

        Messages messages = plugin.getMessages();

        // Joining while shifting will remove the player from the chair.
        if (player.isSneaking()) return true;

        // If there is no economy provider then we won't be able to play.
        if (!plugin.getEconomyExtension().isEnabled()) {
            messages.send(player, Messages.Message.NO_ECONOMY_PROVIDER);
            return true;
        }

        // Can happen when the player is already in the game.
        if (game.isPlaying(player) && game.isSittingOn(player)) {
            messages.send(player, Messages.Message.ALREADY_INGAME);
            return true;
        }

        // Can happen whe the game is already started.
        if (!game.canJoin()) {
            if (game.getState().isEnding()) {
                messages.send(player, Messages.Message.RESTARTING);
            } else {
                messages.send(player, Messages.Message.ALREADY_STARTED);
            }
            return true;
        }

        // Can happen when the game is full.
        if (game.isFull()) {
            messages.send(player, Messages.Message.FULL);
            return true;
        }

        // Check if player is vanished using EssentialsX, SuperVanish, PremiumVanish, VanishNoPacket, etc.
        if (isPluginVanished(player)) {
            messages.send(player, Messages.Message.VANISH);
            return true;
        }

        // The player doesn't have the minimum amount of money required to play.
        if (!plugin.getChipManager().hasEnoughMoney(game, player)) return true;

        // We need to do this part sync to prevent issues.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            PlayerRouletteEnterEvent enterEvent = new PlayerRouletteEnterEvent(game, player);
            plugin.getServer().getPluginManager().callEvent(enterEvent);
            if (enterEvent.isCancelled()) return;

            // If the entities are unloaded, then the chairs won't be valid after a while.
            game.handleChairs();

            // handleChairInteract() will return null ONLY if the seat is taken.
            Integer sitAt = handleChairInteract(game, player, stand.getSettings().getPartName());
            if (sitAt == null) return;

            // Try to get the closest chair to the player.
            if (sitAt == -1) {
                sitAt = game.getChairs().entrySet().stream()
                        .filter(entry -> entry.getValue().getPassengers().isEmpty()) // Get empty chairs.
                        .min(Comparator.comparingDouble(entry -> entry.getValue().getLocation().distanceSquared(player.getLocation()))) // Get the closest.
                        .map(entry -> Integer.parseInt(entry.getKey().split("_")[1])) // Convert to id.
                        .orElse(-1);
            }

            game.add(player, sitAt);
            game.broadcast(Messages.Message.JOIN, line -> line
                    .replace("%player-name%", player.getName())
                    .replace("%playing%", String.valueOf(game.size()))
                    .replace("%max%", String.valueOf(game.getMaxPlayers())));
        });

        return true;
    }

    private @Nullable Integer handleChairInteract(Game game, Player player, @NotNull String name) {
        if (!name.startsWith("CHAIR")) return -1;

        int which = Integer.parseInt(name.split("_")[1]);
        if (!ArrayUtils.contains(Model.CHAIR_SECOND_LAYER, which)) return -1;

        ArmorStand chair = game.getChair(which);
        if (chair == null) return -1;

        if (!chair.getPassengers().isEmpty()) {
            plugin.getMessages().send(player, Messages.Message.SEAT_TAKEN);
            return null;
        }

        return which;
    }

    private boolean isPluginVanished(@NotNull Player player) {
        // SuperVanish, PremiumVanish, EssentialsX, VanishNoPacket and many more vanish plugins.
        for (MetadataValue meta : player.getMetadata("vanished")) {
            if (meta.asBoolean()) return true;
        }

        EssXExtension extension = plugin.getEssXExtension();
        return extension != null && extension.isVanished(player);
    }
}