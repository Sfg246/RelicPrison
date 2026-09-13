package site.mcrelicworld.relicprison.integration;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.data.DataMutateResult;
import net.luckperms.api.model.data.NodeMap;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.group.GroupManager;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeBuilderRegistry;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.platform.PluginMetadata;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LuckPermsIntegrationTest {
    @Test
    void createsInheritanceAndMaintainsOneRankAndPrestige() {
        FakeLuckPerms fake = new FakeLuckPerms();
        LuckPermsIntegration integration = new LuckPermsIntegration(null);
        integration.initialize(fake.api());

        integration.ensureInheritance(List.of("rank-a", "rank-b", "rank-c")).join();
        assertTrue(fake.groupNames("rank-a").isEmpty());
        assertEquals(List.of("rank-a"), fake.groupNames("rank-b"));
        assertEquals(List.of("rank-b"), fake.groupNames("rank-c"));

        UUID playerId = UUID.randomUUID();
        Set<String> managed = Set.of("rank-a", "rank-b", "rank-c", "prestige-coal");
        integration.repairGroups(playerId, managed, Set.of("rank-c", "prestige-coal")).join();
        assertEquals(Set.of("rank-c", "prestige-coal"), Set.copyOf(fake.userNames(playerId)));

        integration.replaceGroups(playerId, Set.of("rank-a", "rank-b", "rank-c"),
                "rank-c", "rank-a").join();
        assertEquals(Set.of("rank-a", "prestige-coal"), Set.copyOf(fake.userNames(playerId)));
    }

    private static final class FakeLuckPerms {
        private final Map<String, Holder> groups = new HashMap<>();
        private final Map<UUID, Holder> users = new HashMap<>();
        private final LuckPerms api;

        private FakeLuckPerms() {
            GroupManager groupManager = proxy(GroupManager.class, (proxy, method, args) -> switch (method.getName()) {
                case "loadGroup" -> CompletableFuture.completedFuture(Optional.ofNullable(groups.get((String) args[0]))
                        .map(holder -> (Group) holder.proxy()));
                case "createAndLoadGroup" -> {
                    String name = (String) args[0];
                    Holder holder = groups.computeIfAbsent(name, this::newGroup);
                    yield CompletableFuture.completedFuture((Group) holder.proxy());
                }
                case "saveGroup" -> CompletableFuture.completedFuture(null);
                case "getGroup" -> Optional.ofNullable(groups.get((String) args[0]))
                        .map(holder -> (Group) holder.proxy()).orElse(null);
                case "getLoadedGroups" -> groups.values().stream()
                        .map(holder -> (Group) holder.proxy()).collect(Collectors.toSet());
                case "isLoaded" -> groups.containsKey((String) args[0]);
                default -> defaultValue(method.getReturnType());
            });

            UserManager userManager = proxy(UserManager.class, (proxy, method, args) -> switch (method.getName()) {
                case "loadUser" -> {
                    UUID id = (UUID) args[0];
                    Holder holder = users.computeIfAbsent(id, this::newUser);
                    yield CompletableFuture.completedFuture((User) holder.proxy());
                }
                case "saveUser" -> CompletableFuture.completedFuture(null);
                case "getUser" -> args[0] instanceof UUID id && users.containsKey(id)
                        ? (User) users.get(id).proxy() : null;
                case "isLoaded" -> users.containsKey((UUID) args[0]);
                default -> defaultValue(method.getReturnType());
            });

            NodeBuilderRegistry registry = proxy(NodeBuilderRegistry.class, (proxy, method, args) -> {
                if (!method.getName().equals("forInheritance")) {
                    return defaultValue(method.getReturnType());
                }
                String[] group = new String[1];
                return proxy(InheritanceNode.Builder.class, (builderProxy, builderMethod, builderArgs) ->
                        switch (builderMethod.getName()) {
                            case "group" -> {
                                group[0] = builderArgs[0] instanceof String text
                                        ? text : ((Group) builderArgs[0]).getName();
                                yield builderProxy;
                            }
                            case "build" -> inheritanceNode(group[0]);
                            case "value", "negated", "expiry", "clearExpiry", "context", "withContext",
                                    "withMetadata" -> builderProxy;
                            default -> defaultValue(builderMethod.getReturnType());
                        });
            });

            PluginMetadata metadata = proxy(PluginMetadata.class, (proxy, method, args) -> switch (method.getName()) {
                case "getVersion" -> "5.4.164";
                case "getApiVersion" -> "5.4";
                default -> defaultValue(method.getReturnType());
            });

            api = proxy(LuckPerms.class, (proxy, method, args) -> switch (method.getName()) {
                case "getGroupManager" -> groupManager;
                case "getUserManager" -> userManager;
                case "getNodeBuilderRegistry" -> registry;
                case "getPluginMetadata" -> metadata;
                default -> defaultValue(method.getReturnType());
            });
        }

        private LuckPerms api() {
            return api;
        }

        private List<String> groupNames(String name) {
            return groups.get(name).nodes().stream().map(InheritanceNode::getGroupName).toList();
        }

        private List<String> userNames(UUID id) {
            return users.get(id).nodes().stream().map(InheritanceNode::getGroupName).toList();
        }

        private Holder newGroup(String name) {
            List<InheritanceNode> nodes = new ArrayList<>();
            NodeMap data = nodeMap(nodes);
            Group group = proxy(Group.class, (proxy, method, args) -> switch (method.getName()) {
                case "getName", "getFriendlyName" -> name;
                case "data" -> data;
                case "getNodes" -> List.copyOf(nodes);
                default -> defaultValue(method.getReturnType());
            });
            return new Holder(group, nodes);
        }

        private Holder newUser(UUID id) {
            List<InheritanceNode> nodes = new ArrayList<>();
            NodeMap data = nodeMap(nodes);
            User user = proxy(User.class, (proxy, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "getUsername", "getFriendlyName" -> id.toString();
                case "data" -> data;
                case "getNodes" -> List.copyOf(nodes);
                default -> defaultValue(method.getReturnType());
            });
            return new Holder(user, nodes);
        }
    }

    private record Holder(Object proxy, List<InheritanceNode> nodes) {
    }

    private static NodeMap nodeMap(List<InheritanceNode> nodes) {
        return proxy(NodeMap.class, (proxy, method, args) -> switch (method.getName()) {
            case "add" -> {
                nodes.add((InheritanceNode) (Node) args[0]);
                yield DataMutateResult.SUCCESS;
            }
            case "remove" -> {
                nodes.remove(args[0]);
                yield DataMutateResult.SUCCESS;
            }
            case "toCollection" -> List.copyOf(nodes);
            case "clear" -> {
                nodes.clear();
                yield null;
            }
            default -> defaultValue(method.getReturnType());
        });
    }

    private static InheritanceNode inheritanceNode(String groupName) {
        return proxy(InheritanceNode.class, (proxy, method, args) -> switch (method.getName()) {
            case "getGroupName" -> groupName;
            case "getKey" -> "group." + groupName;
            case "getValue" -> true;
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "InheritanceNode(" + groupName + ')';
            default -> defaultValue(method.getReturnType());
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }
}
