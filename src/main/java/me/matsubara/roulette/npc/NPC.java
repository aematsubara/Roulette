package me.matsubara.roulette.npc;

import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTInt;
import com.github.retrooper.packetevents.protocol.nbt.NBTIntArray;
import com.github.retrooper.packetevents.protocol.nbt.NBTString;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.google.common.base.Preconditions;
import lombok.Getter;
import lombok.Setter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.npc.modifier.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class NPC {

    private final Set<Player> seeingPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> insideFOVPlayers = ConcurrentHashMap.newKeySet();
    private final int entityId;
    private final UserProfile profile;
    private final SpawnCustomizer spawnCustomizer;
    private @Setter Location location;
    private final Game game;

    public NPC(UserProfile profile, SpawnCustomizer spawnCustomizer, @NotNull Location location, int entityId, Game game) {
        World world = location.getWorld();
        if (world != null) insideFOVPlayers.addAll(world.getPlayers().stream()
                .map(Player::getUniqueId)
                .toList());
        this.entityId = entityId;
        this.spawnCustomizer = spawnCustomizer;
        this.location = location;
        this.profile = profile;
        this.game = game;
    }

    @Contract(" -> new")
    public static @NotNull Builder builder() {
        return new Builder();
    }

    public void show(Player player, RoulettePlugin plugin) {
        seeingPlayers.add(player);

        VisibilityModifier modifier = visibility();
        modifier.queuePlayerListChange(false).send(player);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            modifier.queueSpawn().send(player);
            spawnCustomizer.handleSpawn(this, player);

            // Keeping the NPC longer in the player list, otherwise the skin might not be shown sometimes.
            Bukkit.getScheduler().runTaskLater(
                    plugin,
                    () -> modifier.queuePlayerListChange(true).send(player),
                    40);
        }, 10L);
    }

    public void hide(Player player) {
        visibility()
                .queuePlayerListChange(true)
                .queueDestroy()
                .send(player);
        removeSeeingPlayer(player);
    }

    protected void removeSeeingPlayer(Player player) {
        seeingPlayers.remove(player);
        removeFOV(player);
    }

    public void lookAtDefaultLocation(Player... players) {
        rotation().queueBodyRotation(location.getYaw(), location.getPitch()).send(players);
    }

    public boolean isShownFor(Player player) {
        return seeingPlayers.contains(player);
    }

    public void removeFOV(@NotNull Player player) {
        if (insideFOVPlayers.remove(player.getUniqueId())) {
            lookAtDefaultLocation(player);
        }
    }

    public boolean isInsideFOV(@NotNull Player player) {
        return insideFOVPlayers.contains(player.getUniqueId());
    }

    public void setInsideFOV(@NotNull Player player) {
        insideFOVPlayers.add(player.getUniqueId());
    }

    public AnimationModifier animation() {
        return new AnimationModifier(this);
    }

    public RotationModifier rotation() {
        return new RotationModifier(this);
    }

    public EquipmentModifier equipment() {
        return new EquipmentModifier(this);
    }

    public MetadataModifier metadata() {
        return new MetadataModifier(this);
    }

    public VisibilityModifier visibility() {
        return new VisibilityModifier(this);
    }

    public TeleportModifier teleport() {
        return new TeleportModifier(this);
    }

    public void toggleParrotVisibility(@NotNull MetadataModifier metadata) {
        boolean left = game.getParrotShoulder().isLeft();

        NBTCompound parrot = new NBTCompound();
        if (game.isParrotEnabled()) {
            parrot.setTag("id", new NBTString(EntityType.PARROT.getKey().toString()));
            parrot.setTag("UUID", new NBTIntArray(randomUUID()));
            parrot.setTag("Variant", new NBTInt(game.getParrotVariant().ordinal()));
        }

        metadata.queue(left ?
                MetadataModifier.EntityMetadata.SHOULDER_ENTITY_LEFT :
                MetadataModifier.EntityMetadata.SHOULDER_ENTITY_RIGHT, parrot);
    }

    private int[] randomUUID() {
        UUID uuid = UUID.randomUUID();

        long most = uuid.getMostSignificantBits();
        long least = uuid.getLeastSignificantBits();

        int[] array = new int[4];
        array[0] = (int) (most >>> 32);
        array[1] = (int) most;
        array[2] = (int) (least >>> 32);
        array[3] = (int) least;

        return array;
    }

    public static class Builder {

        private UserProfile profile;
        private int entityId = -1;
        private Location location;
        private Game game;

        private SpawnCustomizer spawnCustomizer = (npc, player) -> {
        };

        private Builder() {
        }

        public Builder profile(UserProfile profile) {
            this.profile = profile;
            return this;
        }

        public Builder spawnCustomizer(SpawnCustomizer spawnCustomizer) {
            this.spawnCustomizer = Preconditions.checkNotNull(spawnCustomizer, "spawnCustomizer");
            return this;
        }

        public Builder entityId(int entityId) {
            this.entityId = entityId;
            return this;
        }

        public Builder location(Location location) {
            this.location = location;
            return this;
        }

        public Builder game(Game game) {
            this.game = game;
            return this;
        }

        @NotNull
        public NPC build(NPCPool pool) {
            if (entityId == -1) {
                throw new IllegalArgumentException("No entity id given!");
            }

            if (profile == null) {
                throw new IllegalArgumentException("No profile given!");
            }

            if (location == null) {
                throw new IllegalArgumentException("No location given!");
            }

            NPC npc = new NPC(profile, spawnCustomizer, location, entityId, game);
            pool.takeCareOf(npc);
            return npc;
        }
    }

    @Getter
    public enum NPCAction {
        LOOK(true, false),
        INVITE(false, true),
        LOOK_AND_INVITE(true, true),
        NONE(false, false);

        private final boolean look, invite;

        NPCAction(boolean look, boolean invite) {
            this.look = look;
            this.invite = invite;
        }
    }
}