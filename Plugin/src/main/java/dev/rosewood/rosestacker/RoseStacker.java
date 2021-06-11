package dev.rosewood.rosestacker;

import dev.rosewood.rosegarden.RosePlugin;
import dev.rosewood.rosegarden.database.DataMigration;
import dev.rosewood.rosegarden.hook.PlaceholderAPIHook;
import dev.rosewood.rosegarden.manager.Manager;
import dev.rosewood.rosegarden.utils.NMSUtil;
import dev.rosewood.rosestacker.database.migrations._1_Create_Tables_Stacks;
import dev.rosewood.rosestacker.database.migrations._2_Create_Tables_Convert_Stacks;
import dev.rosewood.rosestacker.database.migrations._3_Create_Tables_Translation_Locales;
import dev.rosewood.rosestacker.database.migrations._4_Alter_Spawner_Table_Player_Placed;
import dev.rosewood.rosestacker.hook.RoseStackerPlaceholderExpansion;
import dev.rosewood.rosestacker.hook.ShopGuiPlusHook;
import dev.rosewood.rosestacker.hook.ViaVersionHook;
import dev.rosewood.rosestacker.hook.WorldGuardHook;
import dev.rosewood.rosestacker.listener.*;
import dev.rosewood.rosestacker.manager.*;
import dev.rosewood.rosestacker.nms.NMSAdapter;
import dev.rosewood.rosestacker.utils.StackerUtils;
import net.minecraft.world.entity.ambient.EntityBat;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;

import java.util.Arrays;
import java.util.List;

/**
 * @author Esophose
 */
public class RoseStacker extends RosePlugin {

    /**
     * The running instance of RoseStacker on the server
     */
    private static RoseStacker instance;

    public static RoseStacker getInstance() {
        return instance;
    }

    public RoseStacker() {
        super(82729, 5517, ConfigurationManager.class, DataManager.class, LocaleManager.class);

        instance = this;
    }

    @Override
    public void onLoad() {
        WorldGuardHook.registerFlag();
    }

    @Override
    public void enable() {
        this.getLogger().info("Detected server API version as " + NMSUtil.getVersion());
        if (!NMSAdapter.isValidVersion()) {
            this.getLogger().severe("RoseStacker only supports 1.13.2 through " + StackerUtils.MAX_SUPPORTED_VERSION + ". The plugin has been disabled.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        StackerUtils.clearCache();

        // Register listeners
        PluginManager pluginManager = Bukkit.getPluginManager();
        pluginManager.registerEvents(new BlockListener(this), this);
        pluginManager.registerEvents(new WorldListener(this), this);
        pluginManager.registerEvents(new EntityListener(this), this);
        pluginManager.registerEvents(new InteractListener(this), this);
        pluginManager.registerEvents(new ItemListener(this), this);
        pluginManager.registerEvents(new StackToolListener(this), this);
        pluginManager.registerEvents(new BreedingListener(this), this);

        // Axolotls, Goats are only in 1.17+
        if (NMSUtil.getVersionNumber() >= 17)
            // todo
            System.out.println("ahhh coming soon");

        // Bees are only in 1.15+
        if (NMSUtil.getVersionNumber() >= 15)
            pluginManager.registerEvents(new BeeListener(this), this);

        // Dispensers can only shear sheep in 1.14+
        // Raids are only in 1.14+
        if (NMSUtil.getVersionNumber() >= 14) {
            pluginManager.registerEvents(new BlockShearListener(this), this);
            pluginManager.registerEvents(new RaidListener(), this);
        }

        // Try to hook with PlaceholderAPI
        if (PlaceholderAPIHook.enabled())
            new RoseStackerPlaceholderExpansion(this).register();

        // Try to hook with ShopGuiPlus
        Bukkit.getScheduler().runTask(this, () -> {
            if (Bukkit.getPluginManager().isPluginEnabled("ShopGUIPlus"))
                ShopGuiPlusHook.setupSpawners(this);
        });

        // Try to hook with Clearlag
        if (Bukkit.getPluginManager().isPluginEnabled("Clearlag"))
            pluginManager.registerEvents(new ClearlagListener(this), this);

        // Try to hook with ViaVersion
        if (Bukkit.getPluginManager().isPluginEnabled("ViaVersion"))
            ViaVersionHook.suppressMetadataErrors();

        // Try fetching the translation locales
        this.getManager(LocaleManager.class).fetchMinecraftTranslationLocales();
    }

    @Override
    public void disable() {
        Bukkit.getScheduler().cancelTasks(this);
    }

    @Override
    protected List<Class<? extends Manager>> getManagerLoadPriority() {
        return Arrays.asList(
                DataManager.class,
                StackSettingManager.class,
                CommandManager.class,
                ConversionManager.class,
                HologramManager.class,
                EntityCacheManager.class,
                StackManager.class,
                SpawnerSpawnManager.class
        );
    }

    @Override
    public List<Class<? extends DataMigration>> getDataMigrations() {
        return Arrays.asList(
                _1_Create_Tables_Stacks.class,
                _2_Create_Tables_Convert_Stacks.class,
                _3_Create_Tables_Translation_Locales.class,
                _4_Alter_Spawner_Table_Player_Placed.class
        );
    }

}
