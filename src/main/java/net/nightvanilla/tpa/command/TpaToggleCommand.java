package net.nightvanilla.tpa.command;

import net.nightvanilla.tpa.TryTpa;
import net.nightvanilla.tpa.request.RequestStore;
import net.nightvanilla.tpa.request.RequestType;
import net.nightvanilla.tpa.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class TpaToggleCommand implements CommandExecutor {

    private final RequestType type;
    private final String commandName;
    private final String enabledMessageKey;
    private final String disabledMessageKey;

    private TpaToggleCommand(RequestType type, String commandName, String enabledKey, String disabledKey) {
        this.type = type;
        this.commandName = commandName;
        this.enabledMessageKey = enabledKey;
        this.disabledMessageKey = disabledKey;
    }

    public static void registerAll() {
        new TpaToggleCommand(RequestType.TPA,      "tpatoggle",      "Messages.Toggle.TpaEnabled",     "Messages.Toggle.TpaDisabled").register();
        new TpaToggleCommand(RequestType.TPA_HERE, "tpaheretoggle",  "Messages.Toggle.TpaHereEnabled", "Messages.Toggle.TpaHereDisabled").register();
        new TpaToggleCommand(RequestType.TPA_ALL,  "tpaalltoggle",   "Messages.Toggle.TpaAllEnabled",  "Messages.Toggle.TpaAllDisabled").register();
    }

    private void register() {
        CommandUtil.register(commandName, this, null);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return false;

        RequestStore store = TryTpa.getInstance().getRequestStore();
        boolean nowDisabled = !store.isDisabled(type, player.getUniqueId());
        store.setToggle(type, player.getUniqueId(), nowDisabled);
        player.sendMessage(MessageUtil.get(nowDisabled ? disabledMessageKey : enabledMessageKey));
        return false;
    }
}
