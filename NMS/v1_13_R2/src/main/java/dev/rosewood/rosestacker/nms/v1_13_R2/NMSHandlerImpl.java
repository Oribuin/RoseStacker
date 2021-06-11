package dev.rosewood.rosestacker.nms.v1_13_R2;

import com.google.common.collect.Lists;
import dev.rosewood.rosestacker.nms.NMSHandler;
import dev.rosewood.rosestacker.nms.object.SpawnerTileWrapper;
import dev.rosewood.rosestacker.nms.v1_13_R2.entity.SoloEntitySpider;
import dev.rosewood.rosestacker.nms.v1_13_R2.object.SpawnerTileWrapperImpl;
import net.minecraft.server.v1_13_R2.*;
import net.minecraft.server.v1_13_R2.DataWatcher.Item;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.craftbukkit.v1_13_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_13_R2.entity.CraftCreeper;
import org.bukkit.craftbukkit.v1_13_R2.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_13_R2.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.v1_13_R2.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_13_R2.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_13_R2.util.CraftChatMessage;
import org.bukkit.craftbukkit.v1_13_R2.util.CraftNamespacedKey;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.inventory.ItemStack;

import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

@SuppressWarnings("unchecked")
public class NMSHandlerImpl implements NMSHandler {

    private static Field field_PacketPlayOutEntityMetadata_a; // Field to set the entity ID for the packet, normally private
    private static Field field_PacketPlayOutEntityMetadata_b; // Field to set the datawatcher changes for the packet, normally private

    private static Method method_WorldServer_b; // Method to register an entity into a world

    private static DataWatcherObject<Boolean> value_EntityCreeper_d; // DataWatcherObject that determines if a creeper is ignited, normally private
    private static Field field_EntityCreeper_fuseTicks; // Field to set the remaining fuse ticks of a creeper, normally private

    private static Field field_PathfinderGoalSelector_b; // Field to get a PathfinderGoalSelector of an insentient entity, normally private
    private static Field field_EntityInsentient_moveController; // Field to set the move controller of an insentient entity, normally protected

    private static Constructor<EntityZombie.GroupDataZombie> constructor_GroupDataZombie; // Constructor to create a GroupDataZombie instance, normally private

    static {
        try {
            field_PacketPlayOutEntityMetadata_a = PacketPlayOutEntityMetadata.class.getDeclaredField("a");
            field_PacketPlayOutEntityMetadata_a.setAccessible(true);

            field_PacketPlayOutEntityMetadata_b = PacketPlayOutEntityMetadata.class.getDeclaredField("b");
            field_PacketPlayOutEntityMetadata_b.setAccessible(true);

            method_WorldServer_b = WorldServer.class.getDeclaredMethod("b", Entity.class);
            method_WorldServer_b.setAccessible(true);

            Field field_EntityCreeper_d = EntityCreeper.class.getDeclaredField("c");
            field_EntityCreeper_d.setAccessible(true);
            value_EntityCreeper_d = (DataWatcherObject<Boolean>) field_EntityCreeper_d.get(null);

            field_EntityCreeper_fuseTicks = EntityCreeper.class.getDeclaredField("fuseTicks");
            field_EntityCreeper_fuseTicks.setAccessible(true);

            field_PathfinderGoalSelector_b = PathfinderGoalSelector.class.getDeclaredField("b");
            field_PathfinderGoalSelector_b.setAccessible(true);

            field_EntityInsentient_moveController = EntityInsentient.class.getDeclaredField("moveController");
            field_EntityInsentient_moveController.setAccessible(true);

            constructor_GroupDataZombie = EntityZombie.GroupDataZombie.class.getDeclaredConstructor(EntityZombie.class, boolean.class);
            constructor_GroupDataZombie.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
        }
    }

