package org.nagineco.doppel.core;

import java.util.Locale;

/** Pure conversion between Minecraft day ticks and a wall-clock-style HH:mm. */
public final class MinecraftClock {
    public static final int DAY_TICKS = 24000;
    private MinecraftClock() {}

    public static int parse(String clock) {
        if (clock == null || !clock.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]"))
            throw new IllegalArgumentException("Expected HH:mm from 00:00 to 23:59.");
        int hour = Integer.parseInt(clock.substring(0, 2));
        int minute = Integer.parseInt(clock.substring(3, 5));
        return ((hour * 1000 + minute * 1000 / 60) + 18000) % DAY_TICKS;
    }

    public static String format(int tick) {
        int normalized = Math.floorMod(tick, DAY_TICKS);
        int minutes = ((normalized + 6000) % DAY_TICKS) * 1440 / DAY_TICKS;
        return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }
}
