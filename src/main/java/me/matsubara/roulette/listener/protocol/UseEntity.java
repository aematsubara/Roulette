package me.matsubara.roulette.listener.protocol;

import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlayReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.event.PlayerRouletteEnterEvent;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.manager.MessageManager;
import me.matsubara.roulette.model.Model;
import me.matsubara.roulette.model.stand.PacketStand;
import me.matsubara.roulette.util.PluginUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
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
        if (stand.getEntityId() != entityId) return false;

        Model model = game.getModel();
        MessageManager messages = plugin.getMessageManager();

        // Can happen when the game is created.
        if (!model.isModelSpawned()) {
            messages.send(player, MessageManager.Message.MODEL_NOT_LOADED);
            return true;
        }

        // Change table texture.
        if (canChangeTexture(player)) {
            Material material = player.getInventory().getItemInMainHand().getType();

            boolean isPlanks = material.name().contains("PLANKS");

            // No need to change a plank type if is the same.
            if (isPlanks && material == model.getPlanksType()) return true;

            String toUse = material.name().substring(0, material.name().lastIndexOf("_"));

            if (isPlanks) {
                Material slab = PluginUtils.getOrNull(Material.class, toUse + "_SLAB"); // Can't be null.
                model.setPlanksType(material);
                model.setSlabsType(slab);
            } else {
                Material carpet = PluginUtils.getOrNull(Material.class, toUse + "_CARPET"); // Again, can't be null.
                model.setCarpetsType(carpet);
            }

            ItemStack carpets = new ItemStack(model.getCarpetsType());
            ItemStack planks = new ItemStack(model.getPlanksType());
            ItemStack slabs = new ItemStack(model.getSlabsType());

            model.getStands().forEach(part -> {
                String name = part.getSettings().getPartName();
                if (isPlanks) {
                    if (name.startsWith("SIDE")) {
                        part.setEquipment(slabs, PacketStand.ItemSlot.HEAD);
                    }

                    if (name.startsWith("FEET")) {
                        part.setEquipment(planks, PacketStand.ItemSlot.HEAD);
                    }
                }

                if (name.startsWith("CHAIR")) {
                    int current = Integer.parseInt(name.split("_")[1]);
                    if (ArrayUtils.contains(Model.CHAIR_FIRST_LAYER, current)) {
                        part.setEquipment(planks, PacketStand.ItemSlot.HEAD);
                    } else if (ArrayUtils.contains(Model.CHAIR_SECOND_LAYER, current)) {
                        part.setEquipment(slabs, PacketStand.ItemSlot.HEAD);
                    } else {
                        part.setEquipment(carpets, PacketStand.ItemSlot.HEAD);
                    }
                }
            });

            plugin.getGameManager().save(game);
            return true;
        }

        // Joining while shifting will remove the player from the chair.
        if (player.isSneaking()) return true;

        // Can happen when the player is already in the game.
        if (game.isPlaying(player) && game.isSittingOn(player)) {
            messages.send(player, MessageManager.Message.ALREADY_INGAME);
            return true;
        }

        // Can happen whe the game is already started.
        if (!game.canJoin()) {
            if (game.getState().isEnding()) {
                messages.send(player, MessageManager.Message.RESTARTING);
            } else {
                messages.send(player, MessageManager.Message.ALREADY_STARTED);
            }
            return true;
        }

        // Can happen when the game is full.
        if (game.isFull()) {
            messages.send(player, MessageManager.Message.FULL);
            return true;
        }

        // Check if player is vanished using EssentialsX, SuperVanish, PremiumVanish, VanishNoPacket, etc.
        if (isPluginVanished(player)) {
            messages.send(player, MessageManager.Message.VANISH);
            return true;
        }

        // Can happen if the player doens't has money.
        double minAmount = plugin.getChipManager().getMinAmount();
        if (!plugin.getEconomy().has(player, minAmount)) {
            messages.send(player, MessageManager.Message.MIN_REQUIRED, message -> message.replace("%money%", String.valueOf(minAmount)));
            return true;
        }

        // We need to do this part sync to prevent issues.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            PlayerRouletteEnterEvent enterEvent = new PlayerRouletteEnterEvent(game, player);
            plugin.getServer().getPluginManager().callEvent(enterEvent);
            if (enterEvent.isCancelled()) return;

            // If the entities are unloaded, then the chairs won't be valid after a while.
            game.validateChairs();

            Integer sitAt = handleChairInteract(game, player, stand.getSettings().getPartName());
            if (sitAt == null) return;

            game.add(player, sitAt);
            game.broadcast(MessageManager.Message.JOIN.asString()
                    .replace("%player%", player.getName())
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
            plugin.getMessageManager().send(player, MessageManager.Message.SEAT_TAKEN);
            return null;
        }

        return which;
    }

    private boolean canChangeTexture(@NotNull Player player) {
        String inHand = player.getInventory().getItemInMainHand().getType().name();
        boolean validMaterial = inHand.contains("PLANKS") || inHand.contains("WOOL");
        return player.isSneaking() && validMaterial && player.hasPermission("roulette.texturetable");
    }

    private boolean isPluginVanished(@NotNull Player player) {
        Iterator<MetadataValue> iterator = player.getMetadata("vanished").iterator();

        MetadataValue meta;
        do {
            if (!iterator.hasNext()) {
                return false;
            }

            meta = iterator.next();
        } while (!meta.asBoolean());

        boolean vanished = plugin.getEssXExtension().isVanished(player);
        if (vanished) return true;

        // Check if player is vanished using EssentialsX.
        if (!plugin.hasDependency("Essentials")) return false;

        com.earth2me.essentials.Essentials essentials = (com.earth2me.essentials.Essentials) plugin.getServer().getPluginManager().getPlugin("Essentials");
        if (essentials == null) return false;

        com.earth2me.essentials.User user = essentials.getUser(player);
        if (user != null && user.isVanished()) {
            plugin.getMessageManager().send(player, MessageManager.Message.VANISH);
            return true;
        }

        return false;
    }
}