package dev.rosewood.rosestacker.stack;

import dev.rosewood.rosegarden.RosePlugin;
import dev.rosewood.rosestacker.event.*;
import dev.rosewood.rosestacker.hook.NPCsHook;
import dev.rosewood.rosestacker.hook.WorldGuardHook;
import dev.rosewood.rosestacker.manager.ConfigurationManager.Setting;
import dev.rosewood.rosestacker.manager.ConversionManager;
import dev.rosewood.rosestacker.manager.EntityCacheManager;
import dev.rosewood.rosestacker.manager.StackManager;
import dev.rosewood.rosestacker.manager.StackSettingManager;
import dev.rosewood.rosestacker.nms.NMSAdapter;
import dev.rosewood.rosestacker.nms.NMSHandler;
import dev.rosewood.rosestacker.stack.settings.EntityStackSettings;
import dev.rosewood.rosestacker.stack.settings.ItemStackSettings;
import dev.rosewood.rosestacker.utils.EntityUtils;
import dev.rosewood.rosestacker.utils.ItemUtils;
import dev.rosewood.rosestacker.utils.PersistentDataUtils;
import dev.rosewood.rosestacker.utils.StackerUtils;
import org.bukkit.*;
import org.bukkit.Particle.DustOptions;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.*;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class StackingThread implements StackingLogic, AutoCloseable {

    private final static int CLEANUP_TIMER_TARGET = 10;

    private final RosePlugin rosePlugin;
    private final StackManager stackManager;
    private final ConversionManager conversionManager;
    private final EntityCacheManager entityCacheManager;
    private final World targetWorld;

    private final BukkitTask entityStackTask;
    private final BukkitTask itemStackTask;
    private final BukkitTask nametagTask;

    private final Map<UUID, StackedEntity> stackedEntities;
    private final Map<UUID, StackedItem> stackedItems;
    private final Map<Block, StackedBlock> stackedBlocks;
    private final Map<Block, StackedSpawner> stackedSpawners;

    private boolean entityStackSwitch;
    private int cleanupTimer;

    public StackingThread(RosePlugin rosePlugin, StackManager stackManager, World targetWorld) {
        this.rosePlugin = rosePlugin;
        this.stackManager = stackManager;
        this.conversionManager = this.rosePlugin.getManager(ConversionManager.class);
        this.entityCacheManager = this.rosePlugin.getManager(EntityCacheManager.class);
        this.targetWorld = targetWorld;

        long entityStackDelay = (long) Math.max(1, Setting.STACK_FREQUENCY.getLong() / 2.0);
        this.entityStackTask = Bukkit.getScheduler().runTaskTimer(this.rosePlugin, this::stackEntities, 5L, entityStackDelay);
        this.itemStackTask = Bukkit.getScheduler().runTaskTimer(this.rosePlugin, this::stackItems, 5L, Setting.ITEM_STACK_FREQUENCY.getLong());
        // Throws error when you get entities async
        this.nametagTask = Bukkit.getScheduler().runTaskTimer(this.rosePlugin, this::processNametags, 5L, Setting.NAMETAG_UPDATE_FREQUENCY.getLong());

        this.stackedEntities = new ConcurrentHashMap<>();
        this.stackedItems = new ConcurrentHashMap<>();
        this.stackedBlocks = new ConcurrentHashMap<>();
        this.stackedSpawners = new ConcurrentHashMap<>();

        this.cleanupTimer = 0;

        // Disable AI for all existing stacks in the target world
        this.targetWorld.getLivingEntities().forEach(PersistentDataUtils::applyDisabledAi);
    }

    private void stackEntities() {
        boolean itemStackingEnabled = this.stackManager.isItemStackingEnabled();
        boolean entityStackingEnabled = this.stackManager.isEntityStackingEnabled();
        if (!entityStackingEnabled)
            return;

        // Auto stack entities
        if (this.entityStackSwitch) {
            for (StackedEntity stackedEntity : new HashSet<>(this.stackedEntities.values())) {
                LivingEntity livingEntity = stackedEntity.getEntity();
                if (livingEntity == null || !livingEntity.isValid()) {
                    this.removeEntityStack(stackedEntity);
                    continue;
                }

                this.tryStackEntity(stackedEntity);
            }
        }

        // Run entity stacking half as often as the unstacking
        this.entityStackSwitch = !this.entityStackSwitch;

        // Auto unstack entities
        if (!this.stackManager.isEntityUnstackingTemporarilyDisabled())
            for (StackedEntity stackedEntity : new HashSet<>(this.stackedEntities.values()))
                if (!stackedEntity.shouldStayStacked())
                    Bukkit.getScheduler().runTask(this.rosePlugin, () -> this.splitEntityStack(stackedEntity));

        // Cleans up entities/items that aren't stacked
        this.cleanupTimer++;
        if (this.cleanupTimer >= CLEANUP_TIMER_TARGET) {
            for (Entity entity : this.targetWorld.getEntities()) {
                // Don't create stacks from chunks we are about to load
                if (!entity.isValid() || this.stackManager.isChunkPendingLoad(entity.getLocation().getChunk()))
                    continue;

                if (entity instanceof LivingEntity && entity.getType() != EntityType.ARMOR_STAND && entity.getType() != EntityType.PLAYER) {
                    LivingEntity livingEntity = (LivingEntity) entity;
                    if (!this.isEntityStacked(livingEntity))
                        this.createEntityStack(livingEntity, false);
                } else if (itemStackingEnabled && entity.getType() == EntityType.DROPPED_ITEM) {
                    Item item = (Item) entity;
                    if (!this.isItemStacked(item))
                        this.createItemStack(item, false);
                }
            }
            this.cleanupTimer = 0;
        }
    }

    private void stackItems() {
        boolean itemStackingEnabled = this.stackManager.isItemStackingEnabled();
        if (!itemStackingEnabled)
            return;

        // Auto stack items
        for (StackedItem stackedItem : new HashSet<>(this.stackedItems.values())) {
            Item item = stackedItem.getItem();
            if (item == null || !item.isValid()) {
                this.removeItemStack(stackedItem);
                continue;
            }

            this.tryStackItem(stackedItem);
        }
    }

    private void processNametags() {
        // Handle dynamic stack tags
        boolean dynamicEntityTags = Setting.ENTITY_DISPLAY_TAGS.getBoolean() && Setting.ENTITY_DYNAMIC_TAG_VIEW_RANGE_ENABLED.getBoolean();
        boolean dynamicItemTags = Setting.ITEM_DISPLAY_TAGS.getBoolean() && Setting.ITEM_DYNAMIC_TAG_VIEW_RANGE_ENABLED.getBoolean();
        boolean dynamicBlockTags = Setting.BLOCK_DISPLAY_TAGS.getBoolean() && Setting.BLOCK_DYNAMIC_TAG_VIEW_RANGE_ENABLED.getBoolean();

        if (!(dynamicEntityTags || dynamicItemTags || dynamicBlockTags))
            return;

        double entityDynamicViewRange = Setting.ENTITY_DYNAMIC_TAG_VIEW_RANGE.getDouble();
        double itemDynamicViewRange = Setting.ITEM_DYNAMIC_TAG_VIEW_RANGE.getDouble();
        double blockSpawnerDynamicViewRange = Setting.BLOCK_DYNAMIC_TAG_VIEW_RANGE.getDouble();

        double entityDynamicViewRangeSqrd = entityDynamicViewRange * entityDynamicViewRange;
        double itemDynamicViewRangeSqrd = itemDynamicViewRange * itemDynamicViewRange;
        double blockSpawnerDynamicViewRangeSqrd = blockSpawnerDynamicViewRange * blockSpawnerDynamicViewRange;

        boolean entityDynamicWallDetection = Setting.ENTITY_DYNAMIC_TAG_VIEW_RANGE_WALL_DETECTION_ENABLED.getBoolean();
        boolean itemDynamicWallDetection = Setting.ITEM_DYNAMIC_TAG_VIEW_RANGE_WALL_DETECTION_ENABLED.getBoolean();
        boolean blockDynamicWallDetection = Setting.BLOCK_DYNAMIC_TAG_VIEW_RANGE_WALL_DETECTION_ENABLED.getBoolean();

        NMSHandler nmsHandler = NMSAdapter.getHandler();
        Set<EntityType> validEntities = StackerUtils.getStackableEntityTypes();
        for (Player player : new ArrayList<>(this.targetWorld.getPlayers())) {
            if (player.getWorld() != this.targetWorld)
                continue;

            ItemStack itemStack = player.getInventory().getItemInMainHand();
            boolean displayStackingToolParticles = ItemUtils.isStackingTool(itemStack);

            for (Entity entity : new ArrayList<>(this.targetWorld.getEntities())) {
                if (entity.getType() == EntityType.PLAYER)
                    continue;

                if ((entity.getType() == EntityType.DROPPED_ITEM || entity.getType() == EntityType.ARMOR_STAND)
                        && (entity.getCustomName() == null || !entity.isCustomNameVisible()))
                    continue;

                double distanceSqrd;
                try { // The locations can end up comparing cross-world if the player/entity switches worlds mid-loop due to being async
                    distanceSqrd = player.getLocation().distanceSquared(entity.getLocation());
                } catch (Exception e) {
                    continue;
                }

                if (distanceSqrd > StackerUtils.ASSUMED_ENTITY_VISIBILITY_RANGE)
                    continue;

                boolean visible;
                if (dynamicEntityTags && (validEntities.contains(entity.getType()))) {
                    visible = distanceSqrd < entityDynamicViewRangeSqrd;
                    if (entityDynamicWallDetection)
                        visible &= EntityUtils.hasLineOfSight(player, entity, 0.75, true);
                } else if (dynamicItemTags && entity.getType() == EntityType.DROPPED_ITEM) {
                    visible = distanceSqrd < itemDynamicViewRangeSqrd;
                    if (itemDynamicWallDetection)
                        visible &= EntityUtils.hasLineOfSight(player, entity, 0.75, true);
                } else if (dynamicBlockTags && entity.getType() == EntityType.ARMOR_STAND) {
                    visible = distanceSqrd < blockSpawnerDynamicViewRangeSqrd;
                    if (blockDynamicWallDetection)
                        visible &= EntityUtils.hasLineOfSight(player, entity, 0.75, true);
                } else continue;

                if (entity.getType() != EntityType.ARMOR_STAND && entity instanceof LivingEntity) {
                    LivingEntity livingEntity = (LivingEntity) entity;
                    StackedEntity stackedEntity = this.getStackedEntity(livingEntity);
                    if (stackedEntity != null)
                        nmsHandler.updateEntityNameTagForPlayer(player, entity, stackedEntity.getDisplayName(), stackedEntity.isDisplayNameVisible() && visible);

                    // Spawn particles for holding the stacking tool
                    if (visible && displayStackingToolParticles) {
                        Location location = entity.getLocation().add(0, livingEntity.getEyeHeight(true) + 0.75, 0);
                        DustOptions dustOptions;
                        if (PersistentDataUtils.isUnstackable(livingEntity)) {
                            dustOptions = StackerUtils.UNSTACKABLE_DUST_OPTIONS;
                        } else {
                            dustOptions = StackerUtils.STACKABLE_DUST_OPTIONS;
                        }
                        player.spawnParticle(Particle.REDSTONE, location, 1, 0.0, 0.0, 0.0, 0.0, dustOptions);
                    }
                } else {
                    nmsHandler.updateEntityNameTagVisibilityForPlayer(player, entity, visible);
                }
            }
        }
    }

    @Override
    public void close() {
        // Cancel tasks
        if (this.entityStackTask != null)
            this.entityStackTask.cancel();

        if (this.itemStackTask != null)
            this.itemStackTask.cancel();

        if (this.nametagTask != null)
            this.nametagTask.cancel();
    }

    @Override
    public Map<UUID, StackedEntity> getStackedEntities() {
        return this.stackedEntities;
    }

    @Override
    public Map<UUID, StackedItem> getStackedItems() {
        return this.stackedItems;
    }

    @Override
    public Map<Block, StackedBlock> getStackedBlocks() {
        return this.stackedBlocks;
    }

    @Override
    public Map<Block, StackedSpawner> getStackedSpawners() {
        return this.stackedSpawners;
    }

    @Override
    public StackedEntity getStackedEntity(LivingEntity livingEntity) {
        return this.stackedEntities.get(livingEntity.getUniqueId());
    }

    @Override
    public StackedItem getStackedItem(Item item) {
        return this.stackedItems.get(item.getUniqueId());
    }

    @Override
    public StackedBlock getStackedBlock(Block block) {
        return this.stackedBlocks.get(block);
    }

    @Override
    public StackedSpawner getStackedSpawner(Block block) {
        return this.stackedSpawners.get(block);
    }

    @Override
    public boolean isEntityStacked(LivingEntity livingEntity) {
        return this.getStackedEntity(livingEntity) != null;
    }

    @Override
    public boolean isItemStacked(Item item) {
        return this.getStackedItem(item) != null;
    }

    @Override
    public boolean isBlockStacked(Block block) {
        return this.getStackedBlock(block) != null;
    }

    @Override
    public boolean isSpawnerStacked(Block block) {
        return this.getStackedSpawner(block) != null;
    }

    @Override
    public void removeEntityStack(StackedEntity stackedEntity) {
        LivingEntity entity = stackedEntity.getEntity();
        if (entity != null) {
            UUID key = stackedEntity.getEntity().getUniqueId();
            this.stackedEntities.remove(key);
        } else {
            // Entity is null so we have to remove by value instead
            for (Entry<UUID, StackedEntity> entry : this.stackedEntities.entrySet()) {
                if (entry.getValue() == stackedEntity) {
                    this.stackedEntities.remove(entry.getKey());
                    break;
                }
            }
        }

        this.stackManager.markStackDeleted(stackedEntity);
    }

    @Override
    public void removeItemStack(StackedItem stackedItem) {
        Item item = stackedItem.getItem();
        if (item != null) {
            UUID key = stackedItem.getItem().getUniqueId();
            this.stackedItems.remove(key);
        } else {
            // Item is null so we have to remove by value instead
            for (Entry<UUID, StackedItem> entry : this.stackedItems.entrySet()) {
                if (entry.getValue() == stackedItem) {
                    this.stackedItems.remove(entry.getKey());
                    break;
                }
            }
        }

        this.stackManager.markStackDeleted(stackedItem);
    }

    @Override
    public void removeBlockStack(StackedBlock stackedBlock) {
        Block key = stackedBlock.getBlock();
        stackedBlock.kickOutGuiViewers();
        if (this.stackedBlocks.containsKey(key)) {
            this.stackedBlocks.remove(key);
            this.stackManager.markStackDeleted(stackedBlock);
        }
    }

    @Override
    public void removeSpawnerStack(StackedSpawner stackedSpawner) {
        Block key = stackedSpawner.getSpawner().getBlock();
        stackedSpawner.kickOutViewers();
        if (this.stackedSpawners.containsKey(key)) {
            this.stackedSpawners.remove(key);
            this.stackManager.markStackDeleted(stackedSpawner);
        }
    }

    @Override
    public int removeAllEntityStacks() {
        List<StackedEntity> toRemove = this.stackedEntities.values().stream()
                .filter(x -> x.getEntity() != null && x.getEntity().getType() != EntityType.PLAYER)
                .filter(x -> x.getStackSize() != 1 || Setting.MISC_CLEARALL_REMOVE_SINGLE.getBoolean())
                .collect(Collectors.toList());

        EntityStackClearEvent entityStackClearEvent = new EntityStackClearEvent(this.targetWorld, toRemove);
        Bukkit.getPluginManager().callEvent(entityStackClearEvent);
        if (entityStackClearEvent.isCancelled())
            return 0;

        toRemove.forEach(this.stackManager::markStackDeleted);
        toRemove.stream().map(StackedEntity::getEntity).forEach(LivingEntity::remove);
        this.stackedEntities.values().removeIf(toRemove::contains);

        return toRemove.size();
    }

    @Override
    public int removeAllItemStacks() {
        List<StackedItem> toRemove = new ArrayList<>(this.stackedItems.values());

        ItemStackClearEvent itemStackClearEvent = new ItemStackClearEvent(this.targetWorld, toRemove);
        Bukkit.getPluginManager().callEvent(itemStackClearEvent);
        if (itemStackClearEvent.isCancelled())
            return 0;

        toRemove.forEach(this.stackManager::markStackDeleted);
        toRemove.stream().map(StackedItem::getItem).forEach(Item::remove);
        this.stackedItems.values().removeIf(toRemove::contains);

        return toRemove.size();
    }

    @Override
    public void updateStackedEntityKey(LivingEntity oldKey, LivingEntity newKey) {
        StackedEntity stackedEntity = this.stackedEntities.get(oldKey.getUniqueId());
        if (stackedEntity != null) {
            this.stackedEntities.remove(oldKey.getUniqueId());
            this.stackedEntities.put(newKey.getUniqueId(), stackedEntity);
        }
    }

    @Override
    public StackedEntity splitEntityStack(StackedEntity stackedEntity) {
        EntityUnstackEvent entityUnstackEvent = new EntityUnstackEvent(stackedEntity, new StackedEntity(stackedEntity.getEntity()));
        Bukkit.getPluginManager().callEvent(entityUnstackEvent);
        if (entityUnstackEvent.isCancelled())
            return null;

        StackedEntity newlySplit = stackedEntity.decreaseStackSize();
        this.stackedEntities.put(newlySplit.getEntity().getUniqueId(), newlySplit);
        return newlySplit;
    }

    @Override
    public StackedItem splitItemStack(StackedItem stackedItem, int newSize) {
        World world = stackedItem.getLocation().getWorld();
        if (world == null)
            return null;

        ItemStack oldItemStack = stackedItem.getItem().getItemStack();
        ItemStack newItemStack = oldItemStack.clone();

        newItemStack.setAmount(newSize);

        stackedItem.getItem().setPickupDelay(60);
        stackedItem.getItem().setTicksLived(1);

        Item newItem = world.dropItemNaturally(stackedItem.getLocation(), newItemStack);
        newItem.setPickupDelay(0);

        StackedItem newStackedItem = new StackedItem(newSize, newItem);
        this.stackedItems.put(newItem.getUniqueId(), newStackedItem);
        stackedItem.increaseStackSize(-newSize);
        return newStackedItem;
    }

    @Override
    public StackedEntity createEntityStack(LivingEntity livingEntity, boolean tryStack) {
        if (!this.stackManager.isEntityStackingEnabled())
            return null;

        if (livingEntity instanceof Player || livingEntity instanceof ArmorStand || NPCsHook.isNPC(livingEntity))
            return null;

        StackedEntity newStackedEntity = new StackedEntity(livingEntity);
        this.stackedEntities.put(livingEntity.getUniqueId(), newStackedEntity);

        if (tryStack && Setting.ENTITY_INSTANT_STACK.getBoolean())
            this.tryStackEntity(newStackedEntity);

        return newStackedEntity;
    }

    @Override
    public StackedItem createItemStack(Item item, boolean tryStack) {
        if (!this.stackManager.isItemStackingEnabled())
            return null;

        StackedItem newStackedItem = new StackedItem(item.getItemStack().getAmount(), item);
        this.stackedItems.put(item.getUniqueId(), newStackedItem);

        if (tryStack)
            this.tryStackItem(newStackedItem);

        return newStackedItem;
    }

    @Override
    public StackedBlock createBlockStack(Block block, int amount) {
        if (!this.stackManager.isBlockStackingEnabled() || !this.stackManager.isBlockTypeStackable(block))
            return null;

        StackedBlock newStackedBlock = new StackedBlock(amount, block);
        this.stackedBlocks.put(block, newStackedBlock);
        return newStackedBlock;
    }

    @Override
    public StackedSpawner createSpawnerStack(Block block, int amount, boolean placedByPlayer) {
        if (block.getType() != Material.SPAWNER)
            return null;

        CreatureSpawner creatureSpawner = (CreatureSpawner) block.getState();
        if (!this.stackManager.isSpawnerStackingEnabled() || !this.stackManager.isSpawnerTypeStackable(creatureSpawner.getSpawnedType()))
            return null;

        StackedSpawner newStackedSpawner = new StackedSpawner(amount, creatureSpawner, placedByPlayer);
        this.stackedSpawners.put(block, newStackedSpawner);
        return newStackedSpawner;
    }

    @Override
    public void addEntityStack(StackedEntity stackedEntity) {
        if (!this.stackManager.isEntityStackingEnabled() || NPCsHook.isNPC(stackedEntity.getEntity()))
            return;

        this.stackedEntities.put(stackedEntity.getEntity().getUniqueId(), stackedEntity);

        if (Setting.ENTITY_INSTANT_STACK.getBoolean())
            this.tryStackEntity(stackedEntity);
    }

    @Override
    public void addItemStack(StackedItem stackedItem) {
        if (!this.stackManager.isItemStackingEnabled())
            return;

        this.stackedItems.put(stackedItem.getItem().getUniqueId(), stackedItem);
        this.tryStackItem(stackedItem);
    }

    @Override
    public void preStackEntities(EntityType entityType, int amount, Location location, SpawnReason spawnReason) {
        World world = location.getWorld();
        if (world == null)
            return;

        Bukkit.getScheduler().runTaskAsynchronously(this.rosePlugin, () -> {
            EntityStackSettings stackSettings = this.rosePlugin.getManager(StackSettingManager.class).getEntityStackSettings(entityType);
            Set<StackedEntity> stackedEntities = new HashSet<>();
            NMSHandler nmsHandler = NMSAdapter.getHandler();
            for (int i = 0; i < amount; i++) {
                LivingEntity entity = nmsHandler.createNewEntityUnspawned(entityType, location);
                StackedEntity newStack = new StackedEntity(entity);
                Optional<StackedEntity> matchingEntity = stackedEntities.stream().filter(x ->
                        stackSettings.testCanStackWith(x, newStack, false, true)).findFirst();
                if (matchingEntity.isPresent()) {
                    matchingEntity.get().increaseStackSize(entity);
                } else {
                    stackedEntities.add(newStack);
                }
            }

            Bukkit.getScheduler().runTask(this.rosePlugin, () -> {
                this.stackManager.setEntityStackingTemporarilyDisabled(true);
                for (StackedEntity stackedEntity : stackedEntities) {
                    LivingEntity entity = stackedEntity.getEntity();
                    this.entityCacheManager.preCacheEntity(entity);
                    nmsHandler.spawnExistingEntity(stackedEntity.getEntity(), SpawnReason.SPAWNER_EGG);
                    entity.setVelocity(Vector.getRandom().multiply(0.01));
                    this.addEntityStack(stackedEntity);
                }
                this.stackManager.setEntityStackingTemporarilyDisabled(false);
            });
        });
    }

    @Override
    public void preStackEntities(EntityType entityType, int amount, Location location) {
        this.preStackEntities(entityType, amount, location, SpawnReason.CUSTOM);
    }

    @Override
    public void preStackItems(Collection<ItemStack> items, Location location) {
        if (location.getWorld() == null)
            return;

        if (!this.stackManager.isItemStackingEnabled()) {
            for (ItemStack item : items)
                location.getWorld().dropItemNaturally(location, item);
            return;
        }

        this.stackManager.setEntityStackingTemporarilyDisabled(true);

        Set<StackedItem> stackedItems = new HashSet<>();
        for (ItemStack itemStack : items) {
            Optional<StackedItem> matchingItem = stackedItems.stream().filter(x ->
                    x.getItem().getItemStack().isSimilar(itemStack) && x.getStackSize() + itemStack.getAmount() <= x.getStackSettings().getMaxStackSize()).findFirst();
            if (matchingItem.isPresent()) {
                matchingItem.get().increaseStackSize(itemStack.getAmount());
            } else {
                Item item = location.getWorld().dropItemNaturally(location, itemStack);
                stackedItems.add(new StackedItem(item.getItemStack().getAmount(), item));
            }
        }

        stackedItems.forEach(this::addItemStack);
        this.stackManager.setEntityStackingTemporarilyDisabled(false);
    }

    /**
     * Tries to stack a StackedEntity with all other StackedEntities
     *
     * @param stackedEntity the StackedEntity to try to stack
     */
    private void tryStackEntity(StackedEntity stackedEntity) {
        EntityStackSettings stackSettings = stackedEntity.getStackSettings();
        if (stackSettings == null || this.stackManager.isMarkedAsDeleted(stackedEntity))
            return;

        if (stackedEntity.checkNPC()) {
            this.removeEntityStack(stackedEntity);
            return;
        }

        LivingEntity entity = stackedEntity.getEntity();
        if (entity == null)
            return;

        if (!WorldGuardHook.testLocation(entity.getLocation()))
            return;

        Collection<Entity> nearbyEntities;
        Predicate<Entity> predicate = x -> x.getType() == entity.getType();
        if (!Setting.ENTITY_MERGE_ENTIRE_CHUNK.getBoolean()) {
            nearbyEntities = this.entityCacheManager.getNearbyEntities(entity.getLocation(), stackSettings.getMergeRadius(), predicate);
        } else {
            nearbyEntities = this.entityCacheManager.getEntitiesInChunk(entity.getLocation(), predicate);
        }

        Set<StackedEntity> targetEntities = new HashSet<>();
        targetEntities.add(stackedEntity);

        for (Entity otherEntity : nearbyEntities) {
            if (entity == otherEntity || !otherEntity.isValid())
                continue;

            StackedEntity other = this.stackedEntities.get(otherEntity.getUniqueId());
            if (other == null || this.stackManager.isMarkedAsDeleted(other))
                continue;

            if (stackSettings.testCanStackWith(stackedEntity, other, false)
                    && (!Setting.ENTITY_REQUIRE_LINE_OF_SIGHT.getBoolean() || EntityUtils.hasLineOfSight(entity, otherEntity, 0.75, false))
                    && WorldGuardHook.testLocation(otherEntity.getLocation()))
                targetEntities.add(other);
        }

        StackedEntity increased;
        int totalSize;
        List<StackedEntity> removable = new ArrayList<>(targetEntities.size());
        if (!Setting.ENTITY_MIN_STACK_COUNT_ONLY_INDIVIDUALS.getBoolean()) {
            increased = targetEntities.stream().max(StackedEntity::compareTo).orElse(stackedEntity);
            targetEntities.remove(increased);
            totalSize = increased.getStackSize();
            for (StackedEntity target : targetEntities) {
                if (totalSize + target.getStackSize() <= stackSettings.getMaxStackSize()) {
                    totalSize += target.getStackSize();
                    removable.add(target);
                }
            }
        } else {
            increased = stackedEntity;
            targetEntities.remove(increased);
            totalSize = 1;
            int totalStackSize = increased.getStackSize();
            for (StackedEntity target : targetEntities) {
                if (totalStackSize + target.getStackSize() <= stackSettings.getMaxStackSize()) {
                    totalSize++;
                    totalStackSize += target.getStackSize();
                    removable.add(target);
                }
            }
        }

        if (removable.isEmpty() || totalSize < stackSettings.getMinStackSize())
            return;

        EntityStackEvent entityStackEvent = new EntityStackEvent(removable, increased);
        Bukkit.getPluginManager().callEvent(entityStackEvent);
        if (entityStackEvent.isCancelled())
            return;

        for (StackedEntity toStack : removable) {
            stackSettings.applyStackProperties(toStack.getEntity(), increased.getEntity());
            increased.increaseStackSize(toStack.getEntity());
            increased.increaseStackSize(toStack.getStackedEntityNBT());
            this.removeEntityStack(toStack);
        }

        Runnable removeTask = () -> removable.stream().map(StackedEntity::getEntity).forEach(Entity::remove);
        if (Bukkit.isPrimaryThread()) {
            removeTask.run();
        } else {
            Bukkit.getScheduler().runTask(this.rosePlugin, removeTask);
        }
    }

    /**
     * Tries to stack a StackedItem with all other StackedItems
     *
     * @param stackedItem the StackedItem to try to stack
     */
    private void tryStackItem(StackedItem stackedItem) {
        ItemStackSettings stackSettings = stackedItem.getStackSettings();
        if (stackSettings == null
                || !stackSettings.isStackingEnabled()
                || this.stackManager.isMarkedAsDeleted(stackedItem)
                || stackedItem.getItem().getPickupDelay() > 40)
            return;

        Item item = stackedItem.getItem();
        Predicate<Entity> predicate = x -> x.getType() == EntityType.DROPPED_ITEM;
        Set<Item> nearbyItems = this.entityCacheManager.getNearbyEntities(stackedItem.getLocation(), Setting.ITEM_MERGE_RADIUS.getDouble(), predicate)
                .stream()
                .map(x -> (Item) x)
                .collect(Collectors.toSet());

        Set<StackedItem> targetItems = new HashSet<>();
        for (Item otherItem : nearbyItems) {
            if (item == otherItem || otherItem.getPickupDelay() > 40 || !item.getItemStack().isSimilar(otherItem.getItemStack()))
                continue;

            StackedItem other = this.stackedItems.get(otherItem.getUniqueId());
            if (other != null && !this.stackManager.isMarkedAsDeleted(other))
                targetItems.add(other);
        }

        int totalSize = stackedItem.getStackSize();
        Set<StackedItem> removable = new HashSet<>();
        for (StackedItem target : targetItems) {
            if (totalSize + target.getStackSize() <= stackSettings.getMaxStackSize()) {
                totalSize += target.getStackSize();
                removable.add(target);
            }
        }

        StackedItem headStack = stackedItem;
        for (StackedItem other : removable) {
            StackedItem increased = headStack.compareTo(other) > 0 ? headStack : other;
            StackedItem removed = increased == headStack ? other : headStack;

            headStack = increased;

            ItemStackEvent itemStackEvent = new ItemStackEvent(removed, increased);
            Bukkit.getPluginManager().callEvent(itemStackEvent);
            if (itemStackEvent.isCancelled())
                continue;

            increased.increaseStackSize(removed.getStackSize());
            increased.getItem().setTicksLived(1); // Reset the 5 minute pickup timer
            removed.getItem().setPickupDelay(100); // Don't allow the item we just merged to get picked up or stacked
            increased.getItem().setPickupDelay(5);

            Runnable removeTask = () -> removed.getItem().remove();
            if (Bukkit.isPrimaryThread()) {
                removeTask.run();
            } else {
                Bukkit.getScheduler().runTask(this.rosePlugin, removeTask);
            }

            this.removeItemStack(removed);
        }
    }

    public void transferExistingEntityStack(UUID entityUUID, StackedEntity stackedEntity, StackingThread toThread) {
        this.stackedEntities.remove(entityUUID);
        toThread.loadExistingEntityStack(entityUUID, stackedEntity);
    }

    public void transferExistingItemStack(UUID itemUUID, StackedItem stackedItem, StackingThread toThread) {
        this.stackedItems.remove(itemUUID);
        toThread.loadExistingItemStack(itemUUID, stackedItem);
    }

    private void loadExistingEntityStack(UUID entityUUID, StackedEntity stackedEntity) {
        stackedEntity.updateEntity();
        this.stackedEntities.put(entityUUID, stackedEntity);
    }

    private void loadExistingItemStack(UUID itemUUID, StackedItem stackedItem) {
        stackedItem.updateItem();
        this.stackedItems.put(itemUUID, stackedItem);
    }

    /**
     * Gets a List of all StackedEntities within the Set of Chunks given and removes them from memory
     *
     * @param chunks to load entities from
     * @return list of StackedEntities
     */
    public List<StackedEntity> getAndClearStackedEntities(Set<Chunk> chunks) {
        List<StackedEntity> stacks = new ArrayList<>();
        Iterator<StackedEntity> iterator = this.stackedEntities.values().iterator();
        while (iterator.hasNext()) {
            StackedEntity stack = iterator.next();
            if (this.containsChunk(chunks, stack)) {
                stacks.add(stack);
                iterator.remove();
            }
        }
        return stacks;
    }

    /**
     * Gets a List of all StackedItems within the Set of Chunks given and removes them from memory
     *
     * @param chunks to load items from
     * @return list of StackedItems
     */
    public List<StackedItem> getAndClearStackedItems(Set<Chunk> chunks) {
        List<StackedItem> stacks = new ArrayList<>();
        Iterator<StackedItem> iterator = this.stackedItems.values().iterator();
        while (iterator.hasNext()) {
            StackedItem stack = iterator.next();
            if (this.containsChunk(chunks, stack)) {
                stacks.add(stack);
                iterator.remove();
            }
        }
        return stacks;
    }

    /**
     * Gets a List of all StackedBlocks within the Set of Chunks given and removes them from memory
     *
     * @param chunks to load blocks from
     * @return list of StackedBlocks
     */
    public List<StackedBlock> getAndClearStackedBlocks(Set<Chunk> chunks) {
        List<StackedBlock> stacks = new ArrayList<>();
        Iterator<StackedBlock> iterator = this.stackedBlocks.values().iterator();
        while (iterator.hasNext()) {
            StackedBlock stack = iterator.next();
            if (this.containsChunk(chunks, stack)) {
                stacks.add(stack);
                iterator.remove();
            }
        }
        return stacks;
    }

    /**
     * Gets a List of all StackedSpawners within the Set of Chunks given and removes them from memory
     *
     * @param chunks to load spawners from
     * @return list of StackedSpawners
     */
    public List<StackedSpawner> getAndClearStackedSpawners(Set<Chunk> chunks) {
        List<StackedSpawner> stacks = new ArrayList<>();
        Iterator<StackedSpawner> iterator = this.stackedSpawners.values().iterator();
        while (iterator.hasNext()) {
            StackedSpawner stack = iterator.next();
            if (this.containsChunk(chunks, stack)) {
                stacks.add(stack);
                iterator.remove();
            }
        }
        return stacks;
    }

    /**
     * Checks if a Stack is contained within a potential Set of Chunks
     *
     * @param chunks the Chunks
     * @param stack the Stack
     * @return true if the Stack is in one of the given Chunks, false otherwise
     */
    private boolean containsChunk(Set<Chunk> chunks, Stack<?> stack) {
        int stackChunkX = stack.getLocation().getBlockX() >> 4;
        int stackChunkZ = stack.getLocation().getBlockZ() >> 4;
        for (Chunk chunk : chunks)
            if (chunk.getX() == stackChunkX && chunk.getZ() == stackChunkZ)
                return true;
        return false;
    }

    /**
     * Used to add a StackedEntity loaded from the database
     *
     * @param stackedEntity to load
     */
    public void putStackedEntity(StackedEntity stackedEntity) {
        this.stackedEntities.put(stackedEntity.getEntity().getUniqueId(), stackedEntity);
    }

    /**
     * Used to add a StackedItem loaded from the database
     *
     * @param stackedItem to load
     */
    public void putStackedItem(StackedItem stackedItem) {
        this.stackedItems.put(stackedItem.getItem().getUniqueId(), stackedItem);
    }

    /**
     * Used to add a StackedBlock loaded from the database
     *
     * @param stackedBlock to load
     */
    public void putStackedBlock(StackedBlock stackedBlock) {
        this.stackedBlocks.put(stackedBlock.getBlock(), stackedBlock);
    }

    /**
     * Used to add a StackedSpawner loaded from the database
     *
     * @param stackedSpawner to load
     */
    public void putStackedSpawner(StackedSpawner stackedSpawner) {
        this.stackedSpawners.put(stackedSpawner.getSpawner().getBlock(), stackedSpawner);
    }

    /**
     * @return the world that this StackingThread is acting on
     */
    public World getTargetWorld() {
        return this.targetWorld;
    }

}
