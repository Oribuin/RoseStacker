package dev.rosewood.rosestacker.nms.v1_17_R1;

import dev.rosewood.rosestacker.nms.NMSHandler;
import dev.rosewood.rosestacker.nms.object.SpawnerTileWrapper;
import dev.rosewood.rosestacker.nms.v1_17_R1.entity.SoloEntitySpider;
import dev.rosewood.rosestacker.nms.v1_17_R1.entity.SoloEntityStrider;
import dev.rosewood.rosestacker.nms.v1_17_R1.object.SpawnerTileWrapperImpl;
import net.minecraft.core.BlockPosition;
import net.minecraft.core.IRegistry;
import net.minecraft.nbt.NBTCompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.server.level.WorldServer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.control.ControllerMove;
import net.minecraft.world.entity.ai.goal.PathfinderGoalFloat;
import net.minecraft.world.entity.ai.goal.PathfinderGoalSelector;
import net.minecraft.world.entity.ai.goal.PathfinderGoalWrapped;
import net.minecraft.world.entity.monster.EntityCreeper;
import net.minecraft.world.entity.monster.EntitySpider;
import net.minecraft.world.entity.monster.EntityStrider;
import net.minecraft.world.entity.monster.EntityZombie;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.level.chunk.IChunkAccess;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.craftbukkit.v1_17_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_17_R1.entity.CraftCreeper;
import org.bukkit.craftbukkit.v1_17_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_17_R1.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.v1_17_R1.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_17_R1.util.CraftNamespacedKey;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.inventory.ItemStack;

import java.io.*;
import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("unchecked")
public class NMSHandlerImpl implements NMSHandler {

    private static DataWatcherObject<Boolean> value_EntityCreeper_d; // DataWatcherObject that determines if a creeper is ignited, normally private
    private static Field field_EntityCreeper_fuseTicks; // Field to set the remaining fuse ticks of a creeper, normally private

    private static Field field_PathfinderGoalSelector_d; // Field to get a PathfinderGoalSelector of an insentient entity, normally private
    private static Field field_EntityInsentient_moveController; // Field to set the move controller of an insentient entity, normally protected

    static {
        try {
            Field field_EntityCreeper_d = EntityCreeper.class.getDeclaredField("d");
            field_EntityCreeper_d.setAccessible(true);
            value_EntityCreeper_d = (DataWatcherObject<Boolean>) field_EntityCreeper_d.get(null);

            field_EntityCreeper_fuseTicks = EntityCreeper.class.getDeclaredField("bS");
            field_EntityCreeper_fuseTicks.setAccessible(true);

            field_PathfinderGoalSelector_d = PathfinderGoalSelector.class.getDeclaredField("d");
            field_PathfinderGoalSelector_d.setAccessible(true);

            field_EntityInsentient_moveController = EntityInsentient.class.getDeclaredField("bL");
            field_EntityInsentient_moveController.setAccessible(true);
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
            String entityType = IRegistry.Y.getKey(craftEntity.getEntityType()).toString();
            dataOutput.writeUTF(entityType);

            // Write NBT
            NBTCompressedStreamTools.a(nbt, (OutputStream) dataOutput);

            return outputStream.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public LivingEntity createEntityFromNBT(byte[] serialized, Location location, boolean addToWorld, EntityType overwriteType) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(serialized);
             ObjectInputStream dataInput = new ObjectInputStream(inputStream)) {

            // Read entity type
            String entityType = dataInput.readUTF();
            if (overwriteType != null)
                entityType = overwriteType.getKey().getKey();

            // Read NBT
            NBTTagCompound nbt = NBTCompressedStreamTools.a((InputStream) dataInput);

            NBTTagList positionTagList = nbt.getList("Pos", 6);
            positionTagList.set(0, NBTTagDouble.a(location.getX()));
            positionTagList.set(1, NBTTagDouble.a(location.getY()));
            positionTagList.set(2, NBTTagDouble.a(location.getZ()));
            nbt.set("Pos", positionTagList);
            nbt.a("UUID", UUID.randomUUID()); // Reset the UUID to resolve possible duplicates

            Optional<EntityTypes<?>> optionalEntity = EntityTypes.a(entityType);
            if (optionalEntity.isPresent()) {
                WorldServer world = ((CraftWorld) location.getWorld()).getHandle();

                Entity entity = this.createCreature(
                        optionalEntity.get(),
                        world,
                        nbt,
                        null,
                        null,
                        new BlockPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ()),
                        EnumMobSpawn.n,
                        true
                );

                if (entity == null)
                    throw new NullPointerException("Unable to create entity from NBT");

                if (addToWorld) {
                    IChunkAccess ichunkaccess = world.getChunkAt(MathHelper.floor(entity.locX() / 16.0D), MathHelper.floor(entity.locZ() / 16.0D));
                    if (ichunkaccess == null)
                        throw new NullPointerException("Unable to spawn entity from NBT, couldn't get chunk");

                    ichunkaccess.a(entity);
                    world.addEntity(entity);
                }

                // Load NBT
                entity.load(nbt);

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

        EntityTypes<? extends Entity> nmsEntityType = IRegistry.Y.get(CraftNamespacedKey.toMinecraft(entityType.getKey()));
        Entity nmsEntity = this.createCreature(
                nmsEntityType,
                ((CraftWorld) world).getHandle(),
                null,
                null,
                null,
                new BlockPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ()),
                EnumMobSpawn.m,
                false
        );

        return nmsEntity == null ? null : (LivingEntity) nmsEntity.getBukkitEntity();
    }

