package site.mcrelicworld.relicprison.mining;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

public record BulkBlockSnapshot(UUID worldId, int x, int y, int z, String material,
                                String blockData, String customBlockId) {
    private static final String FIELD = "\u001F";
    private static final String ROW = "\u001E";

    public BulkBlockSnapshot {
        if (worldId == null) throw new IllegalArgumentException("worldId is required");
        material = material == null ? "" : material;
        blockData = blockData == null ? "" : blockData;
        customBlockId = customBlockId == null ? "" : customBlockId;
    }

    public boolean custom() {
        return !customBlockId.isBlank();
    }

    public String encode() {
        return worldId + FIELD + x + FIELD + y + FIELD + z + FIELD
                + encodePart(material) + FIELD + encodePart(blockData) + FIELD + encodePart(customBlockId);
    }

    public static BulkBlockSnapshot decode(String value) {
        String[] parts = value.split(FIELD, -1);
        if (parts.length != 7) throw new IllegalArgumentException("Invalid bulk block snapshot");
        return new BulkBlockSnapshot(UUID.fromString(parts[0]), Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]), Integer.parseInt(parts[3]), decodePart(parts[4]),
                decodePart(parts[5]), decodePart(parts[6]));
    }

    public static String encodeList(List<BulkBlockSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) return "";
        return String.join(ROW, snapshots.stream().map(BulkBlockSnapshot::encode).toList());
    }

    public static List<BulkBlockSnapshot> decodeList(String payload) {
        if (payload == null || payload.isBlank()) return List.of();
        List<BulkBlockSnapshot> snapshots = new ArrayList<>();
        for (String row : payload.split(ROW, -1)) {
            if (!row.isBlank()) snapshots.add(decode(row));
        }
        return List.copyOf(snapshots);
    }

    private static String encodePart(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodePart(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
