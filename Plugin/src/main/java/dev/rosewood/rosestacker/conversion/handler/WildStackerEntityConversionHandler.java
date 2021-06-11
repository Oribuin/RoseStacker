package dev.rosewood.rosestacker.conversion.handler;

import dev.rosewood.rosegarden.RosePlugin;
import dev.rosewood.rosestacker.RoseStacker;
import dev.rosewood.rosestacker.conversion.ConversionData;
import dev.rosewood.rosestacker.stack.Stack;
import dev.rosewood.rosestacker.stack.StackType;
import dev.rosewood.rosestacker.stack.StackedEntity;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class WildStackerEntityConversionHandler extends ConversionHandler {

    private static final NamespacedKey STACK_KEY = new NamespacedKey("wildstacker", "stackamount");
    private static final NamespacedKey CONVERTED_KEY = new NamespacedKey(RoseStacker.getInstance(), "converted");

    public WildStackerEntityConversionHandler(RosePlugin rosePlugin) {
        super(rosePlugin, StackType.ENTITY, true);
    }

    @Override
    public Set<Stack<?>> handleConversion(Set<ConversionData> conversionData) {
        Set<LivingEntity> entities = conversionData.stream()
                .map(ConversionData::getEntity)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Stack<?>> stacks = new HashSet<>();

        for (LivingEntity entity : entities) {
            PersistentDataContainer dataContainer = entity.getPersistentDataContainer();
            if (dataContainer.has(CONVERTED_KEY, PersistentDataType.INTEGER))
                continue;

            int stackSize = dataContainer.getOrDefault(STACK_KEY, PersistentDataType.INTEGER, -1);
            if (stackSize == -1)
                continue;

            dataContainer.set(CONVERTED_KEY, PersistentDataType.INTEGER, 1);
            StackedEntity stackedEntity = new StackedEntity(entity, this.createEntityStackNBT(entity.getType(), stackSize, entity.getLocation()));
            this.stackManager.addEntityStack(stackedEntity);
            stacks.add(stackedEntity);
        }

        return stacks;
    }

}
