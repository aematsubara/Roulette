package me.matsubara.roulette.hook;

import com.viaversion.viaversion.api.Via;
import org.bukkit.entity.Player;

public final class ViaExtension {

    @SuppressWarnings("unchecked")
    public static int getPlayerVersion(Player player) {
        return Via.getAPI().getPlayerVersion(player);
    }
}