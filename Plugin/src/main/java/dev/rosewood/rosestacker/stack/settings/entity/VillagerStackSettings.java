package dev.rosewood.rosestacker.stack.settings.entity;

import com.google.gson.JsonObject;
import dev.rosewood.rosegarden.config.CommentedFileConfiguration;
import dev.rosewood.rosegarden.utils.NMSUtil;
import dev.rosewood.rosestacker.stack.EntityStackComparisonResult;
import dev.rosewood.rosestacker.stack.StackedEntity;
import dev.rosewood.rosestacker.stack.settings.EntityStackSettings;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;

import java.util.Arrays;
import java.util.List;

public class VillagerStackSettings extends EntityStackSettings {

    private static final List<String> UNPROFESSIONED_VALUE_NAMES = Arrays.asList("NONE", "NITWIT");

    private final boolean dontStackIfProfessioned;
    private final boolean dontStackIfDifferentProfession;
    private final boolean dontStackIfDifferentType;
    private final boolean dontStackIfDifferentLevel;

    public VillagerStackSettings(CommentedFileConfiguration entitySettingsFileConfiguration, JsonObject jsonObject) {
        super(entitySettingsFileConfiguration, jsonObject);

        this.dontStackIfProfessioned = this.settingsConfiguration.getBoolean("dont-stack-if-professioned");
        this.dontStackIfDifferentProfession = this.settingsConfiguration.getBoolean("dont-stack-if-different-profession");
        this.dontStackIfDifferentType = this.settingsConfiguration.getBoolean("dont-stack-if-different-type");
        this.dontStackIfDifferentLevel = NMSUtil.getVersionNumber() >= 14 && this.settingsConfiguration.getBoolean("dont-stack-if-different-level");
    }

    @Override
    protected EntityStackComparisonResult canStackWithInternal(StackedEntity stack1, StackedEntity stack2) {
        Villager villager1 = (Villager) stack1.getEntity();
        Villager villager2 = (Villager) stack2.getEntity();

        if (this.dontStackIfProfessioned && (!UNPROFESSIONED_VALUE_NAMES.contains(villager1.getProfession().name()) || !UNPROFESSIONED_VALUE_NAMES.contains(villager2.getProfession().name())))
            return EntityStackComparisonResult.PROFESSIONED;

        if (this.dontStackIfDifferentProfession && villager1.getProfession() != villager2.getProfession())
            return EntityStackComparisonResult.DIFFERENT_PROFESSIONS;

        if (this.dontStackIfDifferentType && villager1.getType() != villager2.getType())
            return EntityStackComparisonResult.DIFFERENT_TYPES;

        if (this.dontStackIfDifferentLevel && villager1.getVillagerLevel() != villager2.getVillagerLevel())
            return EntityStackComparisonResult.DIFFERENT_LEVELS;

        return EntityStackComparisonResult.CAN_STACK;
    }

    @Override
    protected void setDefaultsInternal() {
        this.setIfNotExists("dont-stack-if-different-profession", false);
        this.setIfNotExists("dont-stack-if-different-type", false);
        if (NMSUtil.getVersionNumber() >= 14)
            this.setIfNotExists("dont-stack-if-different-level", false);
    }

    @Override
    public EntityType getEntityType() {
        return EntityType.VILLAGER;
    }

}
