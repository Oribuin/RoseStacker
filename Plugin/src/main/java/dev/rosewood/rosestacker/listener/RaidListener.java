package dev.rosewood.rosestacker.listener;

import dev.rosewood.rosegarden.utils.NMSUtil;
import org.bukkit.Raid;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Raider;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.raid.RaidStopEvent;
import org.bukkit.event.raid.RaidTriggerEvent;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RaidListener implements Listener {

    private final static Set<Raid> activeRaids = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRaidTrigger(RaidTriggerEvent event) {
        activeRaids.add(event.getRaid());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRaidStop(RaidStopEvent event) {
        activeRaids.remove(event.getRaid());
    }

    public static boolean isActiveRaider(LivingEntity entity) {
        if (NMSUtil.getVersionNumber() < 14 || !(entity instanceof Raider))
            return false;

        return activeRaids.stream().anyMatch(x -> x.getRaiders().contains(entity));
    }

}
