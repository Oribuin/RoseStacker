package dev.rosewood.rosestacker.stack.settings;

import dev.rosewood.rosegarden.config.CommentedFileConfiguration;
import dev.rosewood.rosestacker.RoseStacker;
import dev.rosewood.rosestacker.manager.ConfigurationManager.Setting;
import dev.rosewood.rosestacker.manager.StackSettingManager;
import dev.rosewood.rosestacker.stack.settings.spawner.ConditionTag;
import dev.rosewood.rosestacker.stack.settings.spawner.ConditionTags;
import dev.rosewood.rosestacker.utils.StackerUtils;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;

public class SpawnerStackSettings extends StackSettings {

    private final EntityType entityType;
    private final boolean enabled;
    private final String displayName;
    private final int maxStackSize;
    private final Boolean disableMobAI;
    private final int spawnCountStackSizeMultiplier;
    private final int minSpawnDelay;
    private final int maxSpawnDelay;
    private final int entitySearchRange;
    private final int playerActivationRange;
    private final int spawnRange;
    private final List<ConditionTag> spawnRequirements;

    public SpawnerStackSettings(CommentedFileConfiguration settingsConfiguration, EntityType entityType) {
        super(settingsConfiguration);
        this.entityType = entityType;

        this.setDefaults();

        this.enabled = this.settingsConfiguration.getBoolean("enabled");
        this.displayName = this.settingsConfiguration.getString("display-name");
        this.maxStackSize = this.settingsConfiguration.getInt("max-stack-size");
        this.disableMobAI = this.settingsConfiguration.getDefaultedBoolean("disable-mob-ai");
        this.spawnCountStackSizeMultiplier = this.settingsConfiguration.getInt("spawn-count-stack-size-multiplier");
        this.minSpawnDelay = this.settingsConfiguration.getInt("spawn-delay-minimum");
        this.maxSpawnDelay = this.settingsConfiguration.getInt("spawn-delay-maximum");
        this.entitySearchRange = this.settingsConfiguration.getInt("entity-search-range");
        this.playerActivationRange = this.settingsConfiguration.getInt("player-activation-range");
        this.spawnRange = this.settingsConfiguration.getInt("spawn-range");

        this.spawnRequirements = new ArrayList<>();

        List<String> requirementStrings = this.settingsConfiguration.getStringList("spawn-requirements");
        for (String requirement : requirementStrings) {
            try {
                this.spawnRequirements.add(ConditionTags.parse(requirement));
            } catch (Exception e) {
                RoseStacker.getInstance().getLogger().warning(String.format("Invalid Spawner Requirement Tag: %s", requirement));
            }
        }

        if (Setting.SPAWNER_DONT_SPAWN_INTO_BLOCKS.getBoolean() && (requirementStrings.stream().noneMatch(x -> x.startsWith("fluid") || x.startsWith("air"))))
            this.spawnRequirements.add(ConditionTags.parse("air")); // All entities that don't require fluids will require air

        if (requirementStrings.stream().noneMatch(x -> x.startsWith("max-nearby-entities")))
            this.spawnRequirements.add(ConditionTags.parse("max-nearby-entities:" + Setting.SPAWNER_SPAWN_MAX_NEARBY_ENTITIES.getInt()));
    }

    @Override
    protected void setDefaults() {
        super.setDefaults();

        this.setIfNotExists("enabled", true);
        this.setIfNotExists("display-name", StackerUtils.formatName(this.entityType.name() + '_' + Material.SPAWNER.name()));
        this.setIfNotExists("max-stack-size", -1);
        this.setIfNotExists("disable-mob-ai", "default");
        this.setIfNotExists("spawn-count-stack-size-multiplier", -1);
        this.setIfNotExists("spawn-delay-minimum", -1);
        this.setIfNotExists("spawn-delay-maximum", -1);
        this.setIfNotExists("entity-search-range", -1);
        this.setIfNotExists("player-activation-range", -1);
        this.setIfNotExists("spawn-range", -1);

        List<String> defaultSpawnRequirements = new ArrayList<>(RoseStacker.getInstance().getManager(StackSettingManager.class).getEntityStackSettings(this.entityType).getEntityTypeData().getDefaultSpawnRequirements());
        this.setIfNotExists("spawn-requirements", defaultSpawnRequirements);
    }

    @Override
    protected String getConfigurationSectionKey() {
        return this.entityType.name();
    }

    @Override
    public boolean isStackingEnabled() {
        return this.enabled;
    }

    @Override
    public String getDisplayName() {
        return this.displayName;
    }

    @Override
    public int getMaxStackSize() {
        if (this.maxStackSize != -1)
            return this.maxStackSize;
        return Setting.SPAWNER_MAX_STACK_SIZE.getInt();
    }

    public EntityType getEntityType() {
        return this.entityType;
    }

    public boolean isMobAIDisabled() {
        if (this.disableMobAI != null)
            return this.disableMobAI;
        return Setting.SPAWNER_DISABLE_MOB_AI.getBoolean();
    }

    public List<ConditionTag> getSpawnRequirements() {
        return this.spawnRequirements;
    }

    public int getSpawnCountStackSizeMultiplier() {
        if (this.spawnCountStackSizeMultiplier != -1)
            return Math.max(this.spawnCountStackSizeMultiplier, 1);
        return Math.max(Setting.SPAWNER_SPAWN_COUNT_STACK_SIZE_MULTIPLIER.getInt(), 1);
    }

    public int getMinSpawnDelay() {
        if (this.minSpawnDelay != -1)
            return Math.max(this.minSpawnDelay, 5);
        return Math.max(Setting.SPAWNER_SPAWN_DELAY_MINIMUM.getInt(), 5);
    }

    public int getMaxSpawnDelay() {
        if (this.maxSpawnDelay != -1)
            return Math.max(this.maxSpawnDelay, this.getMinSpawnDelay());
        return Math.max(Setting.SPAWNER_SPAWN_DELAY_MAXIMUM.getInt(), this.getMinSpawnDelay());
    }

    public int getEntitySearchRange() {
        if (this.entitySearchRange != -1)
            return Math.max(this.entitySearchRange, 1);

        int globalRange = Setting.SPAWNER_SPAWN_ENTITY_SEARCH_RANGE.getInt();
        if (globalRange == -1)
            return this.getSpawnRange();
        return Math.max(globalRange, 1);
    }

    public int getPlayerActivationRange() {
        if (this.playerActivationRange != -1)
            return Math.max(this.playerActivationRange, 1);
        return Math.max(Setting.SPAWNER_SPAWN_PLAYER_ACTIVATION_RANGE.getInt(), 1);
    }

    public int getSpawnRange() {
        if (this.spawnRange != -1)
            return Math.max(this.spawnRange, 1);
        return Math.max(Setting.SPAWNER_SPAWN_RANGE.getInt(), 1);
    }

}
