package funwayguy.esm.ai;

import java.lang.reflect.Field;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityChicken;
import net.minecraft.pathfinding.PathEntity;
import net.minecraft.pathfinding.PathFinder;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.util.MathHelper;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.World;

/**
 * MC 1.7.10 PathNavigate wrapper with:
 * - custom canNavigate() (e.g. chicken jockey)
 * - inherit options from another PathNavigate (reflection for private options)
 * - PathFinder args driven by our own cached flags (stable)
 */
public class ESMPathNavigator extends PathNavigate {

    // ---- private fields in PathNavigate (for inherit only) ----
    private final EntityLiving owner;
    private final World world;
    private static final String[] F_NO_SUN = { "noSunPathfind", "field_75509_f" };
    private static final String[] F_SPEED = { "speed", "field_75511_d" };
    private static final String[] F_OPEN_DOORS = { "canPassOpenWoodenDoors", "field_75518_j" };
    private static final String[] F_CAN_SWIM = { "canSwim", "field_75517_m" };
    private static final String[] F_AVOIDS_WATER = { "avoidsWater", "field_75516_n" };

    // ---- our cached options (single source for PathFinder args) ----
    private boolean canPassOpenWoodenDoors = true;
    private boolean canPassClosedWoodenDoors = false;
    private boolean avoidsWater = false;
    private boolean canSwim = false;

    public ESMPathNavigator(EntityLiving entityLiving, World world) {
        super(entityLiving, world);
        this.owner = entityLiving;
        this.world = world;
        // Initialize caches from current base state (best effort).
        // If reflection fails, keep sane defaults; setters will keep caches correct thereafter.
        this.canSwim = getBoolean(this, F_CAN_SWIM, false);
        this.avoidsWater = getBoolean(this, F_AVOIDS_WATER, false);
        this.canPassOpenWoodenDoors = getBoolean(this, F_OPEN_DOORS, true);

        // canPassClosedWoodenDoors is effectively "break doors" behavior for pathing in most cases.
        // Base getter exists; use it as truth at startup.
        this.canPassClosedWoodenDoors = super.getCanBreakDoors();
    }

