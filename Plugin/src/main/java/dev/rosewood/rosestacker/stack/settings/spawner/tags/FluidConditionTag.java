package dev.rosewood.rosestacker.stack.settings.spawner.tags;

import dev.rosewood.rosestacker.manager.LocaleManager;
import dev.rosewood.rosestacker.stack.settings.SpawnerStackSettings;
import dev.rosewood.rosestacker.stack.settings.spawner.ConditionTag;
import dev.rosewood.rosestacker.utils.EntityUtils;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;

import java.util.Collections;
import java.util.List;

public class FluidConditionTag extends ConditionTag {

    private Material fluidType;

    public FluidConditionTag(String tag) {
        super(tag, true);
    }

    @Override
    public boolean check(CreatureSpawner creatureSpawner, SpawnerStackSettings stackSettings, Block spawnBlock) {
        boolean isFluid = true;
        for (Block block : EntityUtils.getIntersectingBlocks(creatureSpawner.getSpawnedType(), spawnBlock.getLocation().clone().add(0.5, 0, 0.5)))
            isFluid &= block.getType() == this.fluidType;
        return isFluid;
    }

    @Override
    public boolean parseValues(String[] values) {
        if (values.length != 1)
            return false;

        Material fluidType = Material.matchMaterial(values[0]);
        if (fluidType == Material.WATER || fluidType == Material.LAVA) {
            this.fluidType = fluidType;
            return true;
        }

        return false;
    }

    @Override
    protected List<String> getInfoMessageValues(LocaleManager localeManager) {
        return Collections.singletonList(this.fluidType.name());
    }

}
