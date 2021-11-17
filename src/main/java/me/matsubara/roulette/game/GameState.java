package me.matsubara.roulette.game;

public enum GameState {
    // Idle state.
    IDLE,
    // Start cooldown initialized while waiting for more players.
    STARTING,
    // Game started, players must place their bets.
    SELECTING,
    // No more bets, the wheel starts spinning.
    SPINNING,
    // Game is over, the winners (if any) are announced.
    ENDING;

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