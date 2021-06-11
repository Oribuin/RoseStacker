package dev.rosewood.rosestacker.nms.v1_17_R1.object;

import dev.rosewood.rosestacker.nms.object.SpawnerTileWrapper;
import net.minecraft.resources.MinecraftKey;
import net.minecraft.server.level.WorldServer;
import net.minecraft.world.level.MobSpawnerAbstract;
import net.minecraft.world.level.block.entity.TileEntityMobSpawner;
import org.bukkit.NamespacedKey;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.craftbukkit.v1_17_R1.block.CraftBlock;
import org.bukkit.craftbukkit.v1_17_R1.util.CraftNamespacedKey;
import org.bukkit.entity.EntityType;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class SpawnerTileWrapperImpl implements SpawnerTileWrapper {

    private final TileEntityMobSpawner tileEntity;
    private final MobSpawnerAbstract spawner;
    private final WorldServer world;

    public SpawnerTileWrapperImpl(CreatureSpawner spawner) {
        CraftBlock block = (CraftBlock) spawner.getBlock();
        this.world = block.getCraftWorld().getHandle();
        this.tileEntity = (TileEntityMobSpawner) this.world.getTileEntity(block.getPosition());
        if (this.tileEntity == null)
            throw new IllegalStateException("CreatureSpawner at " + spawner.getLocation() + " no longer exists!");

        this.spawner = this.tileEntity.getSpawner();
    }

    @Override
    public int getDelay() {
        return this.spawner.d;
    }

    @Override
    public void setDelay(int delay) {
        this.spawner.d = delay;
        this.update();
    }

    @Override
    public int getMinSpawnDelay() {
        return this.spawner.i;
    }

    @Override
    public void setMinSpawnDelay(int delay) {
        this.spawner.i = delay;
        this.update();
    }

    @Override
    public int getMaxSpawnDelay() {
        return this.spawner.j;
    }

    @Override
    public void setMaxSpawnDelay(int delay) {
        this.spawner.j = delay;
        this.update();
    }

    @Override
    public int getSpawnCount() {
        return this.spawner.k;
    }

    @Override
    public void setSpawnCount(int spawnCount) {
        this.spawner.k = spawnCount;
        this.update();
    }

    @Override
    public int getMaxNearbyEntities() {
        return this.spawner.m;
    }

    @Override
    public void setMaxNearbyEntities(int maxNearbyEntities) {
        this.spawner.m = maxNearbyEntities;
        this.update();
    }

    @Override
    public int getRequiredPlayerRange() {
        return this.spawner.n;
    }

    @Override
    public void setRequiredPlayerRange(int requiredPlayerRange) {
        this.spawner.n = requiredPlayerRange;
        this.update();
    }

    @Override
    public int getSpawnRange() {
        return this.spawner.o;
    }

    @Override
    public void setSpawnRange(int spawnRange) {
        this.spawner.o = spawnRange;
        this.update();
    }

    @Override
    public List<EntityType> getSpawnedTypes() {
        return this.spawner.e.d().stream()
                .map(x -> x.getEntity().getString("id"))
                .map(MinecraftKey::a)
                .filter(Objects::nonNull)
                .map(CraftNamespacedKey::fromMinecraft)
                .map(this::fromKey)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private EntityType fromKey(NamespacedKey namespacedKey) {
        return Arrays.stream(EntityType.values())
                .filter(x -> x != EntityType.UNKNOWN)
                .filter(x -> x.getKey().equals(namespacedKey))
                .findFirst()
                .orElse(null);
    }

    public void update() {
        this.tileEntity.update();
        this.world.notify(this.tileEntity.getPosition(), this.tileEntity.getBlock(), this.tileEntity.getBlock(), 3);
    }

    public MobSpawnerAbstract getHandle() {
        return this.spawner;
    }

}
