package me.matsubara.roulette.util.map;

import org.bukkit.map.MapFont;
import org.jetbrains.annotations.NotNull;

/**
 * A storage class to save text information to later be used in order to write in maps.
 */
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

    /**
     * Gets the x position for the text to be displayed.
     *
     * @return the x position.
     */
    public int getX() {
        return x;
    }

    /**
     * Sets the x position of the text to display it.
     *
     * @param x the x postion.
     */
    public void setX(int x) {
        this.x = x;
    }

    /**
     * Gets the y position for the text to be displayed.
     *
     * @return the y position.
     */
    public int getY() {
        return y;
    }

    /**
     * Sets the y position of the text to display it.
     *
     * @param y the y position.
     */
    public void setY(int y) {
        this.y = y;
    }

    /**
     * Gets the font to be used.
     *
     * @return the MapFont that is used.
     */
    public MapFont getFont() {
        return font;
    }

    /**
     * Sets what font should be used.
     *
     * @param font the actual font.
     */
    public void setFont(@NotNull MapFont font) {
        this.font = font;
    }

    /**
     * Gets what text will be displayed.
     *
     * @return the text.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets what text will be displayed.
     *
     * @param message the actual text.
     */
    public void setMessage(@NotNull String message) {
        this.message = message;
    }
}