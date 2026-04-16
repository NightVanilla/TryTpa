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

public final class TpaHereAcceptCommand implements CommandExecutor, TabCompleter {

    public TpaHereAcceptCommand() {
        CommandUtil.register("tpahereaccept", this, this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return false;
        if (!CommandUtil.checkPermission(player, "trytpa.command.tpahere")) return false;

        TpaService service = TryTpa.getInstance().getTpaService();

        if (args.length == 0) {
            service.acceptAny(RequestType.TPA_HERE, player);
            return false;
        }
        if (args.length == 1) {
            service.accept(RequestType.TPA_HERE, player, args[0]);
            return false;
        }

        CommandUtil.syntax(player, "tpahereaccept <player>");
        return false;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) return List.of();
        return CommandUtil.filterPrefix(CommandUtil.requesterNames(RequestType.TPA_HERE, player), args);
    }
}
