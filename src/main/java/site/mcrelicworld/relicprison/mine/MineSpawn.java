package site.mcrelicworld.relicprison.mine;

import org.bukkit.Location;
import org.bukkit.World;
import site.mcrelicworld.relicprison.api.model.BlockPosition;

public record MineSpawn(double x, double y, double z, float yaw, float pitch) {
    public static MineSpawn from(Location location) {
        return new MineSpawn(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
    }

    public Location toLocation(World world) { return new Location(world, x, y, z, yaw, pitch); }
    public BlockPosition blockPosition() { return new BlockPosition((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)); }
}
