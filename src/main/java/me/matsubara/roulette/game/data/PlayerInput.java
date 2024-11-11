package me.matsubara.roulette.game.data;

public record PlayerInput(float sideways, float forward, boolean jump, boolean dismount) {
    public static final PlayerInput ZERO = new PlayerInput(0.0f, 0.0f, false, false);
}