package funwayguy.esm.core.proxies;

import funwayguy.esm.entities.EntityZombieBoat;

public class ClientProxy extends CommonProxy {

    @Override
    public boolean isClient() {
        return true;
    }

    @Override
    public void registerRenderers() {
        cpw.mods.fml.client.registry.RenderingRegistry.registerEntityRenderingHandler(
            EntityZombieBoat.class,
            new net.minecraft.client.renderer.entity.RenderBoat());
    }
}
