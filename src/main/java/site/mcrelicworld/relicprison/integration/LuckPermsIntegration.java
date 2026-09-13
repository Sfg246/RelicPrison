package site.mcrelicworld.relicprison.integration;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.group.GroupManager;
import net.luckperms.api.model.PermissionHolder;
import net.luckperms.api.model.data.NodeMap;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeBuilder;
import net.luckperms.api.node.NodeBuilderRegistry;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Typed LuckPerms integration compiled and verified against LuckPerms Bukkit 5.4.164.
 *
 * <p>RelicPrison remains the source of truth for rank and prestige progression. LuckPerms mirrors
 * that state by assigning one direct rank group and one direct prestige group to each player. Rank
 * groups inherit the previous rank, so a Rank Z player receives A-Z permissions while holding only
 * {@code rank-z} directly.</p>
 */
public final class LuckPermsIntegration {
    private static final String VERIFIED_RUNTIME_VERSION = "5.4.164";

    private final RelicPrisonPlugin plugin;
    private LuckPerms luckPerms;
    private GroupManager groupManager;
    private UserManager userManager;
    private NodeBuilderRegistry nodeBuilderRegistry;

    public LuckPermsIntegration(RelicPrisonPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        initialize(LuckPermsProvider.get());
    }

    void initialize(LuckPerms api) {
        luckPerms = api;
        groupManager = luckPerms.getGroupManager();
        userManager = luckPerms.getUserManager();
        nodeBuilderRegistry = luckPerms.getNodeBuilderRegistry();

        String runtimeVersion = luckPerms.getPluginMetadata().getVersion();
        String apiVersion = luckPerms.getPluginMetadata().getApiVersion();
        if (plugin != null) {
            plugin.getLogger().info("LuckPerms connected: runtime " + runtimeVersion + ", API " + apiVersion
                    + "; RelicPrison verified against " + VERIFIED_RUNTIME_VERSION + '.');
            if (!VERIFIED_RUNTIME_VERSION.equals(runtimeVersion)) {
                plugin.getLogger().warning("LuckPerms runtime differs from the verified RelicPrison build. "
                        + "Progression remains protected by repair-on-join, but test rankup and prestige on staging.");
            }
        }
    }

    public boolean connected() {
        return luckPerms != null;
    }

    public String version() {
        return luckPerms == null ? "unavailable" : luckPerms.getPluginMetadata().getVersion();
    }

    public CompletableFuture<Void> ensureInheritance(List<String> orderedGroups) {
        Set<String> managed = normalizedSet(orderedGroups);
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

        for (int index = 0; index < orderedGroups.size(); index++) {
            String groupName = normalize(orderedGroups.get(index));
            String parent = index == 0 ? null : normalize(orderedGroups.get(index - 1));
            chain = chain.thenCompose(ignored -> loadOrCreateGroup(groupName).thenCompose(group -> {
                for (InheritanceNode node : inheritanceNodes(group)) {
                    if (managed.contains(normalize(node.getGroupName()))) {
                        normalData(group).remove(node);
                    }
                }
                if (parent != null) {
                    normalData(group).add(buildInheritanceNode(parent));
                }
                return groupManager.saveGroup(group);
            }));
        }

        return chain.exceptionally(error -> {
            if (plugin == null) {
                throw new CompletionException(error);
            }
            plugin.getLogger().severe("LuckPerms inheritance synchronization failed; RelicPrison will continue "
                    + "with progression data safe: " + rootMessage(error));
            return null;
        });
    }

    public CompletableFuture<Void> replaceGroups(UUID playerId, Collection<String> managedGroups,
                                                  String oldGroup, String newGroup) {
        Set<String> managed = normalizedSet(managedGroups);
        String desired = newGroup == null ? null : normalize(newGroup);
        return userManager.loadUser(playerId).thenCompose(user -> mutateUserGroups(user, managed,
                desired == null ? Set.of() : Set.of(desired)));
    }

    public CompletableFuture<Void> repairGroups(UUID playerId, Collection<String> managedGroups,
                                                  Set<String> desiredGroups) {
        Set<String> managed = normalizedSet(managedGroups);
        Set<String> desired = normalizedSet(desiredGroups);
        return userManager.loadUser(playerId).thenCompose(user -> mutateUserGroups(user, managed, desired));
    }

    public CompletableFuture<GroupSnapshot> snapshotManagedGroups(UUID playerId, Collection<String> rankGroups,
                                                                  Collection<String> prestigeGroups) {
        Set<String> managedRanks = normalizedSet(rankGroups);
        Set<String> managedPrestiges = normalizedSet(prestigeGroups);
        return userManager.loadUser(playerId).thenApply(user -> {
            Set<String> directRanks = new HashSet<>();
            Set<String> directPrestiges = new HashSet<>();
            for (InheritanceNode node : inheritanceNodes(user)) {
                String normalized = normalize(node.getGroupName());
                if (managedRanks.contains(normalized)) directRanks.add(normalized);
                if (managedPrestiges.contains(normalized)) directPrestiges.add(normalized);
            }
            return new GroupSnapshot(directRanks, directPrestiges);
        });
    }

    private CompletableFuture<Void> mutateUserGroups(User user, Set<String> managed, Set<String> desired) {
        Set<String> present = new HashSet<>();
        for (InheritanceNode node : inheritanceNodes(user)) {
            String normalized = normalize(node.getGroupName());
            if (!managed.contains(normalized)) {
                continue;
            }
            if (desired.contains(normalized)) {
                present.add(normalized);
            } else {
                normalData(user).remove(node);
            }
        }

        for (String group : desired) {
            if (!present.contains(group)) {
                normalData(user).add(buildInheritanceNode(group));
            }
        }
        return userManager.saveUser(user);
    }

    private static Collection<InheritanceNode> inheritanceNodes(PermissionHolder holder) {
        return holder.getNodes(NodeType.INHERITANCE);
    }

    private static NodeMap normalData(PermissionHolder holder) {
        return holder.data();
    }

    private Node buildInheritanceNode(String groupName) {
        NodeBuilder<?, ?> builder = nodeBuilderRegistry.forInheritance().group(groupName);
        return builder.build();
    }

    private CompletableFuture<Group> loadOrCreateGroup(String groupName) {
        return groupManager.loadGroup(groupName).thenCompose(optional -> {
            Optional<Group> loaded = optional;
            return loaded.<CompletableFuture<Group>>map(CompletableFuture::completedFuture)
                    .orElseGet(() -> groupManager.createAndLoadGroup(groupName));
        });
    }

    private static Set<String> normalizedSet(Collection<String> values) {
        Set<String> normalized = new HashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                normalized.add(normalize(value));
            }
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    public record GroupSnapshot(Set<String> rankGroups, Set<String> prestigeGroups) {
        public GroupSnapshot {
            rankGroups = Set.copyOf(rankGroups);
            prestigeGroups = Set.copyOf(prestigeGroups);
        }
    }
}
