package dev.rosewood.rosestacker.stack.settings.entity;

import com.google.gson.JsonObject;
import dev.rosewood.rosegarden.config.CommentedFileConfiguration;
import dev.rosewood.rosestacker.stack.EntityStackComparisonResult;
import dev.rosewood.rosestacker.stack.StackedEntity;
import dev.rosewood.rosestacker.stack.settings.EntityStackSettings;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.EntityType;

public class AxolotlStackSettings extends EntityStackSettings {

    private final boolean dontStackIfPlayingDead;
    private final boolean dontStackIfDifferentVariant;

    public AxolotlStackSettings(CommentedFileConfiguration settingsFileConfiguration, JsonObject jsonObject) {
        super(settingsFileConfiguration, jsonObject);

        this.dontStackIfPlayingDead = this.settingsConfiguration.getBoolean("dont-stack-if-playing-dead");
        this.dontStackIfDifferentVariant = this.settingsConfiguration.getBoolean("dont-stack-if-different-variant");
    }

    @Override
    protected void setDefaultsInternal() {
        this.setIfNotExists("dont-stack-if-playing-dead", false);
        this.setIfNotExists("dont-stack-if-different-variant", false);
    }

    @Override
    protected EntityStackComparisonResult canStackWithInternal(StackedEntity stack1, StackedEntity stack2) {
        Axolotl axolotl = (Axolotl) stack1.getEntity();
        Axolotl axolotl2 = (Axolotl) stack2.getEntity();

        if (this.dontStackIfPlayingDead && (axolotl.isPlayingDead() || axolotl2.isPlayingDead()))
            return EntityStackComparisonResult.PLAYING_DEAD;

        if (this.dontStackIfDifferentVariant && (axolotl.getVariant() != axolotl2.getVariant()))
            return EntityStackComparisonResult.DIFFERENT_VARIANT;

        return EntityStackComparisonResult.CAN_STACK;
    }

    @Override
    public EntityType getEntityType() {
        return EntityType.AXOLOTL;
    }

}