    /**
     * Duplicate of {@link EntityTypes#createCreature(WorldServer, NBTTagCompound, IChatBaseComponent, EntityHuman, BlockPosition, EnumMobSpawn, boolean, boolean)}.
     * Contains a patch to prevent chicken jockeys from spawning and to not play the mob sound upon creation.
     */
    private <T extends Entity> T createCreature(EntityTypes<T> entityTypes, WorldServer worldserver, NBTTagCompound nbttagcompound, IChatBaseComponent ichatbasecomponent, EntityHuman entityhuman, BlockPosition blockposition, EnumMobSpawn enummobspawn, boolean flag) {
        T newEntity;
        if (entityTypes == EntityTypes.aI) {
            newEntity = (T) new SoloEntitySpider((EntityTypes<? extends EntitySpider>) entityTypes, worldserver);
        } else if (entityTypes == EntityTypes.aL) {
            newEntity = (T) new SoloEntityStrider((EntityTypes<? extends EntityStrider>) entityTypes, worldserver);
        } else {
            newEntity = entityTypes.a(worldserver);
        }

        if (newEntity == null) {
            return null;
        } else {

            newEntity.setPositionRotation(blockposition.getX() + 0.5D, blockposition.getY(), blockposition.getZ() + 0.5D, MathHelper.g(worldserver.w.nextFloat() * 360.0F), 0.0F);
            if (newEntity instanceof EntityInsentient) {
                EntityInsentient entityinsentient = (EntityInsentient) newEntity;
                entityinsentient.aZ = entityinsentient.getYRot();
                entityinsentient.aX = entityinsentient.getYRot();

                GroupDataEntity groupDataEntity = null;
                if (entityTypes == EntityTypes.s
                        || entityTypes == EntityTypes.N
                        || entityTypes == EntityTypes.bg
                        || entityTypes == EntityTypes.bh
                        || entityTypes == EntityTypes.be) {
                    // Don't allow chicken jockeys to spawn
                    groupDataEntity = new EntityZombie.GroupDataZombie(EntityZombie.a(worldserver.getRandom()), false);
                }

                entityinsentient.prepare(worldserver, worldserver.getDamageScaler(entityinsentient.getChunkCoordinates()), enummobspawn, groupDataEntity, nbttagcompound);
            }

            if (ichatbasecomponent != null && newEntity instanceof EntityLiving) {
                newEntity.setCustomName(ichatbasecomponent);
            }

            try {
                EntityTypes.a(worldserver, entityhuman, newEntity, nbttagcompound);
            } catch (Throwable ignored) {
            }

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

        // TODO, Fix this
        try {
//            List<DataWatcher.Item<?>> dataWatchers = new ArrayList<>();
//            Optional<IChatBaseComponent> nameComponent = Optional.ofNullable(CraftChatMessage.fromStringOrNull(customName));
//            dataWatchers.add(new DataWatcher.Item<>(DataWatcherRegistry.f.a(2), nameComponent));
//            dataWatchers.add(new DataWatcher.Item<>(DataWatcherRegistry.i.a(3), customNameVisible));
//
//            ((CraftPlayer) player).getHandle().b.sendPacket(new PacketPlayOutEntityMetadata(entity.getEntityId(), DataWatcher.a(dataWatchers, null), true));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void updateEntityNameTagVisibilityForPlayer(Player player, org.bukkit.entity.Entity entity, boolean customNameVisible) {

        // TODO Fix this
        try {

//            PacketPlayOutEntityMetadata packetPlayOutEntityMetadata = new PacketPlayOutEntityMetadata(entity.getEntityId(), DataWatcher., true);
//            field_PacketPlayOutEntityMetadata_a.set(packetPlayOutEntityMetadata, entity.getEntityId());
//            field_PacketPlayOutEntityMetadata_b.set(packetPlayOutEntityMetadata, Lists.newArrayList(new DataWatcher.Item<>(DataWatcherRegistry.i.a(3), customNameVisible)));
//
//            ((CraftPlayer) player).getHandle().b.sendPacket(packetPlayOutEntityMetadata);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void unigniteCreeper(Creeper creeper) {
        EntityCreeper entityCreeper = ((CraftCreeper) creeper).getHandle();

        entityCreeper.getDataWatcher().set(value_EntityCreeper_d, false);
        try {
            field_EntityCreeper_fuseTicks.set(entityCreeper, entityCreeper.bT);
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

            // Remove all goal AI other than floating in water
            Set<PathfinderGoalWrapped> goals = (Set<PathfinderGoalWrapped>) field_PathfinderGoalSelector_d.get(insentient.bO);
            Iterator<PathfinderGoalWrapped> goalsIterator = goals.iterator();
            while (goalsIterator.hasNext()) {
                PathfinderGoalWrapped goal = goalsIterator.next();
                if (goal.j() instanceof PathfinderGoalFloat)
                    continue;

                goalsIterator.remove();
            }

            // Remove all targetting AI
            ((Set<PathfinderGoalWrapped>) field_PathfinderGoalSelector_d.get(insentient.bP)).clear();

            // Forget any existing targets
            insentient.setGoalTarget(null);

            // Remove the move controller and replace it with a dummy one
            ControllerMove dummyMoveController = new ControllerMove(insentient) {
                @Override
                public void a() {
                }
            };

            field_EntityInsentient_moveController.set(insentient, dummyMoveController);
        } catch (ReflectiveOperationException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public ItemStack setItemStackNBT(ItemStack itemStack, String key, String value) {
        net.minecraft.world.item.ItemStack nmsItem = CraftItemStack.asNMSCopy(itemStack);
        NBTTagCompound tagCompound = nmsItem.getOrCreateTag();
        tagCompound.setString(key, value);
        return CraftItemStack.asBukkitCopy(nmsItem);
    }

    @Override
    public ItemStack setItemStackNBT(ItemStack itemStack, String key, int value) {
        net.minecraft.world.item.ItemStack nmsItem = CraftItemStack.asNMSCopy(itemStack);
        NBTTagCompound tagCompound = nmsItem.getOrCreateTag();
        tagCompound.setInt(key, value);
        return CraftItemStack.asBukkitCopy(nmsItem);
    }

    @Override
    public String getItemStackNBTString(ItemStack itemStack, String key) {
        net.minecraft.world.item.ItemStack nmsItem = CraftItemStack.asNMSCopy(itemStack);
        NBTTagCompound tagCompound = nmsItem.getOrCreateTag();
        return tagCompound.getString(key);
    }

    @Override
    public int getItemStackNBTInt(ItemStack itemStack, String key) {
        net.minecraft.world.item.ItemStack nmsItem = CraftItemStack.asNMSCopy(itemStack);
        NBTTagCompound tagCompound = nmsItem.getOrCreateTag();
        return tagCompound.getInt(key);
    }

    @Override
    public SpawnerTileWrapper getSpawnerTile(CreatureSpawner spawner) {
        return new SpawnerTileWrapperImpl(spawner);
    }

}
