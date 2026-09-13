package site.mcrelicworld.relicprison.gang.migration;

import java.util.List;
import java.util.UUID;

public interface GangMigrationProvider {
    String providerId();
    String providerVersion();
    boolean available();
    List<SourceGang> readPreview() throws Exception;

    record SourceGang(String sourceId, String name, String tag, UUID ownerId, List<SourceMember> members) {
        public SourceGang { members = List.copyOf(members); }
    }

    record SourceMember(UUID playerId, String role) { }
}
