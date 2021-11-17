package me.matsubara.roulette.listener;

import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.manager.ConfigManager;
import me.matsubara.roulette.manager.MessageManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public final class EntityDamageByEntity implements Listener {

    private final RoulettePlugin plugin;

    public EntityDamageByEntity(RoulettePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();

        if (damager.getType() == EntityType.FIREWORK) {
            // Disable firework damage when @instant-explode is true.
            if (event.getDamager().hasMetadata("isRoulette")) event.setCancelled(true);
            return;
        }

        if (ConfigManager.Config.HIT_ON_GAME.asBoolean()) return;

        if (isInGame(damager) || (isInGame(event.getEntity()) && damager.getType() == EntityType.PLAYER)) {
            plugin.getMessageManager().send(damager, MessageManager.Message.CAN_NOT_HIT);
            event.setCancelled(true);
        }
    }

    private boolean isInGame(Entity entity) {
        if (entity.getType() != EntityType.PLAYER) return false;
        return plugin.getGameManager().isPlaying((Player) entity);
    }
}