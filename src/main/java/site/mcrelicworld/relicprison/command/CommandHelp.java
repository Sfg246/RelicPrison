package site.mcrelicworld.relicprison.command;

import org.bukkit.command.CommandSender;
import site.mcrelicworld.relicprison.message.MessageService;

import java.util.List;

final class CommandHelp {
    private CommandHelp() { }

    static void prison(MessageService messages, CommandSender sender, int page) {
        List<List<MessageService.HelpSection>> pages = List.of(
                List.of(section("Mining",
                        entry("/mine [mine]", "Open the mine menu or teleport to an unlocked mine"),
                        entry("/rankup", "Advance to the next rank"),
                        entry("/rankupmax", "Purchase every affordable rank"),
                        entry("/prestige", "Prestige after reaching the final rank"))),
                List.of(section("Menus",
                        entry("/prison", "Open the main prison menu"),
                        entry("/ranks", "View rank progression"),
                        entry("/prestiges", "View prestige progression"),
                        entry("/stats", "View your prison statistics"),
                        entry("/leaderboard [metric] [period]", "Browse prison leaderboards"))),
                List.of(section("Economy & Gangs",
                        entry("/sellall", "Sell configured items in your inventory"),
                        entry("/sellhand", "Sell the item in your hand"),
                        entry("/sellvalue [hand]", "Estimate sell value"),
                        entry("/booster status", "View your active boosters"),
                        entry("/gang", "Open your gang menu"),
                        entry("/gang help", "View gang commands"))));
        send(messages, sender, "RelicPrison", "prison", pages, page);
    }

    static void gang(MessageService messages, CommandSender sender, int page) {
        List<List<MessageService.HelpSection>> pages = List.of(
                List.of(section("Getting Started",
                        entry("/gang", "Open the gang menu"),
                        entry("/gang create <name> <tag>", "Create a new gang"),
                        entry("/gang invites", "View pending invitations"),
                        entry("/gang accept <invite-id>", "Accept an invitation"),
                        entry("/gang deny <invite-id>", "Decline an invitation"),
                        entry("/gang join <name|tag>", "Join an open gang"))),
                List.of(section("Members & Identity",
                        entry("/gang invite <player>", "Invite a player"),
                        entry("/gang leave", "Leave your current gang"),
                        entry("/gang kick <player>", "Remove a member"),
                        entry("/gang promote <player>", "Promote a member"),
                        entry("/gang demote <player>", "Demote a member"),
                        entry("/gang transfer <player>", "Transfer ownership"),
                        entry("/gang settings <field> <value>", "Edit gang identity or privacy"))),
                List.of(section("Progression & Utilities",
                        entry("/gang bank [balance]", "View the gang bank"),
                        entry("/gang bank <deposit|withdraw> <amount>", "Move gang funds"),
                        entry("/gang upgrades [upgrade]", "View or purchase upgrades"),
                        entry("/gang missions [claim <mission>]", "View or claim missions"),
                        entry("/gang ranks", "View gang ranks"),
                        entry("/gang chat [toggle|message]", "Use gang chat"),
                        entry("/gang sethome", "Set the gang home"),
                        entry("/gang home", "Teleport to the gang home"),
                        entry("/gang disband", "Permanently disband the gang"))));
        send(messages, sender, "Gang", "gang", pages, page);
    }

    static void booster(MessageService messages, CommandSender sender, int page) {
        List<List<MessageService.HelpSection>> pages = List.of(
                List.of(section("Player",
                        entry("/booster status [player]", "View active boosters and final multiplier"),
                        entry("/booster list", "List active server boosters"))),
                List.of(section("Administration",
                        entry("/booster give <player> <personal|server> <multiplier> <duration>", "Give a booster item"),
                        entry("/booster activate server <multiplier> <duration>", "Activate a server booster"),
                        entry("/booster activate personal <player> <multiplier> <duration>", "Activate a player booster"),
                        entry("/booster remove <player>", "Remove personal boosters"),
                        entry("/booster setmultiplier <player> <multiplier>", "Set a permanent multiplier"))));
        send(messages, sender, "Boosters", "booster", pages, page);
    }

    static void rankAdmin(MessageService messages, CommandSender sender, String label) {
        send(messages, sender, "Rank Administration", label, List.of(List.of(section("Player Ranks",
                entry("/" + label + " info <player>", "View a player's rank"),
                entry("/" + label + " set <player> <rank>", "Set an exact rank"),
                entry("/" + label + " promote <player> [amount]", "Promote one or more ranks"),
                entry("/" + label + " demote <player> [amount]", "Demote one or more ranks")))), 1);
    }

    static void prestigeAdmin(MessageService messages, CommandSender sender, String label) {
        send(messages, sender, "Prestige Administration", label, List.of(List.of(section("Player Prestiges",
                entry("/" + label + " info <player>", "View a player's prestige"),
                entry("/" + label + " set <player> <prestige|none>", "Set an exact prestige"),
                entry("/" + label + " promote <player> [amount]", "Promote one or more prestiges"),
                entry("/" + label + " demote <player> [amount]", "Demote one or more prestiges")))), 1);
    }

