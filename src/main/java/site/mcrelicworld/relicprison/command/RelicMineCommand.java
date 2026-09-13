package site.mcrelicworld.relicprison.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import site.mcrelicworld.relicprison.RelicPrisonPlugin;
import site.mcrelicworld.relicprison.mine.MineDefinition;
import site.mcrelicworld.relicprison.mine.MineSpawn;
import site.mcrelicworld.relicprison.mine.StructureOperationRecord;
import site.mcrelicworld.relicprison.mine.composition.MineComposition;
import site.mcrelicworld.relicprison.selection.SelectionManager;
import site.mcrelicworld.relicprison.selection.SelectionMode;
import site.mcrelicworld.relicprison.selection.SelectionSession;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class RelicMineCommand implements CommandExecutor, TabCompleter {
    private final RelicPrisonPlugin plugin;

    public RelicMineCommand(RelicPrisonPlugin plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("relicprison.admin.mine")) {
            plugin.messages().send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            usage(sender, label);
            return true;
        }
        String auditAction = auditAction(args);
        String auditTarget = auditTarget(args);
        try {
            boolean handled = switch (args[0].toLowerCase(Locale.ROOT)) {
                case "wand", "select" -> wand(sender);
                case "create" -> beginSelection(sender, args, SelectionMode.CREATE);
                case "resize" -> beginSelection(sender, args, SelectionMode.RESIZE);
                case "move" -> beginSelection(sender, args, SelectionMode.MOVE);
                case "copy" -> beginCopy(sender, args);
                case "delete" -> beginDelete(sender, args);
                case "metadata", "meta" -> metadata(sender, args);
                case "rename" -> rename(sender, args);
                case "sort" -> sort(sender, args);
                case "requirement", "require" -> requirement(sender, args);
                case "confirm" -> confirm(sender);
                case "cancel" -> cancel(sender);
                case "list" -> list(sender);
                case "info" -> info(sender, args);
                case "setspawn" -> setSpawn(sender, args);
                case "enable" -> enable(sender, args, true);
                case "disable" -> enable(sender, args, false);
                case "tp" -> teleport(sender, args);
                case "composition", "comp" -> composition(sender, args);
                case "reset" -> reset(sender, args);
                case "recount" -> recount(sender, args);
                case "structure" -> structure(sender, args);
                case "resetconfig", "resetsettings" -> resetConfig(sender, args);
                case "resethook" -> resetHook(sender, args);
                case "gui" -> gui(sender);
                case "help" -> { usage(sender, page(args, 1)); yield true; }
                default -> {
                    plugin.messages().error(sender, "Unknown mine subcommand &f" + args[0] + "&c.");
                    plugin.messages().styled(sender, "&7Use &e/" + label + " help &7to view available commands.");
                    yield true;
                }
            };
            if (auditAction != null) {
                audit(sender, auditAction, "mine", auditTarget, "", "accepted", true, "command",
                        "args=" + String.join(" ", args));
            }
            return handled;
        } catch (IllegalArgumentException ex) {
            if (auditAction != null) {
                audit(sender, auditAction, "mine", auditTarget, "", "rejected", false, ex.getMessage(),
                        "args=" + String.join(" ", args));
            }
            if (ex.getMessage() != null && ex.getMessage().startsWith("Usage:")) {
                plugin.messages().usage(sender, ex.getMessage());
            } else {
                plugin.messages().error(sender, rootMessage(ex));
            }
            return true;
        } catch (Exception ex) {
            if (auditAction != null) {
                audit(sender, auditAction, "mine", auditTarget, "", "failed", false, rootMessage(ex),
                        "args=" + String.join(" ", args));
            }
            plugin.messages().error(sender, "Mine operation failed: &f" + rootMessage(ex));
            plugin.getLogger().log(Level.SEVERE, "Mine command failed", ex);
            return true;
        }
    }

    private boolean wand(CommandSender sender) {
        Player player = requirePlayer(sender);
        ItemStack wand = new ItemStack(plugin.config().snapshot().selection().wandMaterial());
        ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "RelicPrison Mine Selector");
        meta.setLore(List.of(ChatColor.YELLOW + "Left-click: point 1", ChatColor.YELLOW + "Right-click: point 2"));
        meta.getPersistentDataContainer().set(plugin.wandListener().wandKey(), PersistentDataType.BYTE, (byte) 1);
        wand.setItemMeta(meta);
        player.getInventory().addItem(wand);
        plugin.messages().send(player, "selection-wand-given");
        return true;
    }

    private boolean beginSelection(CommandSender sender, String[] args, SelectionMode mode) {
        Player player = requirePlayer(sender);
        requireArgs(args, 2, "/relicmine " + args[0] + " <mine>");
        String requested = args[1];
        String id = MineDefinition.normalizeId(requested);
        MineDefinition existing = plugin.mineService().findMine(id).orElse(null);
        if (mode == SelectionMode.CREATE && existing != null) {
            plugin.messages().send(player, "mine-exists", Map.of("mine", id));
            return true;
        }
        if (mode != SelectionMode.CREATE && existing == null) {
            plugin.messages().send(player, "mine-not-found", Map.of("mine", id));
            return true;
        }
        SelectionManager.ResolvedSelection selection = plugin.selections().resolve(player).orElse(null);
        if (selection == null) {
            plugin.messages().send(player, "selection-incomplete");
            return true;
        }
        plugin.selections().beginPreview(player, mode, id, selection.world(), selection.cuboid());
        plugin.messages().send(player, "selection-preview", Map.of("mine", id));
        return true;
    }

    private boolean beginCopy(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        requireArgs(args, 3, "/relicmine copy <source-mine> <new-mine> [display name]");
        MineDefinition source = requireMine(args[1]);
        String newId = MineDefinition.normalizeId(args[2]);
        if (plugin.mineService().findMine(newId).isPresent()) {
            plugin.messages().send(player, "mine-exists", Map.of("mine", newId));
            return true;
        }
        String displayName = args.length > 3
                ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length))
                : newId.toUpperCase(Locale.ROOT);
        SelectionManager.ResolvedSelection selection = plugin.selections().resolve(player).orElse(null);
        if (selection == null) {
            plugin.messages().send(player, "selection-incomplete");
            return true;
        }
        plugin.selections().beginPreview(player, SelectionMode.COPY, newId, source.id(), displayName,
                selection.world(), selection.cuboid());
        plugin.messages().send(player, "selection-preview", Map.of("mine", newId));
        return true;
    }

    private boolean beginDelete(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        requireArgs(args, 2, "/relicmine delete <mine>");
        MineDefinition mine = requireMine(args[1]);
        World world = Bukkit.getWorld(mine.worldId());
        if (world == null) throw new IllegalArgumentException("Mine world is not loaded: " + mine.worldName());
        plugin.selections().beginPreview(player, SelectionMode.DELETE, mine.id(), world, mine.bounds());
        plugin.messages().warning(player, "This permanently deletes Mine &f" + mine.id() + "&e.");
        plugin.messages().styled(player, "&7Use &e/relicmine confirm &7to continue or &e/relicmine cancel &7to stop.");
        return true;
    }

    private boolean rename(CommandSender sender, String[] args) throws Exception {
        requireArgs(args, 3, "/relicmine rename <mine> <new-id> [display name]");
        MineDefinition mine = requireMine(args[1]);
        String newId = MineDefinition.normalizeId(args[2]);
        String displayName = args.length > 3 ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)) : args[2].toUpperCase(Locale.ROOT);
        plugin.mineService().rename(mine.id(), newId, displayName);
        plugin.messages().send(sender, "mine-updated", Map.of("mine", newId));
        return true;
    }

    private boolean sort(CommandSender sender, String[] args) throws Exception {
        requireArgs(args, 3, "/relicmine sort <mine> <order>");
        MineDefinition mine = requireMine(args[1]);
        int order;
        try { order = Integer.parseInt(args[2]); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("Sort order must be an integer"); }
        plugin.mineService().update(mine.withSortOrder(order));
        plugin.messages().send(sender, "mine-updated", Map.of("mine", mine.id()));
        return true;
    }

    private boolean requirement(CommandSender sender, String[] args) throws Exception {
        requireArgs(args, 4, "/relicmine requirement <mine> <rank|prestige|permission|clear> <value|all>");
        MineDefinition mine = requireMine(args[1]);
        String type = args[2].toLowerCase(Locale.ROOT);
        MineDefinition updated = switch (type) {
            case "rank" -> mine.withRequirements(args[3], mine.requiredPrestige());
            case "prestige" -> mine.withRequirements(mine.requiredRank(), args[3]);
            case "permission" -> mine.withAccessPermission(args[3]);
            case "clear" -> {
                String target = args[3].toLowerCase(Locale.ROOT);
                yield switch (target) {
                    case "rank" -> mine.withRequirements(null, mine.requiredPrestige());
                    case "prestige" -> mine.withRequirements(mine.requiredRank(), null);
                    case "permission" -> mine.withAccessPermission(null);
                    case "all" -> mine.withRequirements(null, null).withAccessPermission(null);
                    default -> throw new IllegalArgumentException("Clear target must be rank, prestige, permission, or all");
                };
            }
            default -> throw new IllegalArgumentException("Requirement type must be rank, prestige, permission, or clear");
        };
        plugin.mineService().update(updated);
        plugin.messages().send(sender, "mine-updated", Map.of("mine", mine.id()));
        return true;
    }

    private boolean confirm(CommandSender sender) throws Exception {
        Player player = requirePlayer(sender);
        SelectionSession.PendingAction pending = plugin.selections().pending(player);
        if (pending == null) throw new IllegalArgumentException("You do not have a pending mine action");
        switch (pending.mode()) {
            case CREATE -> {
                MineDefinition created = new MineDefinition(
                        pending.mineId(), pending.mineId().toUpperCase(Locale.ROOT), pending.worldId(), pending.worldName(), pending.bounds(),
                        null, true, plugin.mineService().mines().size(), null, null, null, Map.of(),
                        MineComposition.defaultStone(), site.mcrelicworld.relicprison.mine.MineResetConfig.defaults(
                                plugin.config().snapshot().resetEngine().defaultIntervalSeconds(),
                                plugin.config().snapshot().resetEngine().defaultMinedPercentage(),
                                plugin.config().snapshot().resetEngine().defaultWarnings()));
                plugin.mineService().create(created);
                plugin.messages().send(player, "mine-created", Map.of("mine", created.id()));
            }
            case RESIZE -> {
                MineDefinition mine = requireMine(pending.mineId());
                plugin.mineService().update(mine.withBounds(pending.worldId(), pending.worldName(), pending.bounds()));
                plugin.messages().send(player, "mine-updated", Map.of("mine", mine.id()));
            }
            case MOVE -> {
                MineDefinition mine = requireMine(pending.mineId());
                MineDefinition moved = mine.withBounds(pending.worldId(), pending.worldName(), pending.bounds());
                if (mine.spawn() != null && mine.worldId().equals(pending.worldId())) {
                    int dx = pending.bounds().minimum().x() - mine.bounds().minimum().x();
                    int dy = pending.bounds().minimum().y() - mine.bounds().minimum().y();
                    int dz = pending.bounds().minimum().z() - mine.bounds().minimum().z();
                    moved = moved.withSpawn(new MineSpawn(mine.spawn().x() + dx, mine.spawn().y() + dy, mine.spawn().z() + dz, mine.spawn().yaw(), mine.spawn().pitch()));
                } else if (!mine.worldId().equals(pending.worldId())) moved = moved.withSpawn(null);
                startStructureOperation(player, mine, moved, true, false);
                plugin.selections().clearPending(player);
                return true;
            }
            case COPY -> {
                MineDefinition source = requireMine(pending.sourceMineId());
                MineDefinition copied = source.withIdentity(pending.mineId(), pending.displayName()).withBounds(
                        pending.worldId(), pending.worldName(), pending.bounds());
                if (source.spawn() != null && source.worldId().equals(pending.worldId())) {
                    int dx = pending.bounds().minimum().x() - source.bounds().minimum().x();
                    int dy = pending.bounds().minimum().y() - source.bounds().minimum().y();
                    int dz = pending.bounds().minimum().z() - source.bounds().minimum().z();
                    copied = copied.withSpawn(new MineSpawn(source.spawn().x() + dx, source.spawn().y() + dy,
                            source.spawn().z() + dz, source.spawn().yaw(), source.spawn().pitch()));
                } else {
                    copied = copied.withSpawn(null);
                }
                startStructureOperation(player, source, copied, false, true);
                plugin.selections().clearPending(player);
                return true;
            }
            case DELETE -> {
                plugin.mineService().delete(pending.mineId());
                plugin.messages().send(player, "mine-deleted", Map.of("mine", pending.mineId()));
            }
        }
        plugin.selections().clearPending(player);
        return true;
    }

    private void startStructureOperation(Player player, MineDefinition source, MineDefinition target,
                                         boolean clearSource, boolean createTarget) {
        if (clearSource == createTarget) {
            throw new IllegalArgumentException("Invalid structure operation mode");
        }
        if (plugin.mineResets().isResetting(source.id())) {
            throw new IllegalArgumentException("Mine structure operations cannot run during an active reset");
        }
        if (plugin.mineStructures().isLocked(source.id()) || plugin.mineStructures().isLocked(target.id())) {
            throw new IllegalArgumentException("A structure operation is already using this mine");
        }
        World destinationWorld = Bukkit.getWorld(target.worldId());
        if (destinationWorld == null) throw new IllegalArgumentException("Destination world is not loaded: " + target.worldName());
        plugin.messages().send(player, "mine-structure-operation-started", Map.of("mine", target.displayName()));
        plugin.mineStructures().execute(player.getUniqueId(), source, target, createTarget)
                .whenComplete((record, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        audit(player, createTarget ? "mine_copy" : "mine_move", "mine", target.id(),
                                source.id(), "failed", false, rootMessage(error), "structure-operation");
                        plugin.messages().send(player, "mine-structure-operation-failed",
                                Map.of("mine", target.displayName(), "error", rootMessage(error)));
                        return;
                    }
                    audit(player, createTarget ? "mine_copy" : "mine_move", "mine", target.id(),
                            source.id(), target.id(), true, "structure-operation", "operation=" + record.id());
                    plugin.messages().send(player, createTarget ? "mine-created" : "mine-updated",
                            Map.of("mine", target.id()));
                    plugin.messages().info(player, "Structure Operation: &f" + record.id());
                }));
    }

    private boolean structure(CommandSender sender, String[] args) {
        requireArgs(args, 2, "/relicmine structure <list|info|retry|rollback> [operation-id]");
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("list")) {
            Collection<StructureOperationRecord> records = plugin.mineStructures().operations();
            plugin.messages().sectionHeader(sender, "Structure Operations &8(&f" + records.size() + "&8)", "&b");
            int shown = 0;
            for (StructureOperationRecord record : records) {
                if (shown++ >= 20) break;
                plugin.messages().styled(sender, formatStructureOperation(record));
            }
            if (records.isEmpty()) plugin.messages().styled(sender, "&7No structure operations were found.");
            if (records.size() > 20) plugin.messages().styled(sender, "&8Showing the newest 20 operations.");
            plugin.messages().sectionFooter(sender);
            return true;
        }
        requireArgs(args, 3, "/relicmine structure " + action + " <operation-id>");
        UUID operationId = parseOperationId(args[2]);
        if (action.equals("info")) {
            StructureOperationRecord record = plugin.mineStructures().operation(operationId)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown structure operation: " + operationId));
            plugin.messages().sectionHeader(sender, "Structure Operation &f" + record.id(), "&b");
            plugin.messages().field(sender, "Type", "&f", record.type());
            plugin.messages().field(sender, "State", "&b", record.stage());
            plugin.messages().field(sender, "Mine", "&f", record.source().id() + " &8-> &f" + record.target().id());
            plugin.messages().field(sender, "World", "&f",
                    record.source().worldName() + " &8-> &f" + record.target().worldName());
            plugin.messages().field(sender, "Provider", "&f", record.provider());
            plugin.messages().field(sender, "Copied / Cleared", "&b",
                    plugin.numbers().full(record.copiedBlocks()) + " &8/ &b"
                            + plugin.numbers().full(record.clearedBlocks()));
            plugin.messages().field(sender, "Recoverable", record.recoverable() ? "&a" : "&c", record.recoverable());
            if (record.failure() != null) plugin.messages().field(sender, "Failure", "&c", record.failure());
            plugin.messages().sectionFooter(sender);
            return true;
        }
        if (!action.equals("retry") && !action.equals("rollback")) {
            throw new IllegalArgumentException("Structure action must be list, info, retry, or rollback");
        }
        plugin.messages().warning(sender, (action.equals("retry") ? "Retrying" : "Rolling back")
                + " structure operation &f" + operationId + "&e...");
        var future = action.equals("retry")
                ? plugin.mineStructures().retry(operationId)
                : plugin.mineStructures().rollback(operationId);
        future.whenComplete((record, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null) {
                plugin.messages().error(sender, "Structure operation failed: &f" + rootMessage(error));
                return;
            }
            plugin.messages().success(sender, "Structure operation &f" + record.id() + " &ais now &b"
                    + record.stage() + "&a.");
        }));
        return true;
    }

    private static UUID parseOperationId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid structure operation UUID: " + raw);
        }
    }

    private static String formatStructureOperation(StructureOperationRecord record) {
        String color = record.stage().terminal() ? "&7" : "&e";
        return " " + color + record.id() + " &8| &f" + record.type() + " " + record.source().id()
                + " &8-> &f" + record.target().id() + " &8| " + color + record.stage();
    }

    private boolean cancel(CommandSender sender) {
        Player player = requirePlayer(sender);
        plugin.selections().clearPending(player);
        plugin.messages().send(player, "selection-cancelled");
        return true;
    }

    private boolean list(CommandSender sender) {
        Collection<MineDefinition> mines = plugin.mineService().mines();
        plugin.messages().sectionHeader(sender, "Configured Mines &8(&f" + mines.size() + "&8)", "&b");
        if (mines.isEmpty()) plugin.messages().styled(sender, "&7No mines are configured.");
        for (MineDefinition mine : mines) {
            plugin.messages().styled(sender, " " + (mine.enabled() ? "&a" : "&c") + mine.id()
                    + " &8| &f" + mine.worldName() + " &8| &b" + plugin.numbers().full(mine.volume())
                    + " &7blocks &8| " + (mine.enabled() ? "&aEnabled" : "&cDisabled"));
        }
        plugin.messages().sectionFooter(sender);
        return true;
    }

    private boolean info(CommandSender sender, String[] args) {
        requireArgs(args, 2, "/relicmine info <mine>");
        MineDefinition mine = requireMine(args[1]);
        plugin.messages().sectionHeader(sender, "Mine " + mine.displayName(), "&b");
        plugin.messages().field(sender, "ID", "&f", mine.id());
        plugin.messages().field(sender, "World", "&f", mine.worldName());
        plugin.messages().field(sender, "Minimum", "&f", mine.bounds().minimum());
        plugin.messages().field(sender, "Maximum", "&f", mine.bounds().maximum());
        plugin.messages().field(sender, "Volume", "&b", plugin.numbers().full(mine.volume()) + " blocks");
        plugin.messages().field(sender, "Status", mine.enabled() ? "&a" : "&c",
                mine.enabled() ? "Enabled" : "Disabled");
        plugin.messages().field(sender, "Sort Order", "&f", mine.sortOrder());
        plugin.messages().field(sender, "Required Rank", "&b", mine.requiredRank() == null ? "None" : mine.requiredRank());
        plugin.messages().field(sender, "Required Prestige", "&d",
                mine.requiredPrestige() == null ? "None" : mine.requiredPrestige());
        plugin.messages().field(sender, "Spawn", "&f", mine.spawn() == null ? "Not set" : mine.spawn());
        plugin.messages().field(sender, "Composition Weight", "&b", mine.composition().totalWeight());
        plugin.messages().field(sender, "Remaining Blocks", "&b",
                plugin.numbers().full(plugin.mineResets().remainingBlocks(mine.id())) + " &8(&f"
                        + String.format(Locale.US, "%.2f", plugin.mineResets().minedPercentage(mine.id())) + "% mined&8)");
        plugin.messages().field(sender, "Reset State", "&b",
                plugin.mineResets().state(mine.id()).map(state -> state.state().name()).orElse("UNKNOWN"));
        plugin.messages().field(sender, "Reset Count", "&f", plugin.mineResets().resetCount(mine.id()));
        plugin.messages().field(sender, "Reset Interval", "&f", mine.resetConfig().intervalSeconds() + "s");
        plugin.messages().field(sender, "Reset Threshold", "&f", mine.resetConfig().minedPercentage() + "%");
        plugin.messages().sectionFooter(sender);
        return true;
    }

    private boolean metadata(CommandSender sender, String[] args) throws Exception {
        requireArgs(args, 3, "/relicmine metadata <mine> <list|set|remove> [key] [value]");
        MineDefinition mine = requireMine(args[1]);
        String action = args[2].toLowerCase(Locale.ROOT);
        if (action.equals("list")) {
            plugin.messages().sectionHeader(sender, "Metadata for " + mine.id(), "&b");
            if (mine.metadata().isEmpty()) {
                plugin.messages().styled(sender, "&7No metadata is configured.");
            } else {
                mine.metadata().forEach((key, value) -> plugin.messages().field(sender, key, "&f", value));
            }
            plugin.messages().sectionFooter(sender);
            return true;
        }
        Map<String, String> metadata = new LinkedHashMap<>(mine.metadata());
        switch (action) {
            case "set" -> {
                requireArgs(args, 5, "/relicmine metadata <mine> set <key> <value>");
                metadata.put(args[3].toLowerCase(Locale.ROOT), String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length)));
            }
            case "remove" -> {
                requireArgs(args, 4, "/relicmine metadata <mine> remove <key>");
                metadata.remove(args[3].toLowerCase(Locale.ROOT));
            }
            default -> throw new IllegalArgumentException("Metadata action must be list, set, or remove");
        }
        plugin.mineService().update(mine.withMetadata(metadata));
        plugin.messages().send(sender, "mine-updated", Map.of("mine", mine.id()));
        return true;
    }

    private boolean setSpawn(CommandSender sender, String[] args) throws Exception {
        Player player = requirePlayer(sender);
        requireArgs(args, 2, "/relicmine setspawn <mine>");
        MineDefinition mine = requireMine(args[1]);
        if (!player.getWorld().getUID().equals(mine.worldId())) throw new IllegalArgumentException("You must stand in the mine's world");
        plugin.mineService().update(mine.withSpawn(MineSpawn.from(player.getLocation())));
        plugin.messages().send(player, "mine-spawn-set", Map.of("mine", mine.id()));
        return true;
    }

    private boolean enable(CommandSender sender, String[] args, boolean enabled) throws Exception {
        requireArgs(args, 2, "/relicmine " + args[0] + " <mine>");
        MineDefinition mine = requireMine(args[1]);
        plugin.mineService().update(mine.withEnabled(enabled));
        plugin.messages().send(sender, enabled ? "mine-enabled" : "mine-disabled", Map.of("mine", mine.id()));
        return true;
    }

    private boolean teleport(CommandSender sender, String[] args) {
        requireArgs(args, 2, "/relicmine tp <mine> [player]");
        MineDefinition mine = requireMine(args[1]);
        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayer(args[2]);
            if (target == null) throw new IllegalArgumentException("Player is not online: " + args[2]);
        } else {
            target = requirePlayer(sender);
        }
        World world = Bukkit.getWorld(mine.worldId());
        if (world == null) throw new IllegalArgumentException("Mine world is not loaded");
        Location destination = mine.spawn() == null ? world.getSpawnLocation() : mine.spawn().toLocation(world);
        target.teleport(destination);
        return true;
    }

    private boolean gui(CommandSender sender) {
        plugin.mineAdminGui().open(requirePlayer(sender));
        return true;
    }

    private boolean composition(CommandSender sender, String[] args) throws Exception {
        requireArgs(args, 3, "/relicmine composition <mine> <list|set|remove|normalize>");
        MineDefinition mine = requireMine(args[1]);
        String action = args[2].toLowerCase(Locale.ROOT);
        if (action.equals("list")) {
            double total = mine.composition().totalWeight();
            plugin.messages().sectionHeader(sender, "Composition for " + mine.id(), "&b");
            plugin.messages().field(sender, "Total Weight", "&b", total);
            mine.composition().weights().forEach((block, weight) -> plugin.messages().styled(sender, " &e" + block
                    + " &8- &f" + weight + " weight &8(&b"
                    + String.format(Locale.US, "%.2f", weight / total * 100.0) + "%&8)"));
            plugin.messages().sectionFooter(sender);
            return true;
        }
        MineComposition updated;
        try {
            updated = switch (action) {
                case "set", "add" -> {
                    requireArgs(args, 5, "/relicmine composition <mine> set <block> <weight>");
                    double weight = Double.parseDouble(args[4]);
                    yield mine.composition().with(args[3], weight);
                }
                case "remove" -> {
                    requireArgs(args, 4, "/relicmine composition <mine> remove <block>");
                    yield mine.composition().without(args[3]);
                }
                case "normalize" -> mine.composition().normalized();
                case "copy" -> {
                    requireArgs(args, 4, "/relicmine composition <mine> copy <source-mine>");
                    yield requireMine(args[3]).composition();
                }
                default -> throw new IllegalArgumentException("Unknown composition action: " + action);
            };
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Weight must be a number");
        }
        plugin.mineService().update(mine.withComposition(updated));
        plugin.messages().send(sender, "composition-updated", Map.of("mine", mine.id()));
        return true;
    }

    private boolean reset(CommandSender sender, String[] args) {
        if (args.length >= 3 && args[1].equalsIgnoreCase("retry")) {
            MineDefinition mine = requireMine(args[2]);
            retryReset(sender, mine);
            return true;
        }
        requireArgs(args, 2, "/relicmine reset <mine> [now|warn|cancel|retry]");
        MineDefinition mine = requireMine(args[1]);
        if (args.length >= 3 && args[2].equalsIgnoreCase("retry")) {
            retryReset(sender, mine);
            return true;
        }
        if (args.length >= 3 && args[2].equalsIgnoreCase("cancel")) {
            if (!plugin.mineResets().cancelPendingReset(mine.id())) {
                throw new IllegalArgumentException("Only a warning or queued reset can be cancelled safely");
            }
            plugin.messages().send(sender, "reset-cancelled", Map.of("mine", mine.displayName()));
            return true;
        }
        boolean warnings = args.length >= 3 && args[2].equalsIgnoreCase("warn");
        if (!plugin.mineResets().requestReset(mine.id(), "manual", warnings)) {
            throw new IllegalArgumentException("Mine is already queued, warning, or resetting");
        }
        plugin.messages().send(sender, "reset-queued", Map.of("mine", mine.displayName()));
        return true;
    }

    private void retryReset(CommandSender sender, MineDefinition mine) {
        plugin.mineResets().retryFailedReset(mine.id(), sender).whenComplete((queued, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        audit(sender, "failed_reset_retry", "mine", mine.id(), "", "failed", false,
                                rootMessage(error), "command");
                        plugin.messages().error(sender, "Reset retry failed: &f" + rootMessage(error));
                        return;
                    }
                    audit(sender, "failed_reset_retry", "mine", mine.id(), "", queued ? "queued" : "not-queued",
                            queued, "command", "");
                    if (!queued) plugin.messages().error(sender, "No safe retryable reset failure exists for Mine &f"
                            + mine.id() + "&c.");
                }));
    }

    private boolean recount(CommandSender sender, String[] args) {
        requireArgs(args, 2, "/relicmine recount <mine>");
        MineDefinition mine = requireMine(args[1]);
        if (!plugin.mineResets().recount(mine.id(), sender, "manual")) {
            throw new IllegalArgumentException("Mine recount could not start; check world load and active reset state");
        }
        plugin.messages().send(sender, "mine-recount-started", Map.of("mine", mine.displayName()));
        return true;
    }

    private boolean resetConfig(CommandSender sender, String[] args) throws Exception {
        requireArgs(args, 4, "/relicmine resetconfig <mine> <setting> <value>");
        MineDefinition mine = requireMine(args[1]);
        site.mcrelicworld.relicprison.mine.MineResetConfig current = mine.resetConfig();
        String setting = args[2].toLowerCase(Locale.ROOT);
        site.mcrelicworld.relicprison.mine.MineResetConfig updated;
        try {
            updated = switch (setting) {
                case "interval" -> copyReset(current, parsePositiveInt(args[3], "Interval"), null, null, null, null, null, null);
                case "percentage" -> copyReset(current, null, Double.parseDouble(args[3]), null, null, null, null, null);
                case "timed" -> copyReset(current, null, null, parseBoolean(args[3]), null, null, null, null);
                case "percentenabled" -> copyReset(current, null, null, null, parseBoolean(args[3]), null, null, null);
                case "scope" -> copyReset(current, null, null, null, null,
                        site.mcrelicworld.relicprison.mine.MineResetConfig.NotificationScope.valueOf(args[3].toUpperCase(Locale.ROOT)), null, null);
                case "radius" -> copyReset(current, null, null, null, null, null, parsePositiveInt(args[3], "Radius"), null);
                case "evacuate" -> copyReset(current, null, null, null, null, null, null, parseBoolean(args[3]));
                case "order" -> new site.mcrelicworld.relicprison.mine.MineResetConfig(current.timedEnabled(), current.intervalSeconds(),
                        current.percentageEnabled(), current.minedPercentage(), current.warningSeconds(), current.notificationScope(),
                        current.notificationRadius(), current.evacuatePlayers(),
                        site.mcrelicworld.relicprison.mine.MineResetConfig.ResetOrder.valueOf(args[3].toUpperCase(Locale.ROOT)),
                        current.beforeCommands(), current.startCommands(), current.completeCommands(), current.failedCommands(),
                        current.enabled(), current.retryCount(), current.retryDelaySeconds(), current.recountBehavior(),
                        current.teleportDestination(), current.requireOutsideDestination());
                case "warnings" -> new site.mcrelicworld.relicprison.mine.MineResetConfig(current.timedEnabled(), current.intervalSeconds(),
                        current.percentageEnabled(), current.minedPercentage(), parseWarnings(args[3]), current.notificationScope(),
                        current.notificationRadius(), current.evacuatePlayers(), current.order(), current.beforeCommands(),
                        current.startCommands(), current.completeCommands(), current.failedCommands(), current.enabled(),
                        current.retryCount(), current.retryDelaySeconds(), current.recountBehavior(),
                        current.teleportDestination(), current.requireOutsideDestination());
                default -> throw new IllegalArgumentException("Setting must be interval, percentage, timed, percentenabled, scope, radius, evacuate, order, or warnings");
            };
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Value must be a valid number");
        }
        plugin.mineService().update(mine.withResetConfig(updated));
        plugin.messages().send(sender, "reset-config-updated", Map.of("mine", mine.displayName(), "setting", setting));
        return true;
    }

    private boolean resetHook(CommandSender sender, String[] args) throws Exception {
        requireArgs(args, 4, "/relicmine resethook <mine> <before|start|complete|failed> <add|clear> [command]");
        MineDefinition mine = requireMine(args[1]);
        String phase = args[2].toLowerCase(Locale.ROOT);
        String action = args[3].toLowerCase(Locale.ROOT);
        site.mcrelicworld.relicprison.mine.MineResetConfig current = mine.resetConfig();
        List<String> before = new ArrayList<>(current.beforeCommands());
        List<String> start = new ArrayList<>(current.startCommands());
        List<String> complete = new ArrayList<>(current.completeCommands());
        List<String> failed = new ArrayList<>(current.failedCommands());
        List<String> target = switch (phase) {
            case "before" -> before;
            case "start" -> start;
            case "complete" -> complete;
            case "failed" -> failed;
            default -> throw new IllegalArgumentException("Hook phase must be before, start, complete, or failed");
        };
        if (action.equals("clear")) target.clear();
        else if (action.equals("add")) {
            requireArgs(args, 5, "/relicmine resethook <mine> <phase> add <command>");
            target.add(String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length)));
        } else throw new IllegalArgumentException("Hook action must be add or clear");
        plugin.mineService().update(mine.withResetConfig(new site.mcrelicworld.relicprison.mine.MineResetConfig(
                current.timedEnabled(), current.intervalSeconds(), current.percentageEnabled(), current.minedPercentage(),
                current.warningSeconds(), current.notificationScope(), current.notificationRadius(), current.evacuatePlayers(),
                current.order(), before, start, complete, failed, current.enabled(), current.retryCount(),
                current.retryDelaySeconds(), current.recountBehavior(), current.teleportDestination(),
                current.requireOutsideDestination())));
        plugin.messages().send(sender, "reset-config-updated", Map.of("mine", mine.displayName(), "setting", "hook-" + phase));
        return true;
    }

    private static site.mcrelicworld.relicprison.mine.MineResetConfig copyReset(
            site.mcrelicworld.relicprison.mine.MineResetConfig current, Integer interval, Double percentage,
            Boolean timed, Boolean percentageEnabled,
            site.mcrelicworld.relicprison.mine.MineResetConfig.NotificationScope scope,
            Integer radius, Boolean evacuate) {
        return new site.mcrelicworld.relicprison.mine.MineResetConfig(
                timed == null ? current.timedEnabled() : timed,
                interval == null ? current.intervalSeconds() : interval,
                percentageEnabled == null ? current.percentageEnabled() : percentageEnabled,
                percentage == null ? current.minedPercentage() : percentage,
                current.warningSeconds(), scope == null ? current.notificationScope() : scope,
                radius == null ? current.notificationRadius() : radius,
                evacuate == null ? current.evacuatePlayers() : evacuate,
                current.order(), current.beforeCommands(), current.startCommands(), current.completeCommands(),
                current.failedCommands(), current.enabled(), current.retryCount(), current.retryDelaySeconds(),
                current.recountBehavior(), current.teleportDestination(), current.requireOutsideDestination());
    }

    private static boolean parseBoolean(String value) {
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on") || value.equalsIgnoreCase("yes")) return true;
        if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("off") || value.equalsIgnoreCase("no")) return false;
        throw new IllegalArgumentException("Value must be true or false");
    }

    private static int parsePositiveInt(String value, String name) {
        int parsed;
        try { parsed = Integer.parseInt(value); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException(name + " must be a whole number"); }
        if (parsed < 0) throw new IllegalArgumentException(name + " cannot be negative");
        return parsed;
    }

    private static List<Integer> parseWarnings(String input) {
        if (input.equalsIgnoreCase("none")) return List.of();
        List<Integer> result = new ArrayList<>();
        for (String part : input.split(",")) result.add(parsePositiveInt(part.trim(), "Warning"));
        return result.stream().distinct().sorted(java.util.Comparator.reverseOrder()).toList();
    }

    private MineDefinition requireMine(String id) {
        return plugin.mineService().findMine(id).orElseThrow(() -> new IllegalArgumentException("Mine does not exist: " + id));
    }

    private static Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) return player;
        throw new IllegalArgumentException("This command can only be used by a player");
    }

    private static void requireArgs(String[] args, int amount, String usage) {
        if (args.length < amount) throw new IllegalArgumentException("Usage: " + usage);
    }

    private void usage(CommandSender sender, String label) {
        CommandHelp.mineAdmin(plugin.messages(), sender, 1);
    }

    private void usage(CommandSender sender, int page) {
        CommandHelp.mineAdmin(plugin.messages(), sender, page);
    }

    private static int page(String[] args, int index) {
        if (args.length <= index) return 1;
        try { return Integer.parseInt(args[index]); }
        catch (NumberFormatException ignored) { return 1; }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        return String.valueOf(current.getMessage());
    }

    private void audit(CommandSender sender, String action, String targetType, String targetId, String before,
                       String after, boolean success, String reason, String metadata) {
        if (plugin.audit() == null) return;
        plugin.audit().record(sender, action, targetType, targetId, before, after, success, reason,
                "", metadata).exceptionally(error -> {
                    plugin.getLogger().warning("Unable to record mine audit: " + rootMessage(error));
                    return null;
                });
    }

    private static String auditAction(String[] args) {
        if (args.length == 0) return null;
        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("metadata") || action.equals("meta")) {
            return args.length >= 3 && !args[2].equalsIgnoreCase("list") ? "mine_metadata_edit" : null;
        }
        if (action.equals("composition") || action.equals("comp")) {
            return args.length >= 3 && !args[2].equalsIgnoreCase("list") ? "mine_composition_change" : null;
        }
        if (action.equals("resetconfig") || action.equals("resetsettings") || action.equals("resethook")) {
            return "mine_reset_settings_edit";
        }
        return switch (action) {
            case "create" -> "mine_create_preview";
            case "resize" -> "mine_resize_preview";
            case "move" -> "mine_move_preview";
            case "copy" -> "mine_copy_preview";
            case "delete" -> "mine_delete_preview";
            case "confirm" -> "mine_confirm";
            case "rename", "sort", "requirement", "require", "setspawn", "enable", "disable" -> "mine_edit";
            case "reset" -> "forced_reset";
            case "recount" -> "mine_recount";
            default -> null;
        };
    }

    private static String auditTarget(String[] args) {
        if (args.length >= 2) return args[1].toLowerCase(Locale.ROOT);
        return args.length == 0 ? "unknown" : args[0].toLowerCase(Locale.ROOT);
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return match(args[0], List.of("wand", "create", "resize", "move", "copy", "delete", "metadata", "rename", "sort", "requirement", "confirm", "cancel", "list", "info", "setspawn", "enable", "disable", "tp", "composition", "reset", "recount", "resetconfig", "resethook", "structure", "gui", "help"));
        if (args.length == 2 && args[0].equalsIgnoreCase("structure")) {
            return match(args[1], List.of("list", "info", "retry", "rollback"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("structure")
                && List.of("info", "retry", "rollback").contains(args[1].toLowerCase(Locale.ROOT))) {
            return match(args[2], plugin.mineStructures().operations().stream()
                    .map(record -> record.id().toString()).toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            List<String> values = new ArrayList<>(mineIds(args[1]));
            values.addAll(match(args[1], List.of("retry")));
            return values;
        }
        if (args.length == 2 && List.of("resize", "move", "copy", "delete", "metadata", "rename", "sort", "requirement", "info", "setspawn", "enable", "disable", "tp", "composition", "recount", "resetconfig", "resethook").contains(args[0].toLowerCase(Locale.ROOT))) return mineIds(args[1]);
        if (args.length == 3 && args[0].equalsIgnoreCase("composition")) return match(args[2], List.of("list", "set", "remove", "normalize", "copy"));
        if (args.length == 3 && args[0].equalsIgnoreCase("metadata")) return match(args[2], List.of("list", "set", "remove"));
        if (args.length == 3 && args[0].equalsIgnoreCase("reset") && args[1].equalsIgnoreCase("retry")) return mineIds(args[2]);
        if (args.length == 3 && args[0].equalsIgnoreCase("reset")) return match(args[2], List.of("now", "warn", "cancel", "retry"));
        if (args.length == 3 && (args[0].equalsIgnoreCase("resetconfig") || args[0].equalsIgnoreCase("resetsettings"))) return match(args[2], List.of("interval", "percentage", "timed", "percentenabled", "scope", "radius", "evacuate", "order", "warnings"));
        if (args.length == 3 && args[0].equalsIgnoreCase("resethook")) return match(args[2], List.of("before", "start", "complete", "failed"));
        if (args.length == 4 && args[0].equalsIgnoreCase("resethook")) return match(args[3], List.of("add", "clear"));
        if (args.length == 3 && (args[0].equalsIgnoreCase("requirement") || args[0].equalsIgnoreCase("require"))) return match(args[2], List.of("rank", "prestige", "permission", "clear"));
        if (args.length == 4 && (args[0].equalsIgnoreCase("requirement") || args[0].equalsIgnoreCase("require")) && args[2].equalsIgnoreCase("clear")) return match(args[3], List.of("rank", "prestige", "permission", "all"));
        if (args.length == 4 && args[0].equalsIgnoreCase("composition") && args[2].equalsIgnoreCase("set")) return match(args[3], List.of("STONE", "COBBLESTONE", "COAL_ORE", "IRON_ORE", "GOLD_ORE", "REDSTONE_ORE", "DIAMOND_ORE", "EMERALD_ORE"));
        return List.of();
    }

    private List<String> mineIds(String input) {
        return match(input, plugin.mineService().mines().stream().map(MineDefinition::id).toList());
    }

    private static List<String> match(String input, List<String> values) {
        String lower = input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) if (value.toLowerCase(Locale.ROOT).startsWith(lower)) result.add(value);
        return result;
    }
}
