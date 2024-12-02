package me.matsubara.roulette.hook;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import me.matsubara.roulette.RoulettePlugin;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class EssXExtension implements RExtension<EssXExtension> {

    private Essentials essentials;

    @Override
    public EssXExtension init(@NotNull RoulettePlugin plugin) {
        essentials = (Essentials) plugin.getServer().getPluginManager().getPlugin("Essentials");
        return this;
    }

    public boolean isVanished(Player player) {
        if (essentials == null) return false;

        User user = essentials.getUser(player);
        return user != null && (user.isVanished() || user.isHidden());
    }
}