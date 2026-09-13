package site.mcrelicworld.relicprison.reward;

import site.mcrelicworld.relicprison.reward.RewardLedgerService.ComponentDraft;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class FrozenRewardPlanCodec {
    private static final String VERSION = "RP1";
    private static final int MAX_COMPONENTS = 2_000;

    private FrozenRewardPlanCodec() { }

    public static String encode(List<ComponentDraft> drafts) {
        List<ComponentDraft> safe = List.copyOf(drafts == null ? List.of() : drafts);
        if (safe.size() > MAX_COMPONENTS) throw new IllegalArgumentException("Frozen reward plan is too large");
        StringBuilder result = new StringBuilder(VERSION).append('\n');
        for (ComponentDraft draft : safe) {
            result.append(field(draft.componentId())).append('\t')
                    .append(draft.type().name()).append('\t')
                    .append(field(draft.payload())).append('\t')
                    .append(draft.amount() == null ? "0" : draft.amount().toPlainString()).append('\t')
                    .append(draft.dueAt()).append('\t')
                    .append(field(draft.idempotencyKey())).append('\n');
        }
        return result.toString();
    }

    public static List<ComponentDraft> decode(String payload) {
        if (payload == null || payload.isBlank()) throw new IllegalArgumentException("Frozen reward plan is missing");
        String[] lines = payload.split("\\R", -1);
        if (lines.length == 0 || !VERSION.equals(lines[0])) {
            throw new IllegalArgumentException("Unsupported frozen reward plan version");
        }
        List<ComponentDraft> drafts = new ArrayList<>();
        for (int index = 1; index < lines.length; index++) {
            if (lines[index].isBlank()) continue;
            if (drafts.size() >= MAX_COMPONENTS) throw new IllegalArgumentException("Frozen reward plan is too large");
            String[] fields = lines[index].split("\\t", -1);
            if (fields.length != 6) throw new IllegalArgumentException("Malformed frozen reward component");
            try {
                drafts.add(new ComponentDraft(unfield(fields[0]), RewardComponentType.valueOf(fields[1]),
                        unfield(fields[2]), new BigDecimal(fields[3]), Long.parseLong(fields[4]),
                        unfield(fields[5])));
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("Malformed frozen reward component", error);
            }
        }
        return List.copyOf(drafts);
    }

    private static String field(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String unfield(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
