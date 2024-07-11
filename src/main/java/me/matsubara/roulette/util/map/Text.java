package me.matsubara.roulette.util.map;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.map.MapFont;
import org.jetbrains.annotations.NotNull;

@Getter
@Setter
public final class Text {

    private int x;
    private int y;
    private MapFont font;
    private String message;

    public Text(int x, int y, @NotNull MapFont font, @NotNull String message) {
        setX(x);
        setY(y);
        setFont(font);
        setMessage(message);
    }
}