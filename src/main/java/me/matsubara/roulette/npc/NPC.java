package me.matsubara.roulette.npc;

import com.github.retrooper.packetevents.protocol.nbt.NBTNumber;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.google.common.base.Preconditions;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import lombok.Getter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.npc.modifier.*;
import me.matsubara.roulette.util.ParrotUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

@Getter
public class NPC {

    private final Collection<Player> seeingPlayers = new CopyOnWriteArraySet<>();
    private final Collection<UUID> insideFOVPlayers = new CopyOnWriteArraySet<>();
    private final int entityId;
    private final UserProfile profile;
    private final SpawnCustomizer spawnCustomizer;
    private final Location location;
    private final Game game;

    public NPC(UserProfile profile, SpawnCustomizer spawnCustomizer, @NotNull Location location, int entityId, Game game) {
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

    public Collection<Player> getSeeingPlayers() {
        return Collections.unmodifiableCollection(seeingPlayers);
    }

    public boolean isShownFor(Player player) {
        return seeingPlayers.contains(player);
    }

    public void removeFOV(@NotNull Player player) {
        insideFOVPlayers.remove(player.getUniqueId());
        lookAtDefaultLocation(player);
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

    @SuppressWarnings("unused")
    public TeleportModifier teleport() {
        return new TeleportModifier(this);
    }

    public void toggleParrotVisibility(World world, @NotNull MetadataModifier metadata) {
        boolean left = game.getParrotShoulder().isLeft();

        Object parrot = getOrCreateParrot(world);
        metadata.queue(left ?
                MetadataModifier.EntityMetadata.SHOULDER_ENTITY_LEFT :
                MetadataModifier.EntityMetadata.SHOULDER_ENTITY_RIGHT, parrot);
    }

    private @Nullable Object getOrCreateParrot(World world) {
        if (!game.isParrotEnabled()) return ParrotUtils.EMPTY_NBT;

        Object parrotNBT = game.getParrotNBT();
        if (parrotNBT != null) {
            NBTNumber variantId = SpigotReflectionUtil
                    .fromMinecraftNBT(parrotNBT)
                    .getNumberTagOrNull("Variant");
            if (variantId != null && game.getParrotVariant() == Parrot.Variant.values()[variantId.getAsInt()]) {
                return parrotNBT;
            }
        }

        Object temp = ParrotUtils.createParrot(world, game.getParrotVariant());
        if (temp != null) {
            game.setParrotNBT(temp);
            return temp;
        }

        return ParrotUtils.EMPTY_NBT;
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
}