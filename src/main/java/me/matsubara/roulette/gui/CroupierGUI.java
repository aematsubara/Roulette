package me.matsubara.roulette.gui;

import lombok.Getter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.manager.ConfigManager;
import me.matsubara.roulette.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Getter
public class CroupierGUI implements RouletteGUI {

    // The game related to this GUI.
    private final Game game;

    // The inventory being used.
    private final Inventory inventory;

    public CroupierGUI(@NotNull Game game, @NotNull Player player) {
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

        inventory.setItem(13, croupierTexture.build());
        inventory.setItem(16, createParrotItem(plugin));

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

    public ItemStack createParrotItem(@NotNull RoulettePlugin plugin) {
        String state = game.isParrotEnabled() ? ConfigManager.Config.STATE_ENABLED.asString() : ConfigManager.Config.STATE_DISABLED.asString();
        return plugin.getItem("croupier-menu.parrot")
                .replace("%state%", state)
                .build();
    }
}