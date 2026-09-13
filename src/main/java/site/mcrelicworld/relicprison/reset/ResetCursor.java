package site.mcrelicworld.relicprison.reset;

import site.mcrelicworld.relicprison.mine.Cuboid;
import site.mcrelicworld.relicprison.mine.MineResetConfig;

public final class ResetCursor {
    private final Cuboid bounds;
    private final MineResetConfig.ResetOrder order;
    private final int width;
    private final int height;
    private final int depth;
    private final long total;
    private final long shuffleMultiplier;
    private final long shuffleOffset;
    private long index;
    private int x;
    private int y;
    private int z;

    public ResetCursor(Cuboid bounds, MineResetConfig.ResetOrder order) {
        this.bounds = bounds;
        this.order = order;
        width = bounds.maximum().x() - bounds.minimum().x() + 1;
        height = bounds.maximum().y() - bounds.minimum().y() + 1;
        depth = bounds.maximum().z() - bounds.minimum().z() + 1;
        total = bounds.volume();
        shuffleMultiplier = coprimeMultiplier(total);
        shuffleOffset = Math.floorMod((long) bounds.minimum().x() * 31L + (long) bounds.minimum().z() * 17L
                + bounds.minimum().y(), total);
        update();
    }

    public boolean hasNext() { return index < total; }
    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }

    public void advance() {
        if (!hasNext()) return;
        index++;
        if (hasNext()) update();
    }

    private void update() {
        long effective = switch (order) {
            case DETERMINISTIC_SHUFFLED -> Math.floorMod(index * shuffleMultiplier + shuffleOffset, total);
            default -> index;
        };
        int xIndex = (int) (effective % width);
        long plane = effective / width;
        int zIndex = (int) (plane % depth);
        int yIndex = (int) (plane / depth);

        switch (order) {
            case CHUNK_GROUPED_TOP_DOWN, TOP_TO_BOTTOM -> yIndex = height - 1 - yIndex;
            case CENTER_OUTWARD -> {
                xIndex = centerOut(xIndex, width);
                zIndex = centerOut(zIndex, depth);
            }
            case OUTSIDE_INWARD -> {
                xIndex = outsideIn(xIndex, width);
                zIndex = outsideIn(zIndex, depth);
                yIndex = outsideIn(yIndex, height);
            }
            default -> {
                // Bottom-up, layered, and shuffled all use the calculated y index.
            }
        }

        x = bounds.minimum().x() + xIndex;
        y = bounds.minimum().y() + yIndex;
        z = bounds.minimum().z() + zIndex;
    }

    private static int centerOut(int value, int size) {
        int center = (size - 1) / 2;
        if (value == 0) return center;
        int distance = (value + 1) / 2;
        int candidate = value % 2 == 1 ? center - distance : center + distance;
        if (candidate < 0) return size - 1 - (distance - center - 1);
        if (candidate >= size) return distance - (size - center);
        return candidate;
    }

    private static int outsideIn(int value, int size) {
        if (value % 2 == 0) return value / 2;
        return size - 1 - value / 2;
    }

    private static long coprimeMultiplier(long value) {
        if (value <= 2) return 1;
        long candidate = Math.max(3, value / 2);
        if (candidate % 2 == 0) candidate++;
        while (gcd(candidate, value) != 1) candidate += 2;
        return candidate;
    }

    private static long gcd(long left, long right) {
        while (right != 0) {
            long next = left % right;
            left = right;
            right = next;
        }
        return Math.abs(left);
    }
}