    private static Field findFieldUpwards(Object instance, String name) {
        Class<?> c = instance.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static Object getPrivateValueUpwards(Object instance, String[] names) {
        if (instance == null) return null;

        for (String n : names) {
            try {
                Field f = findFieldUpwards(instance, n);
                if (f != null) return f.get(instance);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /** Inherit main options from another navigator. */
    public void inherit(PathNavigate navigator) {
        if (navigator == null) return;

        // public / protected getters are safe:
        this.setAvoidsWater(navigator.getAvoidsWater());
        this.setBreakDoors(navigator.getCanBreakDoors());

        // private fields via reflection (best-effort):
        this.setAvoidSun(getBoolean(navigator, F_NO_SUN, false));
        this.setEnterDoors(getBoolean(navigator, F_OPEN_DOORS, true));
        this.setCanSwim(getBoolean(navigator, F_CAN_SWIM, false));
        this.setSpeedSafe(getDouble(navigator, F_SPEED, 1.0D));
    }

    // -----------------------------
    // Path entry points
    // -----------------------------

    @Override
    public PathEntity getPathToXYZ(double x, double y, double z) {
        if (!canNavigateCustom()) return null;

        return getEntityPathToXYZ(
            world,
            owner,
            MathHelper.floor_double(x),
            (int) y,
            MathHelper.floor_double(z),
            this.getPathSearchRange(),
            this.canPassOpenWoodenDoors,
            this.canPassClosedWoodenDoors,
            this.avoidsWater,
            this.canSwim);
    }

    @Override
    public PathEntity getPathToEntityLiving(Entity target) {
        if (!canNavigateCustom()) return null;

        return getPathEntityToEntity(
            world,
            owner,
            target,
            this.getPathSearchRange(),
            this.canPassOpenWoodenDoors,
            this.canPassClosedWoodenDoors,
            this.avoidsWater,
            this.canSwim);
    }

    // -----------------------------
    // Vanilla-like path building (1.7.10)
    // -----------------------------

    public PathEntity getEntityPathToXYZ(World world, Entity entity, int targetX, int targetY, int targetZ,
        float pathSearchRange, boolean canPassOpenDoors, boolean canPassClosedDoors, boolean avoidsWater,
        boolean canSwim) {
        if (world == null || entity == null) return null;

        world.theProfiler.startSection("pathfind");

        int l = MathHelper.floor_double(entity.posX);
        int i1 = MathHelper.floor_double(entity.posY);
        int j1 = MathHelper.floor_double(entity.posZ);
        int k1 = (int) (pathSearchRange + 8.0F);

        ChunkCache chunkcache = new ChunkCache(world, l - k1, i1 - k1, j1 - k1, l + k1, i1 + k1, j1 + k1, 0);

        PathEntity pathentity = (new PathFinder(chunkcache, canPassOpenDoors, canPassClosedDoors, avoidsWater, canSwim))
            .createEntityPathTo(entity, targetX, targetY, targetZ, pathSearchRange);

        world.theProfiler.endSection();
        return pathentity;
    }

    public PathEntity getPathEntityToEntity(World world, Entity from, Entity to, float pathSearchRange,
        boolean canPassOpenDoors, boolean canPassClosedDoors, boolean avoidsWater, boolean canSwim) {
        if (world == null || from == null || to == null) return null;

        world.theProfiler.startSection("pathfind");

        int i = MathHelper.floor_double(from.posX);
        int j = MathHelper.floor_double(from.posY + 1.0D);
        int k = MathHelper.floor_double(from.posZ);
        int l = (int) (pathSearchRange + 16.0F);

        ChunkCache chunkcache = new ChunkCache(world, i - l, j - l, k - l, i + l, j + l, k + l, 0);

        PathEntity pathentity = (new PathFinder(chunkcache, canPassOpenDoors, canPassClosedDoors, avoidsWater, canSwim))
            .createEntityPathTo(from, to, pathSearchRange);

        world.theProfiler.endSection();
        return pathentity;
    }

    // -----------------------------
    // Custom canNavigate
    // -----------------------------

    private boolean canNavigateCustom() {
        EntityLiving e = this.owner;
        if (e == null) return false;

        if (e.onGround) return true;

        if (this.canSwim && (e.isInWater() || e.handleLavaMovement())) return true;

        // chicken jockey zombie special case
        if (e.isRiding() && (e instanceof EntityZombie) && (e.ridingEntity instanceof EntityChicken)) return true;

        return false;
    }

    // -----------------------------
    // Setters: keep caches in sync (PathFinder args use caches)
    // -----------------------------

    @Override
    public void setCanSwim(boolean flag) {
        super.setCanSwim(flag);
        this.canSwim = flag;
    }

    @Override
    public void setAvoidsWater(boolean flag) {
        super.setAvoidsWater(flag);
        this.avoidsWater = flag;
    }

    @Override
    public void setBreakDoors(boolean flag) {
        super.setBreakDoors(flag);
        this.canPassClosedWoodenDoors = flag;
    }

    @Override
    public void setEnterDoors(boolean flag) {
        super.setEnterDoors(flag);
        this.canPassOpenWoodenDoors = flag;
    }

    private void setSpeedSafe(double speed) {
        try {
            super.setSpeed(speed);
        } catch (Throwable ignored) {}
        // speed affects path following; not required for PathFinder args, so we don't cache it.
    }

    // -----------------------------
    // Reflection helpers (inherit only)
    // -----------------------------

    private static boolean getBoolean(Object nav, String[] names, boolean def) {
        if (!(nav instanceof PathNavigate)) return def;

        Object v = getPrivateValueUpwards(nav, names);
        return (v instanceof Boolean) ? (Boolean) v : def;
    }

    private static double getDouble(Object nav, String[] names, double def) {
        if (!(nav instanceof PathNavigate)) return def;

        Object v = getPrivateValueUpwards(nav, names);
        return (v instanceof Number) ? ((Number) v).doubleValue() : def;
    }
}
