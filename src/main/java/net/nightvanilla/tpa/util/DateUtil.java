package net.nightvanilla.tpa.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class DateUtil {

    public static String secondsToTime(long seconds) {
        if (seconds <= 0) {
            return "0 seconds";
        }

        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        seconds %= 60;

        if (days > 0) return days + " day" + (days == 1 ? " " : "s ") + hours + " hour" + (hours == 1 ? " " : "s ");
        if (hours > 0) return hours + " hour" + (hours == 1 ? " " : "s ") + minutes + " minute" + (hours == 1 ? " " : "s ");
        if (minutes > 0) return minutes + " minute" + (minutes == 1 ? " " : "s ") + seconds + " second" + (seconds == 1 ? " " : "s ");
        return seconds + " second" + (seconds == 1 ? " " : "s ");
    }

}
