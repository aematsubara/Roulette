package me.matsubara.roulette.gui;

import lombok.Getter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.data.CustomizationGroup;
import me.matsubara.roulette.manager.ConfigManager;
import me.matsubara.roulette.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Stream;

@Getter
public class TableGUI extends RouletteGUI {

    // Instance of the plugin.
    private final RoulettePlugin plugin;

    // The game that is being edited.
    private final Game game;

    // Inventoy being used.
    private final Inventory inventory;

    // Valid carpet materials.
    @SuppressWarnings("deprecation")
    public static final Material[] VALID_CARPETS = Stream.of(Material.values())
            .filter(Tag.CARPETS::isTagged)
            .toArray(Material[]::new);

    public TableGUI(@NotNull Game game, @NotNull Player player) {
        super("table-menu");
        this.plugin = game.getPlugin();
        this.game = game;
        this.inventory = Bukkit.createInventory(this, 27, ConfigManager.Config.TABLE_MENU_TITLE.asString());

        // Fill inventory.
        fillInventory();

        // Open inventory.
        player.openInventory(inventory);
    }

    private void fillInventory() {
        ItemStack background = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .setDisplayName("&7")
                .build();

        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, background);
        }

        setTextureItem(); // 10
        setChairItem(); // 13
        setDecorationItem(); // 16
    }

    public void setTextureItem() {
        CustomizationGroup texture = game.getModel().getTexture();
        inventory.setItem(10, getItem("texture")
                .setType(texture.block())
                .build());
    }

    public void setChairItem() {
        inventory.setItem(13, getItem("chair")
                .setType(game.getModel().getCarpetsType())
                .build());
    }

    public void setDecorationItem() {
        inventory.setItem(16, getItem("decoration")
                .replace("%decoration%", game.getModel().getPatternIndex() + 1)
                .build());
    }
}