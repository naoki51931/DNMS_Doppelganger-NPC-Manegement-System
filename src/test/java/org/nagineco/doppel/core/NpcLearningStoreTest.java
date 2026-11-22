package org.nagineco.doppel.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class NpcLearningStoreTest {
    @TempDir Path temp;

    @Test void roundTripPreservesLearningAndContext() throws IOException {
        NpcLearningState original = new NpcLearningState(2, 2, 5);
        original.observe(0, 1, 1, 3);
        original.observe(0, 1, 1, 3);
        original.reward(0, 1, 1, 3, 12);
        original.setEmotion(1);
        original.setMotivation(1);
        Path file = temp.resolve("nested/learning.bin");
        NpcLearningStore.save(file, original);
        NpcLearningState loaded = NpcLearningStore.load(file);
        assertEquals(2, loaded.samples());
        assertEquals(1, loaded.emotion());
        assertEquals(1, loaded.motivation());
        assertEquals(3, loaded.decide(0));
        double[] scores = new double[5];
        loaded.accumulateScores(0, new double[]{0, 5}, true, scores);
        assertEquals(14, scores[3]);
    }

    @Test void snapshotIsDeepCopy() {
        NpcLearningState state = new NpcLearningState(1, 1, 5);
        state.observe(0, 0, 0, 2);
        NpcLearningState.Snapshot saved = state.snapshot();
        saved.counts()[0][0][0][2] = 99;
        assertEquals(1, state.distribution(0, 0, 0)[2]);
    }

    @Test void rejectsTruncatedFile() throws IOException {
        Path file = temp.resolve("broken.bin");
        Files.write(file, new byte[]{1, 2, 3});
        assertThrows(IOException.class, () -> NpcLearningStore.load(file));
    }

    @Test void rejectsInconsistentCounts() {
        NpcLearningState state = new NpcLearningState(1, 1, 5);
        var snapshot = state.snapshot();
        snapshot.counts()[0][0][0][1] = 1;
        assertThrows(IllegalArgumentException.class, () -> NpcLearningState.restore(snapshot));
    }
}
