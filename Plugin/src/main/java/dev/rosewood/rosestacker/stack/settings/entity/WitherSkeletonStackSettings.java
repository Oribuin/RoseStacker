package dev.rosewood.rosestacker.stack.settings.entity;

import com.google.gson.JsonObject;
import dev.rosewood.rosegarden.config.CommentedFileConfiguration;
import org.bukkit.entity.EntityType;

public class WitherSkeletonStackSettings extends SkeletonStackSettings {

    public WitherSkeletonStackSettings(CommentedFileConfiguration entitySettingsFileConfiguration, JsonObject jsonObject) {
        super(entitySettingsFileConfiguration, jsonObject);
    }

    @Override
    public EntityType getEntityType() {
        return EntityType.WITHER_SKELETON;
    }

}
