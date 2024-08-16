package me.matsubara.roulette.gui;

import com.google.common.collect.ImmutableMap;
import lombok.Getter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.manager.ConfigManager;
import me.matsubara.roulette.util.ItemBuilder;
import me.matsubara.roulette.util.ParrotUtils;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Material;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.Function;

@Getter
public class CroupierGUI implements RouletteGUI {

    // Instance of the plugin.
    private final RoulettePlugin plugin;

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

    public CroupierGUI(@NotNull Game game, @NotNull Player player) {
        this.plugin = game.getPlugin();
        this.game = game;

        RoulettePlugin plugin = game.getPlugin();

        String npcName = game.getNPCName(), finalName = npcName != null ? npcName : ConfigManager.Config.UNNAMED_CROUPIER.asString();
        this.inventory = plugin.getServer().createInventory(
                this,
                27,
                ConfigManager.Config.CROUPIER_MENU_TITLE.asString().replace("%croupier-name%", finalName));

        inventory.setItem(10, plugin.getItem("croupier-menu.croupier-name")
                .replace("%croupier-name%", finalName)
                .build());

        ItemBuilder croupierTexture = plugin.getItem("croupier-menu.croupier-texture")
                .setType(Material.PLAYER_HEAD);

        String url = game.getNpcTextureAsURL();
        if (url != null) croupierTexture.setHead(url, true);

        inventory.setItem(11, croupierTexture.build());
        setParrotItem();
        setParrotSoundsItem();
        setParrotVariantItem();
        setParrotShoulderItem();

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

    public void setParrotItem() {
        inventory.setItem(13, plugin.getItem("croupier-menu.parrot")
                .replace("%state%", getState(Game::isParrotEnabled))
                .build());
    }

    public void setParrotSoundsItem() {
        inventory.setItem(15, plugin.getItem("croupier-menu.parrot-sounds")
                .replace("%state%", getState(Game::isParrotSounds))
                .build());
    }

    public void setParrotVariantItem() {
        Parrot.Variant variant = game.getParrotVariant();

        String tempName = variant.name().toLowerCase();
        String variantName = plugin.getConfig().getString("variable-text.parrot-variant." + tempName, StringUtils.capitalize(tempName));

        inventory.setItem(14, plugin.getItem("croupier-menu.parrot-variant")
                .setType(Material.PLAYER_HEAD)
                .setHead(PARROT_SKIN.get(variant), true)
                .replace("%variant%", variantName)
                .build());
    }

    public void setParrotShoulderItem() {
        ParrotUtils.ParrotShoulder shoulder = game.getParrotShoulder();

        String tempName = shoulder.name().toLowerCase();
        String shoulderName = plugin.getConfig().getString("variable-text.parrot-shoulder." + tempName, StringUtils.capitalize(tempName));

        inventory.setItem(16, plugin.getItem("croupier-menu.parrot-shoulder")
                .setType(Material.PLAYER_HEAD)
                .setHead(PARROT_SHOULDER.get(shoulder), true)
                .replace("%shoulder%", shoulderName)
                .build());
    }

    private String getState(@NotNull Function<Game, Boolean> function) {
        return function.apply(game) ? ConfigManager.Config.STATE_ENABLED.asString() : ConfigManager.Config.STATE_DISABLED.asString();
    }
}