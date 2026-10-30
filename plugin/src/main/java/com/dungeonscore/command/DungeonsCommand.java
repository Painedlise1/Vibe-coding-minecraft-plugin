package com.dungeonscore.command;

import com.dungeonscore.ability.ManaManager;
import com.dungeonscore.boss.BossManager;
import com.dungeonscore.boss.BossType;
import com.dungeonscore.dungeon.DungeonManager;
import com.dungeonscore.item.Category;
import com.dungeonscore.item.DungeonItem;
import com.dungeonscore.item.ItemRegistry;
import com.dungeonscore.pack.PackService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DungeonsCommand implements TabExecutor {

    private static final String ADMIN = "dungeons.admin";
    private static final String PLAY = "dungeons.play";

    private final ItemRegistry items;
    private final PackService pack;
    private final BossManager bosses;
    private final ManaManager mana;
    private final DungeonManager dungeon;

    public DungeonsCommand(ItemRegistry items, PackService pack, BossManager bosses, ManaManager mana,
                           DungeonManager dungeon) {
        this.items = items;
        this.pack = pack;
        this.bosses = bosses;
        this.mana = mana;
        this.dungeon = dungeon;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "give" -> give(sender, label, args);
            case "list" -> list(sender, label);
            case "pack" -> pack(sender, args);
            case "boss" -> boss(sender, label, args);
            case "mana" -> mana(sender, label, args);
            case "dungeon" -> dungeon(sender, label, args);
            default -> help(sender, label);
        }
        return true;
    }

    private void help(CommandSender sender, String label) {
        info(sender, "DungeonsCore commands:");
        info(sender, "/" + label + " dungeon <join|leave|status> - the Overgrown City dungeon");
        if (sender.hasPermission(ADMIN)) {
            info(sender, "/" + label + " give <player> <item> [amount]");
            info(sender, "/" + label + " list");
            info(sender, "/" + label + " boss <" + String.join("|", bossIds()) + "> - spawn a boss where you stand");
            info(sender, "/" + label + " boss clear - remove all bosses and their minions");
            info(sender, "/" + label + " mana [player] - refill mana");
            info(sender, "/" + label + " dungeon <reset|open|rebuild> - end the run / open the grove / regenerate");
        }
        info(sender, "/" + label + " pack" + (sender.hasPermission(ADMIN) ? " [player]" : "")
                + " - (re)send the resource pack");
    }

    private void give(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            error(sender, "You don't have permission to do that.");
            return;
        }
        if (args.length < 3) {
            error(sender, "Usage: /" + label + " give <player> <item> [amount]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            error(sender, "Player " + args[1] + " is not online.");
            return;
        }
        DungeonItem def = items.get(args[2].toLowerCase(Locale.ROOT));
        if (def == null) {
            error(sender, "Unknown item " + args[2] + ". Use /" + label + " list.");
            return;
        }
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                error(sender, args[3] + " is not a number.");
                return;
            }
            if (amount < 1 || amount > 2304) {
                error(sender, "Amount must be between 1 and 2304.");
                return;
            }
        }

        int maxStack = items.create(def, 1).getMaxStackSize();
        int left = amount;
        while (left > 0) {
            int n = Math.min(left, maxStack);
            left -= n;
            Map<Integer, ItemStack> overflow = target.getInventory().addItem(items.create(def, n));
            overflow.values().forEach(s -> target.getWorld().dropItemNaturally(target.getLocation(), s));
        }
        sender.sendMessage(Component.text("Gave " + amount + "x ", NamedTextColor.GREEN)
                .append(Component.text(def.name(), def.rarity().color()))
                .append(Component.text(" to " + target.getName() + ".", NamedTextColor.GREEN)));
    }

    private void list(CommandSender sender, String label) {
        if (!sender.hasPermission(ADMIN)) {
            error(sender, "You don't have permission to do that.");
            return;
        }
        String self = sender instanceof Player p ? p.getName() : "<player>";
        for (Category category : Category.values()) {
            List<DungeonItem> inCategory = items.all().stream()
                    .filter(d -> d.category() == category).toList();
            if (inCategory.isEmpty()) {
                continue;
            }
            Component line = Component.text(category.label() + ": ", NamedTextColor.GOLD);
            for (int i = 0; i < inCategory.size(); i++) {
                DungeonItem d = inCategory.get(i);
                if (i > 0) {
                    line = line.append(Component.text(", ", NamedTextColor.DARK_GRAY));
                }
                line = line.append(Component.text(d.id(), d.rarity().color())
                        .hoverEvent(HoverEvent.showText(Component.text(d.name(), d.rarity().color())
                                .append(Component.newline())
                                .append(Component.text("Click to give", NamedTextColor.GRAY))))
                        .clickEvent(ClickEvent.suggestCommand("/" + label + " give " + self + " " + d.id())));
            }
            sender.sendMessage(line);
        }
    }

    private void pack(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            if (!sender.hasPermission(ADMIN)) {
                error(sender, "You can only resend the pack to yourself.");
                return;
            }
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                error(sender, "Player " + args[1] + " is not online.");
                return;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            error(sender, "Usage from console: /dungeons pack <player>");
            return;
        }
        if (pack.send(target)) {
            info(sender, "Sent the resource pack to " + target.getName() + ".");
        } else {
            error(sender, "The resource pack is not available - check the server console.");
        }
    }

    private void boss(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            error(sender, "You don't have permission to do that.");
            return;
        }
        if (args.length < 2) {
            error(sender, "Usage: /" + label + " boss <" + String.join("|", bossIds()) + "|clear>");
            return;
        }
        if (args[1].equalsIgnoreCase("clear")) {
            info(sender, "Removed " + bosses.removeAll() + " bosses/minions.");
            return;
        }
        BossType type = BossType.byId(args[1]);
        if (type == null) {
            error(sender, "Unknown boss " + args[1] + ". Choose: " + String.join(", ", bossIds()));
            return;
        }
        if (!(sender instanceof Player player)) {
            error(sender, "Only players can spawn bosses (they appear where you stand).");
            return;
        }
        bosses.spawn(type, player.getLocation());
        info(sender, "Spawned the " + type.displayName() + ". Switch to survival to fight it.");
    }

    private void mana(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            error(sender, "You don't have permission to do that.");
            return;
        }
        Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1])
                : sender instanceof Player p ? p : null;
        if (target == null) {
            error(sender, "Usage: /" + label + " mana <player>");
            return;
        }
        mana.set(target, ManaManager.MAX_MANA);
        mana.showBar(target);
        info(sender, "Refilled " + target.getName() + "'s mana.");
    }

    private void dungeon(CommandSender sender, String label, String[] args) {
        String action = args.length < 2 ? "status" : args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "join", "leave" -> {
                if (!(sender instanceof Player player)) {
                    error(sender, "Only players can do that.");
                    return;
                }
                if (!player.hasPermission(PLAY)) {
                    error(sender, "You don't have permission to do that.");
                    return;
                }
                String problem = action.equals("join") ? dungeon.join(player) : dungeon.leave(player);
                if (problem != null) {
                    error(sender, problem);
                }
            }
            case "status" -> info(sender, dungeon.status());
            case "reset", "open", "rebuild" -> {
                if (!sender.hasPermission(ADMIN)) {
                    error(sender, "You don't have permission to do that.");
                    return;
                }
                switch (action) {
                    case "reset" -> {
                        dungeon.reset();
                        info(sender, "Dungeon run ended and reset.");
                    }
                    case "open" -> {
                        if (dungeon.forceOpen()) {
                            info(sender, "Opened the Heart of the Grove.");
                        } else {
                            error(sender, "No run is in the clearing phase (" + dungeon.state() + ").");
                        }
                    }
                    default -> {
                        info(sender, "Regenerating the dungeon world - this may lag briefly...");
                        if (dungeon.rebuild()) {
                            info(sender, "Dungeon world rebuilt.");
                        } else {
                            error(sender, "Rebuild failed - check the console.");
                        }
                    }
                }
            }
            default -> error(sender, "Usage: /" + label + " dungeon <join|leave|status>"
                    + (sender.hasPermission(ADMIN) ? " or <reset|open|rebuild>" : ""));
        }
    }

    private static List<String> bossIds() {
        return Arrays.stream(BossType.values()).map(BossType::id).toList();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = sender.hasPermission(ADMIN);
        if (args.length == 1) {
            return filter(admin ? List.of("give", "list", "pack", "boss", "mana", "dungeon")
                    : List.of("pack", "dungeon"), args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && sub.equals("dungeon")) {
            return filter(admin ? List.of("join", "leave", "status", "reset", "open", "rebuild")
                    : List.of("join", "leave", "status"), args[1]);
        }
        if (args.length == 2 && admin && sub.equals("boss")) {
            List<String> options = new ArrayList<>(bossIds());
            options.add("clear");
            return filter(options, args[1]);
        }
        if (args.length == 2 && admin && (sub.equals("give") || sub.equals("pack") || sub.equals("mana"))) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 3 && admin && sub.equals("give")) {
            return filter(new ArrayList<>(items.ids()), args[2]);
        }
        if (args.length == 4 && admin && sub.equals("give")) {
            return filter(List.of("1", "16", "64"), args[3]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).toList();
    }

    private static void info(CommandSender sender, String msg) {
        sender.sendMessage(Component.text(msg, NamedTextColor.YELLOW));
    }

    private static void error(CommandSender sender, String msg) {
        sender.sendMessage(Component.text(msg, NamedTextColor.RED));
    }
}
