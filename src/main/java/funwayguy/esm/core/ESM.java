package funwayguy.esm.core;

import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.Mod.Instance;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.EntityRegistry;
import funwayguy.esm.core.proxies.CommonProxy;
import funwayguy.esm.entities.EntityESMGhast;
import funwayguy.esm.entities.EntityZombieBoat;
import funwayguy.esm.handlers.ESM_EventManager;

@Mod(
    modid = ESM_Settings.ID,
    name = ESM_Settings.Name,
    version = ESM_Settings.Version,
    guiFactory = "funwayguy.esm.client.ESMGuiFactory")
public class ESM {

    @Instance(ESM_Settings.ID)
    public static ESM instance;

    @SidedProxy(clientSide = ESM_Settings.Proxy + ".ClientProxy", serverSide = ESM_Settings.Proxy + ".CommonProxy")
    public static CommonProxy proxy;

    public static org.apache.logging.log4j.Logger log;

    @EventHandler
    public static void preInit(FMLPreInitializationEvent event) {
        log = event.getModLog();
        ESM_Settings.LoadMainConfig(event.getSuggestedConfigurationFile());
        ESM_Blocks.init();
    }

    @EventHandler
    public static void init(FMLInitializationEvent event) {
        ESM_EventManager manager = new ESM_EventManager();
        MinecraftForge.EVENT_BUS.register(manager);
        MinecraftForge.TERRAIN_GEN_BUS.register(manager);
        FMLCommonHandler.instance()
            .bus()
            .register(manager);

        int ghastID = EntityRegistry.findGlobalUniqueEntityId();
        EntityRegistry.registerGlobalEntityID(EntityESMGhast.class, "ESM_Ghast", ghastID);
        EntityRegistry.registerModEntity(EntityESMGhast.class, "ESM_Ghast", ghastID, instance, 128, 1, true);

        EntityRegistry.registerModEntity(EntityZombieBoat.class, "ESM_Boat", 1, instance, 80, 3, true);

        proxy.registerRenderers();
    }

    @EventHandler
    public static void postInit(FMLPostInitializationEvent event) {}
}
