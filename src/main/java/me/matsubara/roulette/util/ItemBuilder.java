package me.matsubara.roulette.util;

import com.cryptomorin.xseries.reflection.XReflection;
import org.bukkit.*;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.*;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

@SuppressWarnings({"unused", "UnusedReturnValue"})
public final class ItemBuilder {

    private final ItemStack item;

    private static final MethodHandle SET_BASE_POTION_TYPE = XReflection.supports(20, 6) ?
            Reflection.getMethod(PotionMeta.class, "setBasePotionType", PotionType.class) :
            null;

    private static final MethodHandle SET_MAX_STACK_SIZE = Reflection.getMethod(ItemMeta.class,
            "setMaxStackSize",
            MethodType.methodType(void.class, Integer.class),
            false,
            false);

    public ItemBuilder(@NotNull ItemStack item) {
        this.item = item.clone();
    }

    public ItemBuilder(Material material) {
        this(new ItemStack(material));
    }

    public ItemBuilder setType(Material type) {
        item.setType(type);
        return this;
    }

    public ItemBuilder setHead(String texture, boolean isUrl) {
        return setHead(UUID.randomUUID(), texture, isUrl);
    }

    public ItemBuilder setHead(UUID uuid, String texture, boolean isUrl) {
        if (item.getType() != Material.PLAYER_HEAD) {
            setType(Material.PLAYER_HEAD);
        }

        if (!(item.getItemMeta() instanceof SkullMeta meta)) return this;

        PluginUtils.applySkin(meta, uuid, texture, isUrl);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder setAmount(int amount) {
        changeMaxStackSize();
        item.setAmount(amount);
        return this;
    }

    private void changeMaxStackSize() {
        // Since 1.20.6, unstackable items can only stack up to ItemMeta#getMaxStackSize().
        if (SET_MAX_STACK_SIZE == null) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        try {
            SET_MAX_STACK_SIZE.invoke(meta, 64);
            item.setItemMeta(meta);
        } catch (Throwable ignored) {

        }
    }

    public ItemBuilder setCustomModelData(int data) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return this;

        meta.setCustomModelData(data);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder setDamage(int damage) {
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return this;
        damageable.setDamage(damage);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder setOwningPlayer(UUID uuid) {
        return setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
    }

    public ItemBuilder setOwningPlayer(OfflinePlayer player) {
        if (item.getType() != Material.PLAYER_HEAD) {
            setType(Material.PLAYER_HEAD);
        }

        if (!(item.getItemMeta() instanceof SkullMeta meta)) return this;

        meta.setOwningPlayer(player);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder setDisplayName(String displayName) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return this;

        meta.setDisplayName(PluginUtils.translate(displayName));
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder clearLore() {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return this;

        meta.setLore(null);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder setLore(String... lore) {
        return setLore(Arrays.asList(lore));
    }

    public ItemBuilder setLore(List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return this;

        meta.setLore(PluginUtils.translate(lore));
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder addLore(String... lore) {
        return addLore(Arrays.asList(lore));
    }

    public ItemBuilder addLore(List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return this;

        List<String> actual = meta.getLore();
        if (actual == null) return setLore(lore);

        actual.addAll(lore);
        return setLore(lore);
    }

    public List<String> getLore() {
        ItemMeta meta = item.getItemMeta();
        List<String> lore;
        return meta != null && (lore = meta.getLore()) != null ? lore : Collections.emptyList();
    }

    public ItemBuilder setLeatherArmorMetaColor(Color color) {
        if (!(item.getItemMeta() instanceof LeatherArmorMeta meta)) return this;

        meta.setColor(color);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder addEnchantment(Enchantment enchantment, int level) {
        item.addUnsafeEnchantment(enchantment, level);
        return this;
    }

    public ItemBuilder removeEnchantment(Enchantment enchantment) {
        item.removeEnchantment(enchantment);
        return this;
    }

    public ItemBuilder addItemFlags(ItemFlag... flags) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return this;

        meta.addItemFlags(flags);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder removeItemFlags(ItemFlag... flags) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return this;

        meta.removeItemFlags(flags);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder setBasePotionData(PotionType type) {
        if (!(item.getItemMeta() instanceof PotionMeta meta)) return this;

        if (SET_BASE_POTION_TYPE != null) {
            try {
                SET_BASE_POTION_TYPE.invoke(meta, type);
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        } else {
            meta.setBasePotionData(new org.bukkit.potion.PotionData(type));
        }

        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder addPattern(int colorId, String patternCode) {
        return addPattern(DyeColor.values()[colorId], PatternType.getByIdentifier(patternCode));
    }

    public ItemBuilder addPattern(DyeColor color, PatternType patternType) {
        return addPattern(new Pattern(color, patternType));
    }

    public ItemBuilder addPattern(Pattern pattern) {
        if (!(item.getItemMeta() instanceof BannerMeta meta)) return this;

        meta.addPattern(pattern);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder setBannerColor(DyeColor color) {
        if (!(item.getItemMeta() instanceof BannerMeta meta)) return this;

        meta.addPattern(new Pattern(color, PatternType.BASE));
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder initializeFirework(int power, FireworkEffect... effects) {
        if (!(item.getItemMeta() instanceof FireworkMeta meta)) return this;

        meta.setPower(power);
        meta.addEffects(effects);
        item.setItemMeta(meta);
        return this;
    }

    public <T, Z> ItemBuilder setData(NamespacedKey key, PersistentDataType<T, Z> type, @NotNull Z value) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return this;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(key, type, value);

        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder replace(String target, @NotNull Object replace) {
        String text = PluginUtils.translate((replace instanceof Double number ? fixedDouble(number) : replace).toString());
        return replaceName(target, text).replaceLore(target, text);
    }

    private double fixedDouble(double value) {
        return new BigDecimal(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    public ItemBuilder replace(UnaryOperator<String> operator) {
        return replaceName(operator).replaceLore(operator);
    }

    public ItemBuilder replaceName(String target, String replace) {
        return replaceName(string -> string.replace(target, replace));
    }

    public ItemBuilder replaceName(UnaryOperator<String> operator) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return this;

        if (meta.hasDisplayName()) {
            meta.setDisplayName(operator.apply(meta.getDisplayName()));
        }

        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder replaceLore(String target, String replace) {
        return replaceLore(string -> string.replace(target, replace));
    }

    public ItemBuilder replaceLore(UnaryOperator<String> operator) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return this;

        List<String> lore;
        if (meta.hasLore() && (lore = meta.getLore()) != null) {
            lore.replaceAll(operator);
            meta.setLore(lore);
        }

        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder glow() {
        item.addUnsafeEnchantment(item.getType() == Material.BOW ?
                Enchantment.DURABILITY :
                Enchantment.ARROW_DAMAGE, 1);
        return addItemFlags(ItemFlag.HIDE_ENCHANTS);
    }

    public ItemBuilder setMapView(MapView view) {
        if (!(item.getItemMeta() instanceof MapMeta meta)) return this;
        meta.setMapView(view);
        item.setItemMeta(meta);
        return this;
    }

    public ItemStack build() {
        return item;
    }
}