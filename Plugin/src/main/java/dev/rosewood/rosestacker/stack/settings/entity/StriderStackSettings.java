package dev.rosewood.rosestacker.stack.settings.entity;

import com.google.gson.JsonObject;
import dev.rosewood.rosegarden.config.CommentedFileConfiguration;
import dev.rosewood.rosestacker.stack.EntityStackComparisonResult;
import dev.rosewood.rosestacker.stack.StackedEntity;
import dev.rosewood.rosestacker.stack.settings.EntityStackSettings;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Strider;

public class StriderStackSettings extends EntityStackSettings {

    private final boolean dontStackIfShivering;
    private final boolean dontStackIfSaddled;

    public StriderStackSettings(CommentedFileConfiguration entitySettingsFileConfiguration, JsonObject jsonObject) {
        super(entitySettingsFileConfiguration, jsonObject);

        this.dontStackIfShivering = this.settingsConfiguration.getBoolean("dont-stack-if-shivering");
        this.dontStackIfSaddled = this.settingsConfiguration.getBoolean("dont-stack-if-saddled");
    }

    @Override
    protected EntityStackComparisonResult canStackWithInternal(StackedEntity stack1, StackedEntity stack2) {
        Strider strider1 = (Strider) stack1.getEntity();
        Strider strider2 = (Strider) stack2.getEntity();

        if (this.dontStackIfShivering && (strider1.isShivering() || strider2.isShivering()))
            return EntityStackComparisonResult.SHIVERING;

        if (this.dontStackIfSaddled && (strider1.hasSaddle() || strider2.hasSaddle()))
            return EntityStackComparisonResult.SADDLED;

        return EntityStackComparisonResult.CAN_STACK;
    }

    @Override
    protected void setDefaultsInternal() {
        this.setIfNotExists("dont-stack-if-shivering", false);
        this.setIfNotExists("dont-stack-if-saddled", false);
    }

    @Override
    public EntityType getEntityType() {
        return EntityType.STRIDER;
    }

}
