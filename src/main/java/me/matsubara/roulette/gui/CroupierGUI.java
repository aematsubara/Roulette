package me.matsubara.roulette.gui;

import com.google.common.collect.ImmutableMap;
import lombok.Getter;
import me.matsubara.roulette.file.Config;
import me.matsubara.roulette.file.Messages;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.manager.InputManager;
import me.matsubara.roulette.npc.NPC;
import me.matsubara.roulette.util.ItemBuilder;
import me.matsubara.roulette.util.ParrotUtils;
import me.matsubara.roulette.util.PluginUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

@Getter
public class CroupierGUI extends RouletteGUI {

    // The game related to this GUI.
    private final Game game;

    // The inventory being used.
    private final Inventory inventory;

    // Parrot skins per variant.
    private static final Map<Parrot.Variant, String> PARROT_SKIN = ImmutableMap.of(
            Parrot.Variant.RED, "a4ba8d66fecb1992e94b8687d6ab4a5320ab7594ac194a2615ed4df818edbc3",
            Parrot.Variant.BLUE, "b78e1c5f48a7e12b262853571ef1f597a92ef58da8faafe07bb7c0e69e93",
            Parrot.Variant.GREEN, "484dc14eb1d3024edb1cb6f62367b7b1160a363a2539a228832911815d7715a",
            Parrot.Variant.CYAN, "3066fd2859afff7fde048435380c380bee866e5956b8a3a5ccc5e1cf9df0f2",
            Parrot.Variant.GRAY, "3d6f4a21e0d62af824f8708ac63410f1a01bbb41d7f4a702d9469c6113222");

    private static final Map<ParrotUtils.ParrotShoulder, String> PARROT_SHOULDER = ImmutableMap.of(
            ParrotUtils.ParrotShoulder.LEFT, "90e0a4d48cd829a6d5868909d643fa4affd39e8ae6caaf6ec79609cf7649b1c",
            ParrotUtils.ParrotShoulder.RIGHT, "865426a33df58b465f0601dd8b9bec3690b2193d1f9503c2caab78f6c2438");

    private static final Map<BlockFace, String> ROTATION_URL = ImmutableMap.of(
            BlockFace.NORTH, "3040fe836a6c2fbd2c7a9c8ec6be5174fddf1ac20f55e366156fa5f712e10",
            BlockFace.NORTH_EAST, "90e0a4d48cd829a6d5868909d643fa4affd39e8ae6caaf6ec79609cf7649b1c",
            BlockFace.EAST, "19bf3292e126a105b54eba713aa1b152d541a1d8938829c56364d178ed22bf",
            BlockFace.SOUTH_EAST, "35cbdb28991a16eb2c793474ef7d0f458a5d13fffc283c4d74d929941bb1989",
            BlockFace.SOUTH, "7437346d8bda78d525d19f540a95e4e79daeda795cbc5a13256236312cf",
            BlockFace.SOUTH_WEST, "354ce8157e71dcd5b6b1674ac5bd55490702027c675e5cdceac55d2fbbd5a",
            BlockFace.WEST, "bd69e06e5dadfd84e5f3d1c21063f2553b2fa945ee1d4d7152fdc5425bc12a9",
            BlockFace.NORTH_WEST, "865426a33df58b465f0601dd8b9bec3690b2193d1f9503c2caab78f6c2438");

