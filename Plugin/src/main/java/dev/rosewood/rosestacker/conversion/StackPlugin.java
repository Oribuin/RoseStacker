package dev.rosewood.rosestacker.conversion;

import dev.rosewood.rosegarden.RosePlugin;
import dev.rosewood.rosestacker.RoseStacker;
import dev.rosewood.rosestacker.conversion.converter.*;
import dev.rosewood.rosestacker.stack.StackType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public enum StackPlugin {

    WildStacker(WildStackerPluginConverter.class, StackType.ENTITY, StackType.ITEM, StackType.BLOCK, StackType.SPAWNER),
    UltimateStacker(UltimateStackerPluginConverter.class, StackType.ENTITY, StackType.ITEM, StackType.BLOCK, StackType.SPAWNER),
    EpicSpawners(EpicSpawnersPluginConverter.class, StackType.SPAWNER),
    StackMob(StackMobPluginConverter.class, StackType.ENTITY);

    private final Class<? extends StackPluginConverter> converterClass;
    private final List<StackType> stackTypes;

    StackPlugin(Class<? extends StackPluginConverter> conveterClass, StackType... stackTypes) {
        this.converterClass = conveterClass;
        this.stackTypes = Arrays.asList(stackTypes);
    }

    public StackPluginConverter getConverter() {
        try {
            return this.converterClass.getConstructor(RosePlugin.class).newInstance(RoseStacker.getInstance());
        } catch (ReflectiveOperationException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    public List<StackType> getStackTypes() {
        return Collections.unmodifiableList(this.stackTypes);
    }

}
