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

import java.util.List;

public final class TpaAcceptCommand implements CommandExecutor, TabCompleter {

    public TpaAcceptCommand() {
        CommandUtil.register("tpaccept", this, this);
        CommandUtil.register("tpaaccept", this, this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return false;
        if (!CommandUtil.checkPermission(player, "trytpa.command.tpa")) return false;

        TpaService service = TryTpa.getInstance().getTpaService();

        if (args.length == 0) {
            service.acceptAny(RequestType.TPA, player);
            return false;
        }
        if (args.length == 1) {
            if (args[0].equals("*")) service.acceptAll(RequestType.TPA, player);
            else service.accept(RequestType.TPA, player, args[0]);
            return false;
        }

        CommandUtil.syntax(player, "tpaccept <player / *>");
        return false;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) return List.of();
        List<String> options = CommandUtil.requesterNames(RequestType.TPA, player);
        options.add("*");
        return CommandUtil.filterPrefix(options, args);
    }
}
