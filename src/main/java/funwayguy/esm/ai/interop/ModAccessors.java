package funwayguy.esm.ai.interop;

import cpw.mods.fml.common.Loader;
import funwayguy.esm.core.ESM;

public class ModAccessors {

    public static InteropPathfinderTweaksInterface PathfinderTweaks;
    public static boolean PATHFINDERTWEAKS_LOADED = false;

    public static void init() {
        try {
            if (Loader.isModLoaded("pathfindertweaks")) {
                ESM.log.info("PathfinderTweaks found");
                PathfinderTweaks = Class.forName("funwayguy.esm.ai.interop.InteropPathfinderTweaks")
                    .asSubclass(InteropPathfinderTweaksInterface.class)
                    .newInstance();
                PATHFINDERTWEAKS_LOADED = true;
            } else {
                ESM.log.info("PathfinderTweaks NOT found");
                PathfinderTweaks = Class.forName("funwayguy.esm.ai.interop.InteropPathfinderTweaksDummy")
                    .asSubclass(InteropPathfinderTweaksInterface.class)
                    .newInstance();
            }
        } catch (Exception e) {
            // shouldn't happen
        }
    }
}
