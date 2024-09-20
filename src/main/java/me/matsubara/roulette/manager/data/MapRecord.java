package me.matsubara.roulette.manager.data;

import java.util.UUID;

public record MapRecord(int mapId, UUID playerUUID, UUID sessionUUID) {
}