package org.nagineco.doppel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import net.minecraft.util.math.Vec3d;

/**
 * Shared, read-only interpretation of the MIP index. All Nao see the same events;
 * the original personal journals remain the source of truth for movement learning.
 */
public final class SharedMemoryReader {
    private static final Pattern EVENT = Pattern.compile(
        "^- nao=(\\d{2,3}) day=(\\d+) tick=(\\d+) position=\\(([^)]*)\\) action=(STAY|NORTH|SOUTH|WEST|EAST) emotion=([A-Z_]+) reason=([A-Z_]+)$");
    public record Experience(int nao, int day, int tick, Vec3d position,
                             HabitBrain.Move action, HabitBrain.Reason reason) {}
    private final Path path;
    private long modified = Long.MIN_VALUE;
    private final EnumMap<NaoLifeStore.Goal, Integer> knowledge = new EnumMap<>(NaoLifeStore.Goal.class);
    private List<Experience> experiences = List.of();
    private double[] packed = new double[0];
    public SharedMemoryReader(Path path) { this.path = path; }
    public void refresh() throws IOException {
        if (!Files.isRegularFile(path)) {
            knowledge.clear(); experiences = List.of(); packed = new double[0]; modified = Long.MIN_VALUE; return;
        }
        long timestamp = Files.getLastModifiedTime(path).toMillis();
        if (timestamp == modified) return;
        EnumMap<NaoLifeStore.Goal, Integer> next = new EnumMap<>(NaoLifeStore.Goal.class);
        List<Experience> events = new ArrayList<>();
        boolean inEvents = false;
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.equals("## Integrated events")) { inEvents = true; continue; }
            if (inEvents && line.startsWith("## ")) break;
            if (!inEvents || !line.startsWith("- nao=")) continue;
            Matcher m = EVENT.matcher(line);
            if (!m.matches()) continue;
            try {
                int nao = Integer.parseInt(m.group(1));
                int day = Integer.parseInt(m.group(2));
                int tick = Integer.parseInt(m.group(3));
                String[] coords = m.group(4).split(",", -1);
                if (nao < 1 || nao > 256 || day < 1 || tick < 0 || tick >= 24000 || coords.length != 3) continue;
                double x = Double.parseDouble(coords[0].trim());
                double y = Double.parseDouble(coords[1].trim());
                double z = Double.parseDouble(coords[2].trim());
                if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) continue;
                HabitBrain.Move action = HabitBrain.Move.valueOf(m.group(5));
                HabitBrain.Reason reason = HabitBrain.Reason.valueOf(m.group(7));
                Experience e = new Experience(nao, day, tick, new Vec3d(x,y,z), action, reason);
                events.add(e);
                next.merge(goal(reason), 1, Integer::sum);
            } catch (RuntimeException ignored) { }
        }
        knowledge.clear(); knowledge.putAll(next);
        experiences = List.copyOf(events);
        packed = new double[events.size() * 5];
        for (int i = 0; i < events.size(); i++) {
            Experience e = events.get(i);
            int offset = i * 5;
            packed[offset] = e.position().x;
            packed[offset + 1] = e.position().y;
            packed[offset + 2] = e.position().z;
            packed[offset + 3] = goal(e.reason()).ordinal();
            packed[offset + 4] = e.action() == HabitBrain.Move.STAY ? 0 : 1;
        }
        modified = timestamp;
    }
    private static NaoLifeStore.Goal goal(HabitBrain.Reason reason) {
        return switch (reason) {
            case EXPLORE -> NaoLifeStore.Goal.EXPLORE;
            case COLLECT -> NaoLifeStore.Goal.COLLECT;
            case BUILD -> NaoLifeStore.Goal.BUILD;
            case SOCIAL -> NaoLifeStore.Goal.SOCIAL;
            case SAFETY -> NaoLifeStore.Goal.SAFETY;
            case NONE -> NaoLifeStore.Goal.REST;
        };
    }
    public int evidence(NaoLifeStore.Goal goal) { return knowledge.getOrDefault(goal, 0); }
    public int entries() { return experiences.size(); }
    /** Nearest matching experience from ANY Nao, bounded to the current area. */
    public Optional<Experience> nearest(NaoLifeStore.Goal goal, Vec3d current, double radius) {
        if (NativeMemorySearch.available() && experiences.size() >= 128) {
            int index = NativeMemorySearch.nearestIndex(packed, experiences.size(), goal.ordinal(),
                current.x, current.y, current.z, radius * radius);
            return index < 0 ? Optional.empty() : Optional.of(experiences.get(index));
        }
        int index = org.nagineco.doppel.core.MemoryNearestPolicy.nearestIndex(
            packed, experiences.size(), goal.ordinal(),
            current.x, current.y, current.z, radius * radius);
        return index < 0 ? Optional.empty() : Optional.of(experiences.get(index));
    }
}
