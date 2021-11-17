package me.matsubara.roulette.hook;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import me.matsubara.roulette.RoulettePlugin;
import org.bukkit.entity.Player;

public final class EssXExtension {

    private final Essentials essentials;

    public EssXExtension(RoulettePlugin plugin) {
        this.essentials = (Essentials) plugin.getServer().getPluginManager().getPlugin("Essentials");
    }

    public boolean isVanished(Player player) {
        if (essentials == null) return false;

        User user = essentials.getUser(player);
        return user != null && user.isVanished();
    }
}