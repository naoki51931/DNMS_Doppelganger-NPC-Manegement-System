package org.nagineco.patch.naogrowth;

/** One-at-a-time daily Nao generation clock; no doubling. */
public final class NaoGrowthPatch {
    private NaoGrowthPatch() {}
    public static final int DAY_TICKS = 24000;
    public static boolean crossed(long previous, long current, int scheduledTick) {
        if (previous < 0 || current <= previous || current - previous > 20) return false;
        long target = Math.floorDiv(current, DAY_TICKS) * DAY_TICKS + scheduledTick;
        return previous < target && current >= target;
    }
    public static int parseClock(String clock) {
        if (!clock.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]"))
            throw new IllegalArgumentException("Use HH:mm (00:00-23:59)");
        int hour = Integer.parseInt(clock.substring(0,2));
        int minute = Integer.parseInt(clock.substring(3,5));
        return (hour * 1000 + minute * 1000 / 60 + 18000) % DAY_TICKS;
    }
    public static String formatClock(int tick) {
        int minutes = ((tick + 6000) % DAY_TICKS) * 1440 / DAY_TICKS;
        return String.format(java.util.Locale.ROOT, "%02d:%02d", minutes/60, minutes%60);
    }
}
