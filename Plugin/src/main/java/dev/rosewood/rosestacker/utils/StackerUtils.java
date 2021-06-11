package dev.rosewood.rosestacker.utils;

import dev.rosewood.rosestacker.RoseStacker;
import dev.rosewood.rosestacker.manager.ConfigurationManager.Setting;
import dev.rosewood.rosestacker.manager.LocaleManager;
import dev.rosewood.rosestacker.nms.NMSAdapter;
import dev.rosewood.rosestacker.nms.NMSHandler;
import dev.rosewood.rosestacker.stack.StackedEntity;
import org.apache.commons.lang.WordUtils;
import org.bukkit.*;
import org.bukkit.Particle.DustOptions;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class StackerUtils {

    public static final String MAX_SUPPORTED_VERSION = "1.16.5";
    public static final String MAX_SUPPORTED_LOCALE_VERSION = "1.16.5";

    public static final int ASSUMED_ENTITY_VISIBILITY_RANGE = 75 * 75;

    public static final DustOptions STACKABLE_DUST_OPTIONS = new DustOptions(Color.fromRGB(0x00FF00), 1.5F);
    public static final DustOptions UNSTACKABLE_DUST_OPTIONS = new DustOptions(Color.fromRGB(0xFF0000), 1.5F);

    private static final Random RANDOM = new Random();
    private static List<EntityType> cachedAlphabeticalEntityTypes;
    private static Set<EntityType> cachedStackableEntityTypes;

    private static NumberFormat formatter = NumberFormat.getInstance();

    /**
     * Formats a string from THIS_FORMAT to This Format
     *
     * @param name The name to format
     * @return the reformatted string
     */
    public static String formatName(String name) {
        return WordUtils.capitalizeFully(name.toLowerCase().replace('_', ' '));
    }

    /**
     * Formats a Material name from THIS_FORMAT to This Format
     *
     * @param material The material to format the name of
     * @return the reformatted string
     */
    public static String formatMaterialName(Material material) {
        if (material == Material.TNT)
            return "TNT"; // The one exception
        return formatName(material.name());
    }

    /**
     * Gets a location as a string key
     *
     * @param location The location
     * @return the location as a string key
     */
    public static String locationAsKey(Location location) {
        return String.format("%s-%.2f-%.2f-%.2f", location.getWorld().getName(), location.getX(), location.getY(), location.getZ());
    }

    /**
     * Gets a random value between the given range, inclusively
     *
     * @param min The minimum value
     * @param max The maximum value
     * @return A value between the min and max, inclusively
     */
    public static int randomInRange(int min, int max) {
        if (min == max)
            return min;

        if (min > max) {
            int temp = min;
            min = max;
            max = temp;
        }
        return RANDOM.nextInt(max - min + 1) + min;
    }

    public static List<EntityType> getAlphabeticalStackableEntityTypes() {
        if (cachedAlphabeticalEntityTypes != null)
            return cachedAlphabeticalEntityTypes;

        return cachedAlphabeticalEntityTypes = Stream.of(EntityType.values())
                .filter(EntityType::isAlive)
                .filter(EntityType::isSpawnable)
                .filter(x -> x != EntityType.PLAYER && x != EntityType.ARMOR_STAND)
                .sorted(Comparator.comparing(Enum::name))
                .collect(Collectors.toList());
    }

    public static Set<EntityType> getStackableEntityTypes() {
        if (cachedStackableEntityTypes != null)
            return cachedStackableEntityTypes;

        return cachedStackableEntityTypes = Stream.of(EntityType.values())
                .filter(EntityType::isAlive)
                .filter(EntityType::isSpawnable)
                .filter(x -> x != EntityType.PLAYER && x != EntityType.ARMOR_STAND)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(EntityType.class)));
    }

    public static boolean passesChance(double chance) {
        return RANDOM.nextDouble() <= chance;
    }

    /**
     * Drops experience at a given location
     *
     * @param location to spawn experience
     * @param lowerBound minimum amount to drop
     * @param upperBound maximum amount to drop
     * @param step the max size an orb can be, will drop multiple orbs if this is exceeded
     */
    public static void dropExperience(Location location, int lowerBound, int upperBound, int step) {
        World world = location.getWorld();
        if (world == null)
            return;

        int experience = RANDOM.nextInt(upperBound - lowerBound + 1) + lowerBound;

        while (experience > step) {
            world.spawn(location.clone().add(RANDOM.nextDouble() - 0.5, RANDOM.nextDouble() - 0.5, RANDOM.nextDouble() - 0.5), ExperienceOrb.class, x -> x.setExperience(step));
            experience -= step;
        }

        if (experience > 0) {
            int fExperience = experience;
            world.spawn(location.clone().add(RANDOM.nextDouble() - 0.5, RANDOM.nextDouble() - 0.5, RANDOM.nextDouble() - 0.5), ExperienceOrb.class, x -> x.setExperience(fExperience));
        }
    }

    /**
     * @return a stream of all block materials that can be considered to be used for stacked blocks
     */
    public static List<Material> getPossibleStackableBlockMaterials() {
        Inventory inventory = Bukkit.createInventory(null, 9);
        return Arrays.stream(Material.values())
                .filter(Material::isBlock)
                .filter(Material::isSolid)
                .filter(x -> !x.isInteractable() || x == Material.TNT)
                .filter(x -> !x.hasGravity())
                .filter(x -> !Tag.CORAL_PLANTS.isTagged(x))
                .filter(x -> !Tag.SLABS.isTagged(x))
                .filter(x -> !Tag.BANNERS.isTagged(x))
                .filter(x -> !x.name().endsWith("_WALL")) // Tags for these don't exist in older versions
                .filter(x -> !x.name().endsWith("_PRESSURE_PLATE"))
                .filter(x -> {
            inventory.setItem(0, new ItemStack(x));
            return inventory.getItem(0) != null && x != Material.SPAWNER;
        }).sorted(Comparator.comparing(Enum::name)).collect(Collectors.toList());
    }

    public static List<LivingEntity> deconstructStackedEntities(StackedEntity stackedEntity) {
        List<byte[]> nbtList = new LinkedList<>(stackedEntity.getStackedEntityNBT());
        List<LivingEntity> livingEntities = new ArrayList<>(nbtList.size());
        Location location = stackedEntity.getLocation();

        NMSHandler nmsHandler = NMSAdapter.getHandler();
        for (byte[] nbt : nbtList)
            livingEntities.add(nmsHandler.createEntityFromNBT(nbt, location, false));

        return livingEntities;
    }

    public static void reconstructStackedEntities(StackedEntity stackedEntity, List<? extends LivingEntity> livingEntities) {
        List<byte[]> nbtList = Collections.synchronizedList(new LinkedList<>());

        NMSHandler nmsHandler = NMSAdapter.getHandler();
        for (LivingEntity livingEntity : livingEntities)
            nbtList.add(nmsHandler.getEntityAsNBT(livingEntity, Setting.ENTITY_SAVE_ATTRIBUTES.getBoolean()));

        stackedEntity.setStackedEntityNBT(nbtList);
    }

    /**
     * Checks if a Material can not be passed through
     *
     * @param material The Material to check
     * @return true if the Material can be passed through, false otherwise
     */
    public static boolean isOccluding(Material material) {
        if (material.name().endsWith("_STAINED_GLASS")
                || material.name().endsWith("_STAINED_GLASS_PANE")
                || material.name().contains("FENCE")
                || material.name().endsWith("SLAB")
                || material.name().endsWith("WALL"))
            return true;

        switch (material) {
            case CHEST:
            case ENDER_CHEST:
            case TRAPPED_CHEST:
            case GLASS:
            case GLASS_PANE:
                return true;
            default:
                return material.isOccluding();
        }
    }

    /**
     * Checks if a Material is air
     *
     * @param material The Material to check
     * @return true if the Material is a type of air
     */
    public static boolean isAir(Material material) {
        switch (material) {
            case AIR:
            case CAVE_AIR:
            case VOID_AIR:
                return true;
            default:
                return false;
        }
    }

    public static String formatNumber(long points) {
        return formatter.format(points);
    }

    /**
     * Gets an integer value from a Permissible's permissions, picking the highest value out of the ones available.
     * The lowerbound will be the lowest the value can possibly be. To use, pass in a permission such as
     * "example.permission" and the integer value will be located at "example.permission.<#>"
     *
     * @param permissible The Permissible
     * @param permission The permission prefix
     * @param lowerBound The lowerbound of the value
     * @return the highest value found within the Permissible's permissions
     */
    public static int getPermissionDefinableValue(Permissible permissible, String permission, int lowerBound) {
        int amount = lowerBound;
        for (PermissionAttachmentInfo info : permissible.getEffectivePermissions()) {
            String target = info.getPermission().toLowerCase();
            if (target.startsWith(permission) && info.getValue()) {
                try {
                    amount = Math.max(amount, Integer.parseInt(target.substring(target.lastIndexOf('.') + 1)));
                } catch (NumberFormatException ignored) { }
            }
        }
        return amount;
    }

    public static void clearCache() {
        cachedAlphabeticalEntityTypes = null;
        cachedStackableEntityTypes = null;
        EntityUtils.clearCache();
        ItemUtils.clearCache();

        String separator = RoseStacker.getInstance().getManager(LocaleManager.class).getLocaleMessage("number-separator");
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance();
        symbols.setGroupingSeparator(!separator.isEmpty() ? separator.charAt(0) : ',');
        formatter = new DecimalFormat("#,##0", symbols);
    }

}
