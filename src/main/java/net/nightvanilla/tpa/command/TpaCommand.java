package net.nightvanilla.tpa.command;

import net.nightvanilla.tpa.TryTpa;
import net.nightvanilla.tpa.request.RequestType;
import net.nightvanilla.tpa.service.TpaService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class TpaCommand implements CommandExecutor, TabCompleter {

    public TpaCommand() {
        CommandUtil.register("tpa", this, this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return false;
        if (!CommandUtil.checkPermission(player, "trytpa.command.tpa")) return false;

        TpaService service = TryTpa.getInstance().getTpaService();

        if (args.length == 1 && args[0].equalsIgnoreCase("accept")) {
            service.acceptAny(RequestType.TPA, player);
            return false;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("accept")) {
            if (args[1].equals("*")) service.acceptAll(RequestType.TPA, player);
            else service.accept(RequestType.TPA, player, args[1]);
            return false;
        }
        if (args.length == 1) {
            service.sendRequest(RequestType.TPA, player, args[0]);
            return false;
        }

        CommandUtil.syntax(player, "tpa <player>");
        CommandUtil.syntax(player, "tpa accept <player / *>");
        return false;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return List.of();

        if (args.length == 1) {
            List<String> options = new ArrayList<>(TryTpa.getInstance().getRequestStore().getOnlinePlayerNames());
            options.add("accept");
            return CommandUtil.filterPrefix(options, args);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("accept")) {
            List<String> options = CommandUtil.requesterNames(RequestType.TPA, player);
            options.add("*");
            return CommandUtil.filterPrefix(options, args);
        }
        return List.of();
    }
}
