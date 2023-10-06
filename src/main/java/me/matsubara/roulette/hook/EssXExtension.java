package me.matsubara.roulette.hook;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import me.matsubara.roulette.RoulettePlugin;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class EssXExtension {

    private final Essentials essentials;

    public EssXExtension(@NotNull RoulettePlugin plugin) {
        this.essentials = (Essentials) plugin.getServer().getPluginManager().getPlugin("Essentials");
    }

    public boolean isVanished(Player player) {
        if (essentials == null) return false;

        User user = essentials.getUser(player);
        return user != null && user.isVanished();
    }
}