    public CroupierGUI(@NotNull Game game, @NotNull Player player) {
        super(game.getPlugin(), "croupier-menu");
        this.game = game;

        String npcName = game.getNPCName(), finalName = npcName != null ? npcName : Config.UNNAMED_CROUPIER.asStringTranslated();
        this.inventory = plugin.getServer().createInventory(
                this,
                36,
                Config.CROUPIER_MENU_TITLE.asStringTranslated().replace("%croupier-name%", finalName));

        inventory.setItem(10, getItem("croupier-name")
                .replace("%croupier-name%", finalName)
                .build());

        ItemBuilder croupierTexture = getItem("croupier-texture")
                .setType(Material.PLAYER_HEAD);

        String url = game.getNpcTextureAsURL();
        if (url != null) croupierTexture.setHead(url, true);

        inventory.setItem(11, croupierTexture.build());
        setCroupierActionItem();
        setParrotItem();
        setParrotSoundsItem();
        setParrotVariantItem();
        setParrotShoulderItem();
        setCroupierRotationItem();
        setCroupierDistanceItem();

        ItemStack background = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .setDisplayName("&7")
                .build();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType().isAir()) {
                inventory.setItem(i, background);
            }
        }

        player.openInventory(inventory);
    }

    public void setCroupierActionItem() {
        NPC.NPCAction action = game.getNpcAction();
        inventory.setItem(12, getItem("croupier-action")
                .replace("%state%", getFormattedEnum("action", action))
                .replace("%fov%", getState(Game::isNpcActionFOV))
                .build());
    }

    public void setParrotItem() {
        inventory.setItem(13, getItem("parrot")
                .replace("%state%", getState(Game::isParrotEnabled))
                .build());
    }

    public void setParrotSoundsItem() {
        inventory.setItem(15, getItem("parrot-sounds")
                .replace("%state%", getState(Game::isParrotSounds))
                .build());
    }

    public void setParrotVariantItem() {
        Parrot.Variant variant = game.getParrotVariant();

        String tempName = variant.name().toLowerCase(Locale.ROOT);
        String variantName = plugin.getConfig().getString("variable-text.parrot-variant." + tempName, StringUtils.capitalize(tempName));

        inventory.setItem(14, getItem("parrot-variant")
                .setHead(PARROT_SKIN.get(variant), true)
                .replace("%variant%", variantName)
                .build());
    }

    public void setParrotShoulderItem() {
        ParrotUtils.ParrotShoulder shoulder = game.getParrotShoulder();

        String tempName = shoulder.name().toLowerCase(Locale.ROOT);
        String shoulderName = plugin.getConfig().getString("variable-text.parrot-shoulder." + tempName, StringUtils.capitalize(tempName));

        inventory.setItem(16, getItem("parrot-shoulder")
                .setHead(PARROT_SHOULDER.get(shoulder), true)
                .replace("%shoulder%", shoulderName)
                .build());
    }

    public void setCroupierRotationItem() {
        BlockFace rotation = game.getCurrentNPCFace();
        inventory.setItem(19, getItem("croupier-rotation")
                .setHead(ROTATION_URL.get(rotation), true)
                .replace("%rotation%", getFormattedEnum("face", rotation))
                .build());
    }

    public void setCroupierDistanceItem() {
        inventory.setItem(20, getItem("croupier-distance")
                .replace("%distance%", game.getNpcDistance())
                .build());
    }

    private @NotNull String getState(@NotNull Function<Game, Boolean> function) {
        return (function.apply(game) ? Config.STATE_ENABLED : Config.STATE_DISABLED).asStringTranslated();
    }

    @SuppressWarnings("deprecation")
    private String getFormattedEnum(String parent, @NotNull Enum<?> value) {
        String name = value.name();
        return plugin.getConfig().getString(
                "variable-text." + parent + "." + name.toLowerCase(Locale.ROOT).replace("_", "-"),
                WordUtils.capitalizeFully(name.replace("_", " ")));
    }

    @Override
    public void handle(@NotNull InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        Messages messages = plugin.getMessages();

        ItemStack current = event.getCurrentItem();
        if (current == null) return;

        ClickType click = event.getClick();
        boolean left = click.isLeftClick(), right = click.isRightClick();

        if (isCustomItem(current, "croupier-name")) {
            if (left) {
                // Change name.
                plugin.getInputManager().newInput(player, InputManager.InputType.CROUPIER_NAME, game);
                messages.send(player, Messages.Message.NPC_NAME);
            } else if (right) {
                // Reset name.
                String npcName = game.getNPCName();
                if (npcName != null && !npcName.isEmpty() && !npcName.equals(Config.UNNAMED_CROUPIER.asStringTranslated())) {
                    messages.send(player, Messages.Message.NPC_RENAMED);
                    game.setNPC(null, game.getNPCTexture(), game.getNPCSignature());
                    plugin.getGameManager().save(game);
                } else {
                    messages.send(player, Messages.Message.NPC_ALREADY_RENAMED);
                }
            }
        } else if (isCustomItem(current, "croupier-texture")) {
            if (left) {
                // Change texture.
                plugin.getInputManager().newInput(player, InputManager.InputType.CROUPIER_TEXTURE, game);
                messages.send(player, Messages.Message.NPC_TEXTURE);
            } else if (right) {
                // Reset texture.
                if (game.hasNPCTexture()) {
                    messages.send(player, Messages.Message.NPC_TEXTURIZED);
                    game.setNPC(game.getNPCName(), null, null);
                    plugin.getGameManager().save(game);
                } else {
                    messages.send(player, Messages.Message.NPC_ALREADY_TEXTURIZED);
                }
            }
        } else if (isCustomItem(current, "croupier-action")) {
            if (click == ClickType.MIDDLE) {
                game.handleGameChange(
                        this,
                        temp -> temp.setNpcActionFOV(!temp.isNpcActionFOV()),
                        CroupierGUI::setCroupierActionItem,
                        false);
                return;
            }

            game.handleGameChange(
                    this,
                    temp -> temp.setNpcAction(PluginUtils.getNextOrPreviousEnum(temp.getNpcAction(), right)),
                    CroupierGUI::setCroupierActionItem,
                    false);

            if (game.getNpcAction() != NPC.NPCAction.INVITE) return;

            NPC npc = game.getNpc();
            npc.getSeeingPlayers().forEach(npc::lookAtDefaultLocation);
            return;
        } else if (isCustomItem(current, "parrot")) {
            game.handleGameChange(
                    this,
                    temp -> temp.setParrotEnabled(!temp.isParrotEnabled()),
                    CroupierGUI::setParrotItem,
                    true);
            return;
        } else if (isCustomItem(current, "parrot-sounds")) {
            game.handleGameChange(
                    this,
                    temp -> temp.setParrotSounds(!temp.isParrotSounds()),
                    CroupierGUI::setParrotSoundsItem,
                    false);
            return;
        } else if (isCustomItem(current, "parrot-variant")) {
            game.handleGameChange(
                    this,
                    temp -> temp.setParrotVariant(PluginUtils.getNextOrPreviousEnum(temp.getParrotVariant(), right)),
                    CroupierGUI::setParrotVariantItem,
                    true);
            return;
        } else if (isCustomItem(current, "parrot-shoulder")) {
            game.handleGameChange(
                    this,
                    temp -> temp.setParrotShoulder(PluginUtils.getNextOrPreviousEnum(temp.getParrotShoulder(), right)),
                    CroupierGUI::setParrotShoulderItem,
                    true);

            // Send another metadata packet but for the other shoulder.
            game.getNpc().metadata().queueShoulderEntity(!game.getParrotShoulder().isLeft(), null).send();
            return;
        } else if (isCustomItem(current, "croupier-rotation")) {
            game.handleGameChange(
                    this,
                    temp -> temp.setCurrentNPCFace(PluginUtils.getNextOrPrevious(PluginUtils.RADIAL,
                            temp.getCurrentNPCFace(),
                            right)),
                    CroupierGUI::setCroupierRotationItem,
                    false);

            game.lookAtFace(game.getCurrentNPCFace());
            return;
        } else if (isCustomItem(current, "croupier-distance")) {
            game.setNpcDistance(game.getNpcDistance() + (right ? 5.0d : -5.0d));
            this.setCroupierDistanceItem();
            plugin.getGameManager().save(game);
            return;
        } else return;

        closeInventory(player);
    }
}