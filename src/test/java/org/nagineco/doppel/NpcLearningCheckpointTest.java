package org.nagineco.doppel;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nagineco.doppel.core.NpcLearningState;
import org.nagineco.doppel.core.NpcLearningStore;
import static org.junit.jupiter.api.Assertions.*;

class NpcLearningCheckpointTest {
    @TempDir Path temp;

    @Test void snapshotTakesPrecedenceOverJournalReplay() throws Exception {
        PersonalityMemoryStore memories = new PersonalityMemoryStore(temp);
        HabitBrain original = new HabitBrain();
        original.learnMemory(HabitBrain.Move.EAST, HabitBrain.Emotion.HAPPY,
            HabitBrain.Reason.EXPLORE, 100);
        NpcLearningCheckpoint.save(temp, java.util.List.of(original));
        memories.remember(0, 1, 100, new PersonalityMemoryStore.VecRecord(
            0, 0, 0, "EAST", HabitBrain.Emotion.HAPPY, HabitBrain.Reason.EXPLORE));
        memories.flush();
        HabitBrain restored = NpcLearningCheckpoint.load(temp, 0, memories);
        assertEquals(1, restored.samples(), "Do not replay the same journal over a snapshot");
    }

    @Test void legacyJournalFallbackStillWorks() throws Exception {
        PersonalityMemoryStore memories = new PersonalityMemoryStore(temp);
        memories.remember(0, 1, 100, new PersonalityMemoryStore.VecRecord(
            0, 0, 0, "WEST", HabitBrain.Emotion.NEUTRAL, HabitBrain.Reason.NONE));
        memories.flush();
        HabitBrain restored = NpcLearningCheckpoint.load(temp, 0, memories);
        assertEquals(1, restored.samples());
    }

    @Test void corruptSnapshotFailsRatherThanDoubleCountingJournal() throws Exception {
        PersonalityMemoryStore memories = new PersonalityMemoryStore(temp);
        java.nio.file.Files.write(temp.resolve(NpcLearningCheckpoint.fileName(0)), new byte[]{1, 2});
        assertThrows(java.io.IOException.class, () -> NpcLearningCheckpoint.load(temp, 0, memories));
    }

    @Test void filenameSupportsAll256Slots() {
        assertEquals("nao_256.learning.bin", NpcLearningCheckpoint.fileName(255));
        assertThrows(IllegalArgumentException.class, () -> NpcLearningCheckpoint.fileName(256));
    }
}
