package dev.rosewood.rosestacker.stack.settings.spawner.tags;

import dev.rosewood.rosestacker.manager.LocaleManager;
import dev.rosewood.rosestacker.stack.settings.SpawnerStackSettings;
import dev.rosewood.rosestacker.stack.settings.spawner.ConditionTag;
import dev.rosewood.rosestacker.utils.StackerUtils;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;

import java.util.Collections;
import java.util.List;

public class LightnessConditionTag extends ConditionTag {

    public LightnessConditionTag(String tag) {
        super(tag, true);
    }

    @Override
    public boolean check(CreatureSpawner creatureSpawner, SpawnerStackSettings stackSettings, Block spawnBlock) {
        return StackerUtils.isAir(spawnBlock.getType()) && spawnBlock.getLightLevel() > 8;
    }

    @Override
    public boolean parseValues(String[] values) {
        return values.length == 0;
    }

    @Override
    protected List<String> getInfoMessageValues(LocaleManager localeManager) {
        return Collections.emptyList();
    }

}
