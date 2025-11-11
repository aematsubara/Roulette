package me.matsubara.roulette.gui;

import lombok.Getter;
import me.matsubara.roulette.file.Config;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.data.CustomizationGroup;
import me.matsubara.roulette.model.Model;
import me.matsubara.roulette.util.ItemBuilder;
import me.matsubara.roulette.util.PluginUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Stream;

@Getter
public class TableGUI extends RouletteGUI {

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
        super(game.getPlugin(), "table-menu");
        this.game = game;
        this.inventory = Bukkit.createInventory(this, 27, Config.TABLE_MENU_TITLE.asStringTranslated());

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

    @Override
    public void handle(@NotNull InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        if (current == null) return;

        ItemMeta meta = current.getItemMeta();
        if (meta == null) return;

        Model model = game.getModel();

        ClickType click = event.getClick();
        boolean left = click.isLeftClick(), right = click.isRightClick();
        if (!left && !right) return;

        Model.CustomizationChange change;
        if (isCustomItem(current, "texture")) {
            change = Model.CustomizationChange.TABLE;
            CustomizationGroup texture = PluginUtils.getNextOrPrevious(CustomizationGroup.GROUPS,
                    CustomizationGroup.GROUPS.indexOf(model.getTexture()),
                    right);
            model.setTexture(texture);
            setTextureItem();
        } else if (isCustomItem(current, "chair")) {
            change = Model.CustomizationChange.CHAIR_CARPET;
            Material newCarpet = PluginUtils.getNextOrPrevious(TableGUI.VALID_CARPETS,
                    model.getCarpetsType(),
                    right);
            model.setCarpetsType(newCarpet);
            setChairItem();
        } else if (isCustomItem(current, "decoration")) {
            change = Model.CustomizationChange.DECO;
            String[] pattern = PluginUtils.getNextOrPrevious(Model.PATTERNS,
                    model.getPatternIndex(),
                    right);
            model.setPatternIndex(ArrayUtils.indexOf(Model.PATTERNS, pattern));
            setDecorationItem();
        } else return;

        model.updateModel(game.getSeeingPlayers(), change);
        plugin.getGameManager().save(game);
    }
}