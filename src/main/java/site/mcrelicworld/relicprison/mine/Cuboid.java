package site.mcrelicworld.relicprison.mine;

import org.bukkit.Location;
import site.mcrelicworld.relicprison.api.model.BlockPosition;

public record Cuboid(BlockPosition minimum, BlockPosition maximum) {
    public Cuboid {
        int minX = Math.min(minimum.x(), maximum.x());
        int minY = Math.min(minimum.y(), maximum.y());
        int minZ = Math.min(minimum.z(), maximum.z());
        int maxX = Math.max(minimum.x(), maximum.x());
        int maxY = Math.max(minimum.y(), maximum.y());
        int maxZ = Math.max(minimum.z(), maximum.z());
        minimum = new BlockPosition(minX, minY, minZ);
        maximum = new BlockPosition(maxX, maxY, maxZ);
    }

    public static Cuboid of(Location first, Location second) {
        if (first.getWorld() == null || second.getWorld() == null || !first.getWorld().getUID().equals(second.getWorld().getUID())) {
            throw new IllegalArgumentException("Both points must be in the same world");
        }
        return new Cuboid(
                new BlockPosition(first.getBlockX(), first.getBlockY(), first.getBlockZ()),
                new BlockPosition(second.getBlockX(), second.getBlockY(), second.getBlockZ())
        );
    }

    public long volume() {
        long x = (long) maximum.x() - minimum.x() + 1;
        long y = (long) maximum.y() - minimum.y() + 1;
        long z = (long) maximum.z() - minimum.z() + 1;
        return Math.multiplyExact(Math.multiplyExact(x, y), z);
    }

    public boolean contains(int x, int y, int z) {
        return x >= minimum.x() && x <= maximum.x()
                && y >= minimum.y() && y <= maximum.y()
                && z >= minimum.z() && z <= maximum.z();
    }

    public boolean intersects(Cuboid other) {
        return minimum.x() <= other.maximum.x() && maximum.x() >= other.minimum.x()
                && minimum.y() <= other.maximum.y() && maximum.y() >= other.minimum.y()
                && minimum.z() <= other.maximum.z() && maximum.z() >= other.minimum.z();
    }
}
