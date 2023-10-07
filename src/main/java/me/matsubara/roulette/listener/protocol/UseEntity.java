package me.matsubara.roulette.listener.protocol;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.cryptomorin.xseries.ReflectionUtils;
import com.cryptomorin.xseries.XMaterial;
import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.event.PlayerRouletteEnterEvent;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.manager.MessageManager;
import me.matsubara.roulette.model.Model;
import me.matsubara.roulette.model.stand.PacketStand;
import org.apache.commons.lang3.ArrayUtils;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;

public final class UseEntity extends PacketAdapter {

    private final RoulettePlugin plugin;

    public UseEntity(RoulettePlugin plugin) {
        super(plugin, ListenerPriority.HIGHEST, PacketType.Play.Client.USE_ENTITY);
        this.plugin = plugin;
    }

    @Override
    public void onPacketReceiving(@NotNull PacketEvent event) {
        PacketContainer packet = event.getPacket();
        int entityId = packet.getIntegers().readSafely(0);

        EnumWrappers.EntityUseAction action;
        if (ReflectionUtils.MINOR_NUMBER > 16) {
            action = packet.getEnumEntityUseActions().readSafely(0).getAction();
        } else {
            action = packet.getEntityUseActions().readSafely(0);
        }

        if (action == EnumWrappers.EntityUseAction.ATTACK) return;

        Player player = event.getPlayer();

        for (Game game : plugin.getGameManager().getGames()) {
            handleInteract(game, player, entityId, game.getModel().getStands(), game.getJoinHologram().getStands());
        }
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @SafeVarargs
    private void handleInteract(Game game, Player player, int entityId, Map<String, PacketStand> @NotNull ... collections) {
        MessageManager messages = plugin.getMessageManager();
        for (Map<String, PacketStand> collection : collections) {
            for (Map.Entry<String, PacketStand> entry : collection.entrySet()) {
                PacketStand stand = entry.getValue();
                if (stand == null || stand.getEntityId() != entityId) continue;

                Model model = game.getModel();

                // Can happen when the game is created.
                if (!model.isModelSpawned()) {
                    messages.send(player, MessageManager.Message.MODEL_NOT_LOADED);
                    return;
                }

                // Change table texture.
                if (canChangeTexture(player)) {
                    XMaterial material = getMaterialInHand(player);

                    boolean isPlanks = material.name().contains("PLANKS");

                    // No need to change planks type if is the same.
                    if (isPlanks && material == model.getPlanksType()) return;

                    String toUse = material.name().substring(0, material.name().lastIndexOf("_"));

                    if (isPlanks) {
                        XMaterial slab = XMaterial.matchXMaterial(toUse + "_SLAB").get(); // Can't be null.
                        model.setPlanksType(material);
                        model.setSlabsType(slab);
                    } else {
                        XMaterial carpet = XMaterial.matchXMaterial(toUse + "_CARPET").get(); // Again, can't be null.
                        model.setCarpetsType(carpet);
                    }

                    ItemStack carpets = model.getCarpetsType().parseItem();
                    ItemStack planks = model.getPlanksType().parseItem();
                    ItemStack slabs = model.getSlabsType().parseItem();

                    model.getStands().forEach((name, part) -> {
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
                    return;
                }

                // Joining while shifting will remove the player from the chair.
                if (player.isSneaking()) return;

                // Can happen when the player is already in the game.
                if (game.isPlaying(player) && game.isSittingOn(player)) {
                    messages.send(player, MessageManager.Message.ALREADY_INGAME);
                    return;
                }

                // Can happen whe the game is already started.
                if (!game.canJoin()) {
                    if (game.getState().isEnding()) {
                        messages.send(player, MessageManager.Message.RESTARTING);
                    } else {
                        messages.send(player, MessageManager.Message.ALREADY_STARTED);
                    }
                    return;
                }

                // Can happen when the game is full.
                if (game.isFull()) {
                    messages.send(player, MessageManager.Message.FULL);
                    return;
                }

                // Check if player is vanished using EssentialsX, SuperVanish, PremiumVanish, VanishNoPacket, etc.
                if (isPluginVanished(player)) {
                    messages.send(player, MessageManager.Message.VANISH);
                    return;
                }

                // Can happen if the player doens't have money.
                double minAmount = plugin.getChipManager().getMinAmount();
                if (!plugin.getEconomy().has(player, minAmount)) {
                    messages.send(player, MessageManager.Message.MIN_REQUIRED, message -> message.replace("%money%", String.valueOf(minAmount)));
                    return;
                }

                // We need to do this part sync to prevent issues.
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    PlayerRouletteEnterEvent enterEvent = new PlayerRouletteEnterEvent(game, player);
                    plugin.getServer().getPluginManager().callEvent(enterEvent);
                    if (enterEvent.isCancelled()) return;

                    Integer sitAt = handleChairInteract(game, player, entry.getKey());
                    if (sitAt == null) return;

                    game.add(player, sitAt);
                    game.broadcast(MessageManager.Message.JOIN.asString()
                            .replace("%player%", player.getName())
                            .replace("%playing%", String.valueOf(game.size()))
                            .replace("%max%", String.valueOf(game.getMaxPlayers())));
                });
                return;
            }
        }
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

    private XMaterial getMaterialInHand(@NotNull Player player) {
        XMaterial material;
        try {
            material = XMaterial.matchXMaterial(player.getInventory().getItemInMainHand());
        } catch (IllegalArgumentException exception) {
            material = XMaterial.AIR;
        }
        return material;
    }

    private boolean canChangeTexture(Player player) {
        String inHand = getMaterialInHand(player).name();
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

        Essentials essentials = (Essentials) plugin.getServer().getPluginManager().getPlugin("Essentials");
        if (essentials == null) return false;

        User user = essentials.getUser(player);
        if (user != null && user.isVanished()) {
            plugin.getMessageManager().send(player, MessageManager.Message.VANISH);
            return true;
        }

        return false;
    }
}