    @Override
    public byte[] getEntityAsNBT(LivingEntity livingEntity, boolean includeAttributes) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ObjectOutputStream dataOutput = new ObjectOutputStream(outputStream)) {

            NBTTagCompound nbt = new NBTTagCompound();
            EntityLiving craftEntity = ((CraftLivingEntity) livingEntity).getHandle();
            craftEntity.save(nbt);

            // Don't store attributes, it's pretty large and doesn't usually matter
            if (!includeAttributes)
                nbt.remove("Attributes");

            // Write entity type
            String entityType = IRegistry.ENTITY_TYPE.getKey(craftEntity.P()).toString();
            dataOutput.writeUTF(entityType);

            // Write NBT
            NBTCompressedStreamTools.a(nbt, (OutputStream) dataOutput);

            return outputStream.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    @SuppressWarnings("deprecation")
    @Override
    public LivingEntity createEntityFromNBT(byte[] serialized, Location location, boolean addToWorld, EntityType overwriteType) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(serialized);
             ObjectInputStream dataInput = new ObjectInputStream(inputStream)) {

            // Read entity type
            String entityType = dataInput.readUTF();
            if (overwriteType != null)
                entityType = overwriteType.getName();

            // Read NBT
            NBTTagCompound nbt = NBTCompressedStreamTools.a(dataInput);

            NBTTagList positionTagList = nbt.getList("Pos", 6);
            positionTagList.set(0, new NBTTagDouble(location.getX()));
            positionTagList.set(1, new NBTTagDouble(location.getY()));
            positionTagList.set(2, new NBTTagDouble(location.getZ()));
            nbt.set("Pos", positionTagList);
            nbt.a("UUID", UUID.randomUUID()); // Reset the UUID to resolve possible duplicates

            EntityTypes<?> entityTypes = EntityTypes.a(entityType);
            if (entityTypes != null) {
                WorldServer world = ((CraftWorld) location.getWorld()).getHandle();

                Entity entity = this.createCreature(
                        entityTypes,
                        world,
                        nbt,
                        null,
                        null,
                        new BlockPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ()),
                        true
                );

                if (entity == null)
                    throw new NullPointerException("Unable to create entity from NBT");

                if (addToWorld) {
                    Chunk chunk = world.getChunkAt(MathHelper.floor(entity.locX / 16.0D), MathHelper.floor(entity.locZ / 16.0D));
                    if (chunk == null)
                        throw new NullPointerException("Unable to spawn entity from NBT, couldn't get chunk");

                    chunk.a(entity);
                    world.entityList.add(entity);
                    method_WorldServer_b.invoke(world, entity);
                }

                // Load NBT
                entity.f(nbt);

                return (LivingEntity) entity.getBukkitEntity();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public LivingEntity createNewEntityUnspawned(EntityType entityType, Location location) {
        World world = location.getWorld();
        if (world == null)
            return null;

        Class<? extends org.bukkit.entity.Entity> entityClass = entityType.getEntityClass();
        if (entityClass == null || !LivingEntity.class.isAssignableFrom(entityClass))
            throw new IllegalArgumentException("EntityType must be of a LivingEntity");

        EntityTypes<? extends Entity> nmsEntityType = IRegistry.ENTITY_TYPE.get(CraftNamespacedKey.toMinecraft(NamespacedKey.minecraft(entityType.getName())));
        if (nmsEntityType == null)
            return null;

        Entity nmsEntity = this.createCreature(
                nmsEntityType,
                ((CraftWorld) world).getHandle(),
                null,
                null,
                null,
                new BlockPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ()),
                false
        );

        return nmsEntity == null ? null : (LivingEntity) nmsEntity.getBukkitEntity();
    }

    /**
     * Duplicate of {@link EntityTypes#b(net.minecraft.server.v1_13_R2.World, NBTTagCompound, IChatBaseComponent, EntityHuman, BlockPosition, boolean, boolean)}
     * Contains a patch to prevent chicken jockeys from spawning and to not play the mob sound upon creation.
     */
    private <T extends Entity> T createCreature(EntityTypes<T> entityTypes, net.minecraft.server.v1_13_R2.World worldserver, NBTTagCompound nbttagcompound, IChatBaseComponent ichatbasecomponent, EntityHuman entityhuman, BlockPosition blockposition, boolean flag) {
        T newEntity;
        if (entityTypes == EntityTypes.SPIDER) {
            newEntity = (T) new SoloEntitySpider((EntityTypes<? extends EntitySpider>) entityTypes, worldserver);
        } else {
            newEntity = entityTypes.a(worldserver);
        }

        if (newEntity == null) {
            return null;
        } else {
            newEntity.setPositionRotation(blockposition.getX() + 0.5D, blockposition.getY(), blockposition.getZ() + 0.5D, MathHelper.g(worldserver.random.nextFloat() * 360.0F), 0.0F);
            if (newEntity instanceof EntityInsentient) {
                EntityInsentient entityinsentient = (EntityInsentient)newEntity;
                entityinsentient.aS = entityinsentient.yaw;
                entityinsentient.aQ = entityinsentient.yaw;

                GroupDataEntity groupDataEntity = null;
                if (entityTypes == EntityTypes.DROWNED
                        || entityTypes == EntityTypes.HUSK
                        || entityTypes == EntityTypes.ZOMBIE_VILLAGER
                        || entityTypes == EntityTypes.ZOMBIE_PIGMAN
                        || entityTypes == EntityTypes.ZOMBIE) {
                    // Don't allow chicken jockeys to spawn
                    try {
                        groupDataEntity = constructor_GroupDataZombie.newInstance(newEntity, false);
                    } catch (ReflectiveOperationException ex) {
                        ex.printStackTrace();
                    }
                }

                entityinsentient.prepare(worldserver.getDamageScaler(new BlockPosition(entityinsentient)), groupDataEntity, nbttagcompound);
            }

            if (ichatbasecomponent != null && newEntity instanceof EntityLiving) {
                newEntity.setCustomName(ichatbasecomponent);
            }

            try {
                EntityTypes.a(worldserver, entityhuman, newEntity, nbttagcompound);
            } catch (Throwable ignored) { }

            return newEntity;
        }
    }

    @Override
    public void spawnExistingEntity(LivingEntity entity, SpawnReason spawnReason) {
        Location location = entity.getLocation();
        World world = location.getWorld();
        if (world == null)
            throw new IllegalArgumentException("Entity is not in a loaded world");

        ((CraftWorld) world).getHandle().addEntity(((CraftEntity) entity).getHandle(), spawnReason);
    }

    @Override
    public LivingEntity spawnEntityWithReason(EntityType entityType, Location location, SpawnReason spawnReason) {
        World world = location.getWorld();
        if (world == null)
            throw new IllegalArgumentException("Cannot spawn into null world");

        Class<? extends org.bukkit.entity.Entity> entityClass = entityType.getEntityClass();
        if (entityClass == null || !LivingEntity.class.isAssignableFrom(entityClass))
            throw new IllegalArgumentException("EntityType must be of a LivingEntity");

        CraftWorld craftWorld = (CraftWorld) world;
        return (LivingEntity) craftWorld.spawn(location, entityClass, null, spawnReason);
    }

    @Override
    public void updateEntityNameTagForPlayer(Player player, org.bukkit.entity.Entity entity, String customName, boolean customNameVisible) {
        try {
            List<Item<?>> dataWatchers = new ArrayList<>();
            Optional<IChatBaseComponent> nameComponent = Optional.ofNullable(CraftChatMessage.fromStringOrNull(customName));
            dataWatchers.add(new DataWatcher.Item<>(DataWatcherRegistry.f.a(2), nameComponent));
            dataWatchers.add(new DataWatcher.Item<>(DataWatcherRegistry.i.a(3), customNameVisible));

            PacketPlayOutEntityMetadata packetPlayOutEntityMetadata = new PacketPlayOutEntityMetadata();
            field_PacketPlayOutEntityMetadata_a.set(packetPlayOutEntityMetadata, entity.getEntityId());
            field_PacketPlayOutEntityMetadata_b.set(packetPlayOutEntityMetadata, dataWatchers);

            ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packetPlayOutEntityMetadata);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void updateEntityNameTagVisibilityForPlayer(Player player, org.bukkit.entity.Entity entity, boolean customNameVisible) {
        try {
            PacketPlayOutEntityMetadata packetPlayOutEntityMetadata = new PacketPlayOutEntityMetadata();
            field_PacketPlayOutEntityMetadata_a.set(packetPlayOutEntityMetadata, entity.getEntityId());
            field_PacketPlayOutEntityMetadata_b.set(packetPlayOutEntityMetadata, Lists.newArrayList(new DataWatcher.Item<>(DataWatcherRegistry.i.a(3), customNameVisible)));

            ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packetPlayOutEntityMetadata);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void unigniteCreeper(Creeper creeper) {
        EntityCreeper entityCreeper = ((CraftCreeper) creeper).getHandle();

        entityCreeper.getDataWatcher().set(value_EntityCreeper_d, false);
        try {
            field_EntityCreeper_fuseTicks.set(entityCreeper, entityCreeper.maxFuseTicks);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void removeEntityGoals(LivingEntity livingEntity) {
        EntityLiving nmsEntity = ((CraftLivingEntity) livingEntity).getHandle();
        if (!(nmsEntity instanceof EntityInsentient))
            return;

        try {
            EntityInsentient insentient = (EntityInsentient) nmsEntity;

            // Remove all goal AI
            ((Set<?>) field_PathfinderGoalSelector_b.get(insentient.goalSelector)).clear();

            // Remove all targetting AI
            ((Set<?>) field_PathfinderGoalSelector_b.get(insentient.targetSelector)).clear();

            // Forget any existing targets
            insentient.setGoalTarget(null);

            // Remove the move controller and replace it with a dummy one
            ControllerMove dummyMoveController = new ControllerMove(insentient) {
                @Override
                public void a() { }
            };

            field_EntityInsentient_moveController.set(insentient, dummyMoveController);
        } catch (ReflectiveOperationException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public ItemStack setItemStackNBT(ItemStack itemStack, String key, String value) {
        net.minecraft.server.v1_13_R2.ItemStack nmsItem = CraftItemStack.asNMSCopy(itemStack);
        NBTTagCompound tagCompound = nmsItem.getOrCreateTag();
        tagCompound.setString(key, value);
        return CraftItemStack.asBukkitCopy(nmsItem);
    }

    @Override
    public ItemStack setItemStackNBT(ItemStack itemStack, String key, int value) {
        net.minecraft.server.v1_13_R2.ItemStack nmsItem = CraftItemStack.asNMSCopy(itemStack);
        NBTTagCompound tagCompound = nmsItem.getOrCreateTag();
        tagCompound.setInt(key, value);
        return CraftItemStack.asBukkitCopy(nmsItem);
    }

    @Override
    public String getItemStackNBTString(ItemStack itemStack, String key) {
        net.minecraft.server.v1_13_R2.ItemStack nmsItem = CraftItemStack.asNMSCopy(itemStack);
        NBTTagCompound tagCompound = nmsItem.getOrCreateTag();
        return tagCompound.getString(key);
    }

    @Override
    public int getItemStackNBTInt(ItemStack itemStack, String key) {
        net.minecraft.server.v1_13_R2.ItemStack nmsItem = CraftItemStack.asNMSCopy(itemStack);
        NBTTagCompound tagCompound = nmsItem.getOrCreateTag();
        return tagCompound.getInt(key);
    }

    @Override
    public SpawnerTileWrapper getSpawnerTile(CreatureSpawner spawner) {
        return new SpawnerTileWrapperImpl(spawner);
    }

}