    static void gangAdmin(MessageService messages, CommandSender sender, int page) {
        List<List<MessageService.HelpSection>> pages = List.of(
                List.of(section("Discovery",
                        entry("/relicgang list [query]", "List or search gangs"),
                        entry("/relicgang inspect <name|tag>", "View detailed gang status"),
                        entry("/relicgang audit <gang>", "View recent gang audit entries"))),
                List.of(section("Members & Identity",
                        entry("/relicgang addmember <gang> <player>", "Force-add a member"),
                        entry("/relicgang removemember <gang> <player>", "Remove a member"),
                        entry("/relicgang setowner <gang> <player>", "Transfer ownership"),
                        entry("/relicgang rename <gang> <name>", "Rename a gang"),
                        entry("/relicgang tag <gang> <tag>", "Change a gang tag"),
                        entry("/relicgang disband <gang> confirm", "Permanently disband a gang"))),
                List.of(section("Progression & Operations",
                        entry("/relicgang <setlevel|addlevel> <gang> <amount>", "Adjust gang level"),
                        entry("/relicgang <setxp|addxp> <gang> <amount>", "Adjust gang XP"),
                        entry("/relicgang <setpoints|addpoints> <gang> <amount>", "Adjust gang points"),
                        entry("/relicgang adjustbank <gang> <signed-amount> [reason]", "Adjust gang funds"),
                        entry("/relicgang season <create|finalize|results> <id>", "Manage gang seasons"),
                        entry("/relicgang reload", "Reload gang configuration"))));
        send(messages, sender, "Gang Administration", "relicgang", pages, page);
    }

    static void mineAdmin(MessageService messages, CommandSender sender, int page) {
        List<List<MessageService.HelpSection>> pages = List.of(
                List.of(section("Selection & Creation",
                        entry("/relicmine wand", "Receive the mine selection wand"),
                        entry("/relicmine create <mine>", "Create a mine from your selection"),
                        entry("/relicmine resize <mine>", "Resize a mine"),
                        entry("/relicmine move <mine>", "Move a mine"),
                        entry("/relicmine copy <mine> <new-mine>", "Copy a mine"),
                        entry("/relicmine confirm", "Confirm a pending mine action"),
                        entry("/relicmine cancel", "Cancel a pending mine action"))),
                List.of(section("Mine Management",
                        entry("/relicmine list", "List configured mines"),
                        entry("/relicmine info <mine>", "View detailed mine status"),
                        entry("/relicmine tp <mine>", "Teleport to a mine"),
                        entry("/relicmine setspawn <mine>", "Set a mine spawn"),
                        entry("/relicmine <enable|disable> <mine>", "Change mine availability"),
                        entry("/relicmine rename <mine> <name>", "Rename a mine"),
                        entry("/relicmine sort <mine> <order>", "Set menu order"),
                        entry("/relicmine requirement <mine> ...", "Set access requirements"))),
                List.of(section("Blocks & Resets",
                        entry("/relicmine metadata <mine> <list|set|remove>", "Manage mine metadata"),
                        entry("/relicmine composition <mine> <list|set|remove|normalize|copy>", "Manage block weights"),
                        entry("/relicmine reset <mine>", "Queue a mine reset"),
                        entry("/relicmine recount <mine>", "Recount remaining blocks"),
                        entry("/relicmine resetconfig <mine> ...", "Edit reset settings"),
                        entry("/relicmine resethook <mine> ...", "Manage reset hooks"))),
                List.of(section("Recovery",
                        entry("/relicmine structure list", "List structure operations"),
                        entry("/relicmine structure info <operation-id>", "Inspect an operation"),
                        entry("/relicmine structure retry <operation-id>", "Retry a recoverable operation"),
                        entry("/relicmine structure rollback <operation-id>", "Roll back an operation"),
                        entry("/relicmine retryfailed <mine>", "Retry a failed mine reset"))));
        send(messages, sender, "Mine Administration", "relicmine", pages, page);
    }

    static void prisonAdmin(MessageService messages, CommandSender sender, int page, String label) {
        List<List<MessageService.HelpSection>> pages = List.of(
                List.of(section("System",
                        entry("/" + label + " status", "View runtime health"),
                        entry("/" + label + " admin", "Open the admin menu"),
                        entry("/" + label + " reload [module]", "Reload configuration safely"),
                        entry("/" + label + " validate", "Validate runtime configuration"),
                        entry("/" + label + " diagnose [full]", "View live diagnostics"),
                        entry("/" + label + " diagnostic", "Create a diagnostic export"))),
                List.of(section("Data & Recovery",
                        entry("/" + label + " backup <list|create|info|verify|delete|restore>", "Manage backups"),
                        entry("/" + label + " audit [filters]", "Search staff audit records"),
                        entry("/" + label + " repair <player|all>", "Repair progression state"),
                        entry("/" + label + " progression <list|info|retry>", "Manage progression transactions"),
                        entry("/" + label + " rewards <preview|finalize|history|pending|retry>", "Manage leaderboard rewards"))),
                List.of(section("Transfer",
                        entry("/" + label + " export <player>", "Export player data"),
                        entry("/" + label + " import <file.yml> confirm", "Import player data"),
                        entry("/relicmine help", "View mine administration commands"),
                        entry("/relicrank", "View rank administration commands"),
                        entry("/relicprestige", "View prestige administration commands"),
                        entry("/relicgang help", "View gang administration commands"))));
        send(messages, sender, "RelicPrison Administration", label, pages, page);
    }

    private static void send(MessageService messages, CommandSender sender, String title, String command,
                             List<List<MessageService.HelpSection>> pages, int requestedPage) {
        int page = Math.max(1, Math.min(pages.size(), requestedPage));
        messages.help(sender, title, pages.get(page - 1), page, pages.size(), command);
    }

    private static MessageService.HelpSection section(String title, MessageService.HelpEntry... entries) {
        return new MessageService.HelpSection(title, List.of(entries));
    }

    private static MessageService.HelpEntry entry(String syntax, String description) {
        return new MessageService.HelpEntry(syntax, description);
    }
}
