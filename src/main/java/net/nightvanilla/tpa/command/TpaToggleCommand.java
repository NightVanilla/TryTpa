package net.nightvanilla.tpa.command;

import net.nightvanilla.tpa.TryTpa;
import net.nightvanilla.tpa.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class TpaToggleCommand implements CommandExecutor {

    public TpaToggleCommand() {
        var cmd = Objects.requireNonNull(TryTpa.getInstance().getCommand("tpatoggle"));
        cmd.setExecutor(this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return false;

        boolean nowDisabled = !TryTpa.getInstance().getRequestStore().isTpaDisabled(player.getUniqueId());
        TryTpa.getInstance().getRequestStore().setTpaToggle(player.getUniqueId(), nowDisabled);

        if (nowDisabled) {
            player.sendMessage(MessageUtil.get("Messages.Toggle.TpaDisabled"));
        } else {
            player.sendMessage(MessageUtil.get("Messages.Toggle.TpaEnabled"));
        }

        return false;
    }

}
