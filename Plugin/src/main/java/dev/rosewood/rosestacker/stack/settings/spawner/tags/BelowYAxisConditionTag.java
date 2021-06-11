package dev.rosewood.rosestacker.stack.settings.spawner.tags;

import dev.rosewood.rosestacker.manager.LocaleManager;
import dev.rosewood.rosestacker.stack.settings.SpawnerStackSettings;
import dev.rosewood.rosestacker.stack.settings.spawner.ConditionTag;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;

import java.util.Collections;
import java.util.List;

public class BelowYAxisConditionTag extends ConditionTag {

    private int yValue;

    public BelowYAxisConditionTag(String tag) {
        super(tag, true);
    }

    @Override
    public boolean check(CreatureSpawner creatureSpawner, SpawnerStackSettings stackSettings, Block spawnBlock) {
        return spawnBlock.getY() <= this.yValue;
    }

    @Override
    public boolean parseValues(String[] values) {
        if (values.length != 1)
            return false;

        try {
            this.yValue = Integer.parseInt(values[0]);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    @Override
    protected List<String> getInfoMessageValues(LocaleManager localeManager) {
        return Collections.singletonList(String.valueOf(this.yValue));
    }

}
