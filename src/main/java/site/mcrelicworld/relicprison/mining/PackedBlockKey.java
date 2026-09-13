package site.mcrelicworld.relicprison.mining;

/** Packs modern Minecraft block coordinates into one long without allocating Location objects. */
public final class PackedBlockKey {
    private PackedBlockKey() {}
    public static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
    }
}
