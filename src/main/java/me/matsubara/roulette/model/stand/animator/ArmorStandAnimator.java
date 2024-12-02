package me.matsubara.roulette.model.stand.animator;

import lombok.Getter;
import lombok.Setter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.model.stand.PacketStand;
import me.matsubara.roulette.model.stand.StandSettings;
import org.apache.commons.lang3.ArrayUtils;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Consumer;
import org.bukkit.util.EulerAngle;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
public final class ArmorStandAnimator {

    private final PacketStand stand;
    private final Set<Player> seeing;
    private boolean spawned;

    private Frame[] frames;
    private Location location;

    private int length;
    private int currentFrame;

    private boolean paused;
    private boolean interpolate;

    private static final Map<String, AnimatorCache> CACHE = new HashMap<>();

    public ArmorStandAnimator(RoulettePlugin plugin, Set<Player> seeing, @NotNull File file, StandSettings settings, Location location) {
        this.stand = new PacketStand(plugin, location, settings);
        this.seeing = seeing;
        this.location = stand.getLocation();

        String path = file.getAbsolutePath();
        AnimatorCache cache = CACHE.get(path);

        if (cache != null) {
            this.frames = cache.frames();
            this.length = cache.length();
            this.interpolate = cache.interpolate();
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            Frame currentFrame = null;
            while ((line = reader.readLine()) != null) {
                String[] split = line.split(" ");
                if (line.startsWith("length")) {
                    this.length = (int) Float.parseFloat(split[1]);
                    this.frames = new Frame[this.length];
                    continue;
                }

                if (line.startsWith("frame")) {
                    if (currentFrame != null) {
                        this.frames[currentFrame.getId()] = currentFrame;
                    }
                    int frameID = Integer.parseInt(split[1]);
                    currentFrame = new Frame();
                    currentFrame.setId(frameID);
                    continue;
                }

                if (line.contains("interpolate")) {
                    this.interpolate = true;
                    continue;
                }

                if (currentFrame == null) continue;

                if (line.contains("Armorstand_Position")) {
                    currentFrame.setX(Float.parseFloat(split[1]));
                    currentFrame.setY(Float.parseFloat(split[2]));
                    currentFrame.setZ(Float.parseFloat(split[3]));
                    currentFrame.setRotation(Float.parseFloat(split[4]));
                    continue;
                }

                if (setAngle(line, "Armorstand_Right_Leg", currentFrame::setRightLeg)) continue;
                if (setAngle(line, "Armorstand_Left_Leg", currentFrame::setLeftLeg)) continue;
                if (setAngle(line, "Armorstand_Left_Arm", currentFrame::setLeftArm)) continue;
                if (setAngle(line, "Armorstand_Right_Arm", currentFrame::setRightArm)) continue;
                setAngle(line, "Armorstand_Head", currentFrame::setHead);
            }

            if (currentFrame != null) this.frames[currentFrame.getId()] = currentFrame;
        } catch (Exception exception) {
            exception.printStackTrace();
        }

        CACHE.put(path, new AnimatorCache(ArrayUtils.clone(this.frames), length, interpolate));
    }

    private boolean setAngle(@NotNull String line, String part, Consumer<EulerAngle> setter) {
        if (!line.contains(part)) return false;
        try {
            String[] split = line.split(" ");
            double x = toRadians(split[1]);
            double y = toRadians(split[2]);
            double z = toRadians(split[3]);
            setter.accept(new EulerAngle(x, y, z));
            return true;
        } catch (IndexOutOfBoundsException exception) {
            return false;
        }
    }

    private double toRadians(String data) {
        return Math.toRadians(Double.parseDouble(data));
    }

    public void stop() {
        this.currentFrame = 0;
        this.paused = true;
        stand.destroy();
    }

    public void play() {
        this.paused = false;
    }

    public void update() {
        if (this.paused) return;

        if (this.currentFrame >= this.length - 1 || this.currentFrame < 0) {
            this.currentFrame = 0;
        }

        Frame frame = this.frames[this.currentFrame];
        if (this.interpolate && frame == null) {
            frame = interpolate(this.currentFrame);
        }

        if (frame != null) {
            Location newLocation = this.location.clone().add(frame.getX(), frame.getY(), frame.getZ());
            newLocation.setYaw(frame.getRotation() + newLocation.getYaw());
            this.stand.teleport(seeing, newLocation);

            StandSettings settings = this.stand.getSettings();
            settings.setLeftLegPose(frame.getLeftLeg());
            settings.setRightLegPose(frame.getRightLeg());
            settings.setLeftArmPose(frame.getLeftArm());
            settings.setRightArmPose(frame.getRightArm());
            settings.setHeadPose(frame.getHead());

            stand.sendMetadata(seeing);
        }

        this.currentFrame++;
    }

    private Frame interpolate(int id) {
        Frame minFrame = null;
        for (int i = id; i >= 0; i--) {
            if (this.frames[i] != null) {
                minFrame = this.frames[i];
                break;
            }
        }

        Frame maxFrame = null;
        for (int j = id; j < this.frames.length; j++) {
            if (this.frames[j] != null) {
                maxFrame = this.frames[j];
                break;
            }
        }

        Frame interpolated;
        if (maxFrame == null || minFrame == null) {
            if (maxFrame == null && minFrame != null) return minFrame;
            if (maxFrame != null) return maxFrame;
            interpolated = new Frame();
            interpolated.setId(id);
            return interpolated;
        }

        interpolated = new Frame();
        interpolated.setId(id);

        float lowerDifference = (id - minFrame.getId());
        float totalDifference = (maxFrame.getId() - minFrame.getId());
        float interpolationFactor = lowerDifference / totalDifference;

        return minFrame
                .multiply(1.0f - interpolationFactor, id)
                .add(maxFrame.multiply(interpolationFactor, id), id);
    }
}