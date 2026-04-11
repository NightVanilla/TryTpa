package net.nightvanilla.tpa.util;

import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.nightvanilla.tpa.TryTpa;

@UtilityClass
public class MessageUtil {

    public static String get(String key) {
        return ColorUtil.color(TryTpa.getInstance().getConfig().getString(key, ""))
                .replace("%prefix%", getPrefix());
    }

    public static String getPrefix() {
        return ColorUtil.color(TryTpa.getInstance().getConfig().getString("Messages.Prefix", ""));
    }

    public static Component getRequest(String type, String player) {
        String message = TryTpa.getInstance().getConfig().getString("Messages.Request." + type + ".Message", "");
        String click = TryTpa.getInstance().getConfig().getString("Messages.Request." + type + ".Click", "");
        String clickHover = TryTpa.getInstance().getConfig().getString("Messages.Request." + type + ".ClickHover", null);

        return ColorUtil.colorComponent(message.replace("%player%", player)).replaceText((b) -> b.matchLiteral("%click%").replacement(
                ColorUtil.colorComponent(click)
                        .clickEvent(ClickEvent.runCommand("/" + type.toLowerCase() + " accept " + player))
                        .hoverEvent((clickHover == null || clickHover.isEmpty() ? null : ColorUtil.colorComponent(clickHover)))
        ));
    }

}
