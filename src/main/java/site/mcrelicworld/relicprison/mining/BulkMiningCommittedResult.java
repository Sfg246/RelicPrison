package site.mcrelicworld.relicprison.mining;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record BulkMiningCommittedResult(
        long occurredAt,
        long totalBlocks,
        long itemsSold,
        BigDecimal moneyEarned,
        int experience,
        boolean mineAnalytics,
        List<Period> periods,
        List<MineResult> mines
) {
    private static final int FORMAT_VERSION = 1;

    public BulkMiningCommittedResult {
        moneyEarned = moneyEarned == null ? BigDecimal.ZERO : moneyEarned;
        periods = List.copyOf(periods == null ? List.of() : periods);
        mines = List.copyOf(mines == null ? List.of() : mines);
    }

    public String encode() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(FORMAT_VERSION);
                output.writeLong(occurredAt);
                output.writeLong(totalBlocks);
                output.writeLong(itemsSold);
                output.writeUTF(moneyEarned.toPlainString());
                output.writeInt(experience);
                output.writeBoolean(mineAnalytics);
                output.writeInt(periods.size());
                for (Period period : periods) {
                    output.writeUTF(period.type());
                    output.writeUTF(period.key());
                }
                output.writeInt(mines.size());
                for (MineResult mine : mines) {
                    output.writeUTF(mine.mineId());
                    writeMap(output, mine.materials());
                    writeMap(output, mine.customBlocks());
                    output.writeLong(mine.autoSellBlocks());
                    output.writeLong(mine.autoPickupBlocks());
                    output.writeLong(mine.autoBlockBlocks());
                    output.writeLong(mine.fallbackDroppedItems());
                }
            }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
        } catch (IOException error) {
            throw new IllegalStateException("Unable to encode committed bulk result", error);
        }
    }

    public static BulkMiningCommittedResult decode(String encoded) {
        if (encoded == null || encoded.isBlank()) throw new IllegalArgumentException("Missing committed bulk result");
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(
                Base64.getUrlDecoder().decode(encoded)))) {
            int version = input.readInt();
            if (version != FORMAT_VERSION) throw new IllegalArgumentException("Unsupported bulk result version " + version);
            long occurredAt = input.readLong();
            long totalBlocks = input.readLong();
            long itemsSold = input.readLong();
            BigDecimal moneyEarned = new BigDecimal(input.readUTF());
            int experience = input.readInt();
            boolean mineAnalytics = input.readBoolean();
            List<Period> periods = new ArrayList<>();
            int periodCount = bounded(input.readInt(), 8, "period");
            for (int index = 0; index < periodCount; index++) {
                periods.add(new Period(input.readUTF(), input.readUTF()));
            }
            List<MineResult> mines = new ArrayList<>();
            int mineCount = bounded(input.readInt(), 256, "mine");
            for (int index = 0; index < mineCount; index++) {
                mines.add(new MineResult(input.readUTF(), readMap(input), readMap(input), input.readLong(),
                        input.readLong(), input.readLong(), input.readLong()));
            }
            if (input.available() != 0) throw new IllegalArgumentException("Trailing committed bulk result data");
            return new BulkMiningCommittedResult(occurredAt, totalBlocks, itemsSold, moneyEarned, experience,
                    mineAnalytics, periods, mines);
        } catch (IOException | IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid committed bulk result", error);
        }
    }

    private static void writeMap(DataOutputStream output, Map<String, Integer> values) throws IOException {
        output.writeInt(values.size());
        for (var entry : values.entrySet()) {
            output.writeUTF(entry.getKey());
            output.writeInt(entry.getValue());
        }
    }

    private static Map<String, Integer> readMap(DataInputStream input) throws IOException {
        int count = bounded(input.readInt(), 4096, "map");
        Map<String, Integer> values = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            String key = input.readUTF();
            int value = input.readInt();
            if (!key.isBlank() && value > 0) values.merge(key, value, Integer::sum);
        }
        return Map.copyOf(values);
    }

    private static int bounded(int value, int maximum, String label) {
        if (value < 0 || value > maximum) throw new IllegalArgumentException("Invalid " + label + " count " + value);
        return value;
    }

    public record Period(String type, String key) {
        public Period {
            if (type == null || type.isBlank() || key == null || key.isBlank()) {
                throw new IllegalArgumentException("Bulk statistic period is required");
            }
        }
    }

    public record MineResult(String mineId, Map<String, Integer> materials, Map<String, Integer> customBlocks,
                             long autoSellBlocks, long autoPickupBlocks, long autoBlockBlocks,
                             long fallbackDroppedItems) {
        public MineResult {
            if (mineId == null || mineId.isBlank()) throw new IllegalArgumentException("Mine ID is required");
            materials = Map.copyOf(materials == null ? Map.of() : materials);
            customBlocks = Map.copyOf(customBlocks == null ? Map.of() : customBlocks);
            autoSellBlocks = Math.max(0L, autoSellBlocks);
            autoPickupBlocks = Math.max(0L, autoPickupBlocks);
            autoBlockBlocks = Math.max(0L, autoBlockBlocks);
            fallbackDroppedItems = Math.max(0L, fallbackDroppedItems);
        }

        public long blocks() {
            return materials.values().stream().mapToLong(Integer::longValue).sum();
        }
    }
}
