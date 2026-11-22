package org.nagineco.doppel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.nagineco.doppel.core.NpcLearningState;
import org.nagineco.doppel.core.NpcLearningStore;

/** Minecraft integration for the portable per-Nao learning snapshots. */
public final class NpcLearningCheckpoint {
    private NpcLearningCheckpoint() {}

    public static String fileName(int slot) {
        if (slot < 0 || slot >= 256) throw new IllegalArgumentException("Nao slot");
        return String.format(Locale.ROOT, "nao_%02d.learning.bin", slot + 1);
    }

    public static void save(Path memoryDirectory, List<HabitBrain> brains) throws IOException {
        if (brains.size() > 256) throw new IOException("Too many NPC learning states");
        for (int i = 0; i < brains.size(); i++) {
            NpcLearningState state = NpcLearningState.restore(brains.get(i).learningSnapshot());
            NpcLearningStore.save(memoryDirectory.resolve(fileName(i)), state);
        }
        // Remove obsolete slots so checkpoint manifests match the active NPC count.
        for (int i = brains.size(); i < 256; i++)
            Files.deleteIfExists(memoryDirectory.resolve(fileName(i)));
    }

    /** Prefer the checkpoint snapshot. Fall back to historical Markdown journals. */
    public static HabitBrain load(Path memoryDirectory, int slot,
                                  PersonalityMemoryStore memories) throws IOException {
        HabitBrain brain = new HabitBrain();
        Path snapshot = memoryDirectory.resolve(fileName(slot));
        if (Files.isRegularFile(snapshot)) {
            NpcLearningState state = NpcLearningStore.load(snapshot);
            try {
                brain.restoreLearning(state);
            } catch (IllegalArgumentException ex) {
                throw new IOException("Incompatible learning snapshot for Nao " + (slot + 1), ex);
            }
        } else {
            memories.restore(slot, brain);
            memories.restoreRewards(slot, brain);
        }
        return brain;
    }
}
