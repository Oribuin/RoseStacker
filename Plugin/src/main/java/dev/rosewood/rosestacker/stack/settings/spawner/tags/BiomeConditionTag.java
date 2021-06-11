package dev.rosewood.rosestacker.stack.settings.spawner.tags;

import dev.rosewood.rosestacker.manager.LocaleManager;
import dev.rosewood.rosestacker.stack.settings.SpawnerStackSettings;
import dev.rosewood.rosestacker.stack.settings.spawner.ConditionTag;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class BiomeConditionTag extends ConditionTag {

    private List<Biome> biomes;

    public BiomeConditionTag(String tag) {
        super(tag, false);
    }

    @Override
    public boolean check(CreatureSpawner creatureSpawner, SpawnerStackSettings stackSettings, Block spawnBlock) {
        return this.biomes.contains(spawnBlock.getBiome());
    }

    @Override
    public boolean parseValues(String[] values) {
        this.biomes = new ArrayList<>();

        if (values.length == 0)
            return false;

        for (String value : values) {
            try {
                Biome biome = Biome.valueOf(value.toUpperCase());
                this.biomes.add(biome);
            } catch (Exception ignored) { }
        }

        return !this.biomes.isEmpty();
    }

    @Override
    protected List<String> getInfoMessageValues(LocaleManager localeManager) {
        return this.biomes.stream().map(Enum::name).collect(Collectors.toList());
    }

}
