package dev.esophose.rosestacker.hologram;

import com.sainttx.holograms.HologramPlugin;
import com.sainttx.holograms.api.Hologram;
import com.sainttx.holograms.api.HologramManager;
import com.sainttx.holograms.api.line.HologramLine;
import com.sainttx.holograms.api.line.TextLine;
import dev.esophose.rosestacker.utils.StackerUtils;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class HologramsHologramHandler implements HologramHandler {

    private HologramManager hologramManager;
    private Set<String> holograms;

    public HologramsHologramHandler() {
        this.hologramManager = JavaPlugin.getPlugin(HologramPlugin.class).getHologramManager();
        this.holograms = new HashSet<>();
    }

    @Override
    public void createOrUpdateHologram(Location location, String text) {
        String key = StackerUtils.locationAsKey(location);
        Hologram hologram = this.hologramManager.getHologram(key);
        if (hologram == null) {
            hologram = new Hologram(StackerUtils.locationAsKey(location), location.add(0, 0.5, 0));
            hologram.addLine(new TextLine(hologram, text));
            this.hologramManager.addActiveHologram(hologram);
            this.holograms.add(key);
        } else {
            for (HologramLine line : new ArrayList<>(hologram.getLines()))
                hologram.removeLine(line);
            hologram.addLine(new TextLine(hologram, text));
        }
    }

    @Override
    public void deleteHologram(Location location) {
        String key = StackerUtils.locationAsKey(location);
        Hologram hologram = this.hologramManager.getHologram(key);
        if (hologram != null) {
            hologram.despawn();
            this.hologramManager.deleteHologram(hologram);
        }
        this.holograms.remove(location);
    }

    @Override
    public void deleteAllHolograms() {
        for (String key : this.holograms) {
            Hologram hologram = this.hologramManager.getHologram(key);
            if (hologram != null) {
                hologram.despawn();
                this.hologramManager.removeActiveHologram(hologram);
            }
        }
        this.holograms.clear();
    }

}
