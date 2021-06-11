package dev.rosewood.rosestacker.stack;

import dev.rosewood.rosegarden.utils.StringPlaceholders;
import dev.rosewood.rosestacker.RoseStacker;
import dev.rosewood.rosestacker.gui.StackedSpawnerGui;
import dev.rosewood.rosestacker.manager.ConfigurationManager.Setting;
import dev.rosewood.rosestacker.manager.HologramManager;
import dev.rosewood.rosestacker.manager.LocaleManager;
import dev.rosewood.rosestacker.manager.StackSettingManager;
import dev.rosewood.rosestacker.nms.NMSAdapter;
import dev.rosewood.rosestacker.nms.object.SpawnerTileWrapper;
import dev.rosewood.rosestacker.stack.settings.SpawnerStackSettings;
import dev.rosewood.rosestacker.stack.settings.spawner.ConditionTag;
import dev.rosewood.rosestacker.utils.StackerUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class StackedSpawner extends Stack<SpawnerStackSettings> {

    private int size;
    private CreatureSpawner spawner;
    private SpawnerTileWrapper spawnerTile;
    private Location location;
    private boolean placedByPlayer;
    private StackedSpawnerGui stackedSpawnerGui;
    private List<Class<? extends ConditionTag>> lastInvalidConditions;

    private boolean powered;
    private int lastDelay;

    private SpawnerStackSettings stackSettings;

    public StackedSpawner(int id, int size, CreatureSpawner spawner, boolean placedByPlayer) {
        super(id);

        this.size = size;
        this.spawner = spawner;
        this.placedByPlayer = placedByPlayer;
        this.location = this.spawner.getLocation();
        this.stackedSpawnerGui = null;
        this.lastInvalidConditions = new ArrayList<>();

        this.powered = false;
        this.lastDelay = spawner.getDelay();

        if (this.spawner != null) {
            this.spawnerTile = NMSAdapter.getHandler().getSpawnerTile(this.spawner);
            this.stackSettings = RoseStacker.getInstance().getManager(StackSettingManager.class).getSpawnerStackSettings(this.spawner);

            if (Bukkit.isPrimaryThread()) {
                this.updateSpawnerProperties(true);
                this.updateDisplay();
            }
        }
    }

    public StackedSpawner(int size, CreatureSpawner spawner, boolean placedByPlayer) {
        this(-1, size, spawner, placedByPlayer);
    }

    /**
     * This constructor should only be used by the converters and SHOULD NEVER be put into a StackingThread
     *
     * @param size The size of the stack
     * @param location The Location of the stack
     */
    public StackedSpawner(int size, Location location) {
        super(-1);

        this.size = size;
        this.spawner = null;
        this.location = location;
    }

    public CreatureSpawner getSpawner() {
        return this.spawner;
    }

    public SpawnerTileWrapper getSpawnerTile() {
        return this.spawnerTile;
    }

    public void kickOutViewers() {
        if (this.stackedSpawnerGui != null)
            this.stackedSpawnerGui.kickOutViewers();
    }

    public void increaseStackSize(int amount) {
        this.size += amount;
        this.updateSpawnerProperties(false);
        this.updateDisplay();
    }

    public void setStackSize(int size) {
        this.size = size;
        this.updateSpawnerProperties(false);
        this.updateDisplay();
    }

    public void openGui(Player player) {
        if (this.stackedSpawnerGui == null)
            this.stackedSpawnerGui = new StackedSpawnerGui(this);
        this.stackedSpawnerGui.openFor(player);
    }

    public List<Class<? extends ConditionTag>> getLastInvalidConditions() {
        return this.lastInvalidConditions;
    }

    @Override
    public int getStackSize() {
        return this.size;
    }

    @Override
    public Location getLocation() {
        return this.location;
    }

    @Override
    public void updateDisplay() {
        if (!Setting.SPAWNER_DISPLAY_TAGS.getBoolean() || this.stackSettings == null)
            return;

        HologramManager hologramManager = RoseStacker.getInstance().getManager(HologramManager.class);

        Location location = this.location.clone().add(0.5, 0.75, 0.5);

        int sizeForHologram = Setting.SPAWNER_DISPLAY_TAGS_SINGLE.getBoolean() ? 0 : 1;
        if (this.size <= sizeForHologram) {
            hologramManager.deleteHologram(location);
            return;
        }

        String displayString;
        if (this.size == 1 && !Setting.SPAWNER_DISPLAY_TAGS_SINGLE_AMOUNT.getBoolean()) {
            displayString = RoseStacker.getInstance().getManager(LocaleManager.class).getLocaleMessage("spawner-stack-display-single", StringPlaceholders.builder("amount", this.getStackSize())
                    .addPlaceholder("name", this.stackSettings.getDisplayName()).build());
        } else {
            displayString = RoseStacker.getInstance().getManager(LocaleManager.class).getLocaleMessage("spawner-stack-display", StringPlaceholders.builder("amount", this.getStackSize())
                    .addPlaceholder("name", this.stackSettings.getDisplayName()).build());
        }

        hologramManager.createOrUpdateHologram(location, displayString);
    }

    @Override
    public SpawnerStackSettings getStackSettings() {
        return this.stackSettings;
    }

    public boolean isPlacedByPlayer() {
        return this.placedByPlayer;
    }

    public void updateSpawnerProperties(boolean resetDelay) {
        if (this.spawner.getBlock().getType() != Material.SPAWNER)
            return;

        // Handle the entity type changing
        EntityType oldEntityType = this.spawner.getSpawnedType();
        this.updateSpawnerState();
        this.spawnerTile = NMSAdapter.getHandler().getSpawnerTile(this.spawner);
        if (oldEntityType != this.spawner.getSpawnedType())
            this.stackSettings = RoseStacker.getInstance().getManager(StackSettingManager.class).getSpawnerStackSettings(this.spawner);

        this.spawnerTile.setSpawnCount(this.size * this.stackSettings.getSpawnCountStackSizeMultiplier());
        this.spawnerTile.setMaxSpawnDelay(this.stackSettings.getMaxSpawnDelay());
        this.spawnerTile.setMinSpawnDelay(this.stackSettings.getMinSpawnDelay());
        this.spawnerTile.setRequiredPlayerRange(this.stackSettings.getPlayerActivationRange());
        this.spawnerTile.setSpawnRange(this.stackSettings.getSpawnRange());

        int delay;
        if (resetDelay) {
            delay = StackerUtils.randomInRange(this.stackSettings.getMinSpawnDelay(), this.stackSettings.getMaxSpawnDelay());
        } else {
            delay = this.spawner.getDelay();
        }

        this.spawnerTile.setDelay(delay);
    }

    public void updateSpawnerState() {
        if (this.spawner.getBlock().getType() == Material.SPAWNER)
            this.spawner = (CreatureSpawner) this.spawner.getBlock().getState();
    }

    public void setPowered(boolean powered) {
        this.powered = powered;
    }

    public boolean isPowered() {
        return this.powered;
    }

    public void setLastDelay(int lastDelay) {
        this.lastDelay = lastDelay;
    }

    public int getLastDelay() {
        return this.lastDelay;
    }

}
