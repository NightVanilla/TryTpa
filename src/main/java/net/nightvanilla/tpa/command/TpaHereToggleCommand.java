package net.nightvanilla.tpa.command;

import net.nightvanilla.tpa.TryTpa;
import net.nightvanilla.tpa.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class TpaHereToggleCommand implements CommandExecutor {

    public TpaHereToggleCommand() {
        var cmd = Objects.requireNonNull(TryTpa.getInstance().getCommand("tpaheretoggle"));
        cmd.setExecutor(this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return false;

        boolean nowDisabled = !TryTpa.getInstance().getRequestStore().isTpaHereDisabled(player.getUniqueId());
        TryTpa.getInstance().getRequestStore().setTpaHereToggle(player.getUniqueId(), nowDisabled);

        if (nowDisabled) {
            player.sendMessage(MessageUtil.get("Messages.Toggle.TpaHereDisabled"));
        } else {
            player.sendMessage(MessageUtil.get("Messages.Toggle.TpaHereEnabled"));
        }

        return false;
    }

}
