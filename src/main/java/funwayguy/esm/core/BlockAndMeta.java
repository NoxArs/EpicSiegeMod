package funwayguy.esm.core;

import java.util.HashSet;

import net.minecraft.block.Block;

import cpw.mods.fml.common.registry.GameRegistry;

public class BlockAndMeta {

    public final Block block;
    public final int meta;

    public BlockAndMeta(Block block, int meta) {
        this.block = block;
        this.meta = meta;
    }

    // public static BlockAndMeta[] buildList(String listName, ArrayList<String> blockListRaw) {
    // return buildList(listName, blockListRaw.toArray(new String[blockListRaw.size()]));
    // }

    // 如果想让 meta=-1 不参与去重比较
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlockAndMeta)) return false;
        BlockAndMeta other = (BlockAndMeta) o;
        if (!Block.isEqualTo(this.block, other.block)) return false;
        if (this.meta == -1 || other.meta == -1) return true;

        return this.meta == other.meta;
    }

    @Override
    public int hashCode() {
        int id = (block == null) ? 0 : Block.getIdFromBlock(block);
        return (meta == -1) ? 31 * id - 1 : 31 * id + meta;
    }

    public static BlockAndMeta[] buildList(String listName, String[] blockListRaw) {
        HashSet<BlockAndMeta> blockList = new HashSet<BlockAndMeta>();

        ESM.log.info("Building " + listName + "...");

        if (blockListRaw == null) {
            ESM.log.warn(" - " + listName + " list is null");
            return new BlockAndMeta[0];
        }

        for (String raw : blockListRaw) {
            if (raw == null) continue;

            String line = raw.trim();
            if (line.isEmpty()) continue;

            // Optional: allow comments in config lines (# or //)
            int hash = line.indexOf('#');
            int slash = line.indexOf("//");

            int cut = -1;
            if (hash >= 0) cut = hash;
            if (slash >= 0) cut = (cut < 0) ? slash : Math.min(cut, slash);

            if (cut >= 0) {
                line = line.substring(0, cut)
                    .trim();
            }

            if (line.isEmpty()) continue;

            String[] parts = line.split(":");
            int len = parts.length;

            if (len != 2 && len != 3) {
                ESM.log.warn(" - Invalid block (bad length of " + len + "): " + line);
                continue;
            }

            String modid = parts[0].trim();
            String name = parts[1].trim();

            if (modid.isEmpty() || name.isEmpty()) {
                ESM.log.warn(" - Invalid block (parse/format error): " + line);
                continue;
            }

            Block block = GameRegistry.findBlock(modid, name);
            if (block == null) {
                ESM.log.warn(" - Skipping missing block: " + line);
                continue;
            }

            int meta = -1;
            if (len == 3) {
                String metaStr = parts[2].trim();
                try {
                    meta = Integer.parseInt(metaStr);
                    if (meta < 0 || meta > 15) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    ESM.log.warn(" - Meta value invalid: '" + metaStr + "' (must be 0..15) in: " + line);
                    continue;
                }
            }

            blockList.add(new BlockAndMeta(block, meta));
        }

        ESM.log.info("...added " + blockList.size() + " blocks to " + listName);

        return blockList.toArray(new BlockAndMeta[blockList.size()]);
    }

    public static boolean isInBlockAndMetaList(Block block, int meta, BlockAndMeta[] list) {
        if (block == null || list == null) return false;

        for (BlockAndMeta bam : list) {
            if (bam == null || bam.block == null) continue;
            if (Block.isEqualTo(bam.block, block)) {
                if (bam.meta == -1 || bam.meta == meta) return true;
            }
        }
        return false;
    }
}
