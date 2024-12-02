package me.matsubara.roulette.model.stand;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;

@Getter
@Setter
public class ModelLocation {

    private final StandSettings settings;
    private Location location;

    public ModelLocation(StandSettings settings, Location location) {
        this.settings = settings;
        this.location = location;
    }
}