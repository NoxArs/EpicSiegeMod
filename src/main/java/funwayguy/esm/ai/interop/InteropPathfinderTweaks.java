package funwayguy.esm.ai.interop;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;

public class InteropPathfinderTweaks implements InteropPathfinderTweaksInterface {

    // 缓存反射结果，避免每 tick 反射带来额外开销
    private static final String CLS = "com.cosmicdan.pathfindertweaks.Main";
    private static volatile java.lang.reflect.Method isTallBlockMethod;
    private static volatile boolean resolved = false;

    @Override
    public boolean isTallBlock(Entity entity, Block block, int posX, int posY, int posZ) {
        java.lang.reflect.Method m = resolve();
        if (m == null) return false; // 没装 PT 或反射失败：按“不高”处理（你也可按需求改默认值）
        try {
            Object r = m.invoke(null, entity, block, posX, posY, posZ);
            return (r instanceof Boolean) ? (Boolean) r : false;
        } catch (Throwable t) {
            return false;
        }
    }

    private static java.lang.reflect.Method resolve() {
        if (resolved) return isTallBlockMethod;
        synchronized (InteropPathfinderTweaks.class) {
            if (resolved) return isTallBlockMethod;
            resolved = true;
            try {
                Class<?> c = Class.forName(CLS, false, InteropPathfinderTweaks.class.getClassLoader());
                isTallBlockMethod = c.getMethod(
                    "isTallBlock",
                    net.minecraft.entity.Entity.class,
                    net.minecraft.block.Block.class,
                    int.class,
                    int.class,
                    int.class);
            } catch (Throwable t) {
                isTallBlockMethod = null;
            }
            return isTallBlockMethod;
        }
    }
}
