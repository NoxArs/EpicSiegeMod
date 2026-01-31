package funwayguy.esm.core;

import java.util.UUID;

import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;

public final class AttrUtil {

    private AttrUtil() {}

    public static void applyOrReplace(IAttributeInstance attr, UUID id, String name, double amount, int operation) {
        if (attr == null || id == null) return;

        try {
            AttributeModifier old = attr.getModifier(id);
            if (old != null) {
                attr.removeModifier(old);
            }

            if (amount != 0.0D) {
                attr.applyModifier(new AttributeModifier(id, name, amount, operation));
            }
        } catch (Throwable t) {
            // 1.7.10 ecosystem: don't let a broken attribute impl crash the server.
            // Optional: log at debug level if you have a logger.
            // ESM.log.debug("AttrUtil.applyOrReplace failed: " + name, t);
        }
    }
}
