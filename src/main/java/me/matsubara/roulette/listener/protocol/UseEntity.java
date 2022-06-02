package me.matsubara.roulette.listener.protocol;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.cryptomorin.xseries.ReflectionUtils;
import com.cryptomorin.xseries.XMaterial;
import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.manager.MessageManager;
import me.matsubara.roulette.model.stand.PacketStand;
import org.apache.commons.lang.ArrayUtils;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;

import java.util.Collection;
import java.util.Iterator;
import java.util.stream.IntStream;

public final class UseEntity extends PacketAdapter {

    private final RoulettePlugin plugin;

    public UseEntity(RoulettePlugin plugin) {
        super(plugin, ListenerPriority.HIGHEST, PacketType.Play.Client.USE_ENTITY);
        this.plugin = plugin;
    }

    @Override
    public void onPacketReceiving(PacketEvent event) {
        int entityId = event.getPacket().getIntegers().readSafely(0);

        EnumWrappers.EntityUseAction action;
        if (ReflectionUtils.VER > 16) {
            action = event.getPacket().getEnumEntityUseActions().readSafely(0).getAction();
        } else {
            action = event.getPacket().getEntityUseActions().readSafely(0);
        }

        boolean isLeft = action == EnumWrappers.EntityUseAction.ATTACK;
        if (isLeft) return;

        Player player = event.getPlayer();

        for (Game game : plugin.getGameManager().getGames()) {
            handleInteract(game, player, entityId, game.getModel().getStands().values(), game.getJoinHologram().getStands());
        }
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @SafeVarargs
    private final void handleInteract(Game game, Player player, int entityId, Collection<PacketStand>... collections) {
        for (Collection<PacketStand> stands : collections) {
            for (PacketStand stand : stands) {
                if (stand == null || stand.getEntityId() != entityId) continue;

                // Can happen when the game is created.
                if (!game.getModel().isModelSpawned()) {
                    plugin.getMessageManager().send(player, MessageManager.Message.MODEL_NOT_LOADED);
                    return;
                }

                // Change table texture.
                if (canChangeTexture(player)) {
                    XMaterial material = getMaterialInHand(player);

                    boolean isPlanks = material.name().contains("PLANKS");

                    // No need to change planks type if is the same.
                    if (isPlanks && material == game.getModel().getPlanksType()) return;

                    String toUse = material.name().substring(0, material.name().lastIndexOf("_"));

                    if (isPlanks) {
                        XMaterial slab = XMaterial.matchXMaterial(toUse + "_SLAB").get(); // Can't be null.
                        game.getModel().setPlanksType(material);
                        game.getModel().setSlabsType(slab);
                    } else {
                        XMaterial carpet = XMaterial.matchXMaterial(toUse + "_CARPET").get(); // Again, can't be null.
                        game.getModel().setCarpetsType(carpet);
                    }

                    ItemStack carpets = game.getModel().getCarpetsType().parseItem();
                    ItemStack planks = game.getModel().getPlanksType().parseItem();
                    ItemStack slabs = game.getModel().getSlabsType().parseItem();

                    game.getModel().getStands().forEach((name, part) -> {
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
                            if (ArrayUtils.contains(IntStream.range(0, 10).map(x -> (x * 3) + 1).toArray(), current)) {
                                part.setEquipment(planks, PacketStand.ItemSlot.HEAD);
                            } else if (ArrayUtils.contains(IntStream.range(0, 10).map(x -> (x * 3) + 2).toArray(), current)) {
                                part.setEquipment(slabs, PacketStand.ItemSlot.HEAD);
                            } else {
                                part.setEquipment(carpets, PacketStand.ItemSlot.HEAD);
                            }
                        }
                    });

                    plugin.getGameManager().save(game);
                    return;
                }

                // Can happen when the player is already in the game.
                if (game.isPlaying(player) && game.isSittingOn(player)) {
                    plugin.getMessageManager().send(player, MessageManager.Message.ALREADY_INGAME);
                    return;
                }

                // Can happen whe the game is already started.
                if (!game.canJoin()) {
                    if (game.getState().isEnding()) {
                        plugin.getMessageManager().send(player, MessageManager.Message.RESTARTING);
                    } else {
                        plugin.getMessageManager().send(player, MessageManager.Message.ALREADY_STARTED);
                    }
                    return;
                }

                // Can happen when the game is full.
                if (game.isFull()) {
                    plugin.getMessageManager().send(player, MessageManager.Message.FULL);
                    return;
                }

                // Check if player is vanished using EssentialsX.
                if (plugin.hasDependency("Essentials")) {
                    Essentials essentials = (Essentials) plugin.getServer().getPluginManager().getPlugin("Essentials");
                    if (essentials != null) {
                        User user = essentials.getUser(player);
                        if (user != null && user.isVanished()) {
                            plugin.getMessageManager().send(player, MessageManager.Message.VANISH);
                            return;
                        }
                    }
                }

                // Check if player is vanished using SuperVanish, PremiumVanish, VanishNoPacket, etc.
                if (isPluginVanished(player)) {
                    plugin.getMessageManager().send(player, MessageManager.Message.VANISH);
                    return;
                }

                // Can happen if the player doens't have money.
                double minAmount = plugin.getChipManager().getMinAmount();
                if (!plugin.getEconomy().has(player, minAmount)) {
                    plugin.getMessageManager().send(player, MessageManager.Message.MIN_REQUIRED, message -> message.replace("%money%", String.valueOf(minAmount)));
                    return;
                }

                game.add(player);
                game.broadcast(MessageManager.Message.JOIN.asString()
                        .replace("%player%", player.getName())
                        .replace("%playing%", String.valueOf(game.size()))
                        .replace("%max%", String.valueOf(game.getMaxPlayers())));
                return;
            }
        }
    }

    private XMaterial getMaterialInHand(Player player) {
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
        return player.isSneaking() && validMaterial && player.hasPermission("roulette.texture_table");
    }

    private boolean isPluginVanished(Player player) {
        Iterator<MetadataValue> iterator = player.getMetadata("vanished").iterator();

        MetadataValue meta;
        do {
            if (!iterator.hasNext()) {
                return false;
            }

            meta = iterator.next();
        } while (!meta.asBoolean());

        return plugin.getEssXExtension().isVanished(player);
    }
}