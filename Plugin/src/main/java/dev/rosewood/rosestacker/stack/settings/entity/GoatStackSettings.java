package dev.rosewood.rosestacker.stack.settings.entity;

import com.google.gson.JsonObject;
import dev.rosewood.rosegarden.config.CommentedFileConfiguration;
import dev.rosewood.rosestacker.stack.EntityStackComparisonResult;
import dev.rosewood.rosestacker.stack.StackedEntity;
import dev.rosewood.rosestacker.stack.settings.EntityStackSettings;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Goat;

public class GoatStackSettings extends EntityStackSettings {

    // haha screeching goat
    private final boolean dontStackIfScreeching;

    public GoatStackSettings(CommentedFileConfiguration settingsFileConfiguration, JsonObject jsonObject) {
        super(settingsFileConfiguration, jsonObject);

        this.dontStackIfScreeching = this.settingsConfiguration.getBoolean("dont-stack-if-screaming");
    }

    @Override
    protected void setDefaultsInternal() {
        this.setIfNotExists("dont-stack-if-screaming", false);
    }

    @Override
    protected EntityStackComparisonResult canStackWithInternal(StackedEntity stack1, StackedEntity stack2) {
        Goat goat = (Goat) stack1.getEntity();
        Goat goat2 = (Goat) stack2.getEntity();

        if (this.dontStackIfScreeching && (goat.isScreaming() || goat2.isScreaming()))
            return EntityStackComparisonResult.SCREAMING;

        return EntityStackComparisonResult.CAN_STACK;
    }

    @Override
    public EntityType getEntityType() {
        return EntityType.GOAT;
    }

}
