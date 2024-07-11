package me.matsubara.roulette.game;

public enum GameState {
    IDLE, // Idle state.
    STARTING, // Start cooldown initialized while waiting for more players.
    SELECTING, // Game started, players must place their bets.
    SPINNING, // No more bets, the wheel starts spinning.
    ENDING; // The game is over, the winners (if any) are announced.

    public boolean isIdle() {
        return this == IDLE;
    }

    public boolean isStarting() {
        return this == STARTING;
    }

    public boolean isSelecting() {
        return this == SELECTING;
    }

    public boolean isSpinning() {
        return this == SPINNING;
    }

    public boolean isEnding() {
        return this == ENDING;
    }
}