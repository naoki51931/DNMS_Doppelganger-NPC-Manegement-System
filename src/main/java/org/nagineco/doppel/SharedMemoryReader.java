package org.nagineco.doppel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Reads MIP's derived index as shared factual knowledge.
 * Never re-trains HabitBrain from this index: raw journals already supply action counts.
 */
public final class SharedMemoryReader {
    private final Path path;
    private long modified = Long.MIN_VALUE;
    private final EnumMap<NaoLifeStore.Goal, Integer> knowledge = new EnumMap<>(NaoLifeStore.Goal.class);
    private int entries;
    public SharedMemoryReader(Path path) { this.path = path; }
    public void refresh() throws IOException {
        if (!Files.isRegularFile(path)) { knowledge.clear(); entries = 0; modified = Long.MIN_VALUE; return; }
        long timestamp = Files.getLastModifiedTime(path).toMillis();
        if (timestamp == modified) return;
        EnumMap<NaoLifeStore.Goal, Integer> next = new EnumMap<>(NaoLifeStore.Goal.class);
        int total = 0;
        boolean inEvents = false;
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.equals("## Integrated events")) { inEvents = true; continue; }
            if (inEvents && line.startsWith("## ")) break;
            if (!inEvents || !line.startsWith("- nao=")) continue;
            int at = line.indexOf(" reason=");
            if (at < 0) continue;
            try {
                HabitBrain.Reason reason = HabitBrain.Reason.valueOf(line.substring(at + 8).trim());
                NaoLifeStore.Goal goal = switch (reason) {
                    case EXPLORE -> NaoLifeStore.Goal.EXPLORE;
                    case COLLECT -> NaoLifeStore.Goal.COLLECT;
                    case BUILD -> NaoLifeStore.Goal.BUILD;
                    case SOCIAL -> NaoLifeStore.Goal.SOCIAL;
                    case SAFETY -> NaoLifeStore.Goal.SAFETY;
                    case NONE -> NaoLifeStore.Goal.REST;
                };
                next.merge(goal, 1, Integer::sum);
                total++;
            } catch (IllegalArgumentException ignored) { }
        }
        knowledge.clear();
        knowledge.putAll(next);
        entries = total;
        modified = timestamp;
    }
    public int evidence(NaoLifeStore.Goal goal) { return knowledge.getOrDefault(goal, 0); }
    public int entries() { return entries; }
}
