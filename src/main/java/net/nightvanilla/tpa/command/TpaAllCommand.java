package net.nightvanilla.tpa.command;

import net.nightvanilla.tpa.TryTpa;
import net.nightvanilla.tpa.request.RequestStore;
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
import java.util.UUID;

public final class TpaAllCommand implements CommandExecutor, TabCompleter {

    public TpaAllCommand() {
        CommandUtil.register("tpaall", this, this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return false;

        TpaService service = TryTpa.getInstance().getTpaService();

        // /tpaall accept <requester> — no tpaall perm required to accept
        if (args.length == 2 && args[0].equalsIgnoreCase("accept")) {
            service.acceptTpaAll(player, args[1]);
            return false;
        }

        if (!CommandUtil.checkPermission(player, "trytpa.command.tpaall")) return false;

        if (args.length == 0) {
            service.broadcastTpaAll(player);
            return false;
        }

        CommandUtil.syntax(player, "tpaall");
        return false;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) return CommandUtil.filterPrefix(List.of("accept"), args);

        if (args.length == 2 && args[0].equalsIgnoreCase("accept")) {
            RequestStore store = TryTpa.getInstance().getRequestStore();
            List<String> names = new ArrayList<>();
            for (UUID uuid : store.getTpaAllRequesters()) {
                String name = store.resolvePlayerName(uuid);
                if (name != null) names.add(name);
            }
            return CommandUtil.filterPrefix(names, args);
        }
        return List.of();
    }
}
