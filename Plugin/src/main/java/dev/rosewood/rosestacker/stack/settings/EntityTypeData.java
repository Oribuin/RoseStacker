package dev.rosewood.rosestacker.stack.settings;

import dev.rosewood.rosestacker.utils.ItemUtils;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Contains data loaded from entity_data.json
 */
public class EntityTypeData {

    private final boolean isSwimmingMob;
    private final boolean isFlyingMob;
    private final Material spawnEggMaterial;
    private final List<String> defaultSpawnRequirements;
    private final String skullTexture;
    private final List<Material> breedingMaterials;

    public EntityTypeData(boolean isSwimmingMob, boolean isFlyingMob, Material spawnEggMaterial, List<String> defaultSpawnRequirements, String skullTexture, List<Material> breedingMaterials) {
        this.isSwimmingMob = isSwimmingMob;
        this.isFlyingMob = isFlyingMob;
        this.spawnEggMaterial = spawnEggMaterial;
        this.defaultSpawnRequirements = defaultSpawnRequirements;
        this.skullTexture = skullTexture;
        this.breedingMaterials = breedingMaterials;
    }

    public boolean isSwimmingMob() {
        return this.isSwimmingMob;
    }

    public boolean isFlyingMob() {
        return this.isFlyingMob;
    }

    public Material getSpawnEggMaterial() {
        return this.spawnEggMaterial;
    }

    public List<String> getDefaultSpawnRequirements() {
        return this.defaultSpawnRequirements;
    }

    public ItemStack getSkullItem() {
        return ItemUtils.getCustomSkull(this.skullTexture);
    }

    public boolean isValidBreedingMaterial(Material material) {
        return this.breedingMaterials.contains(material);
    }

}
