package org.nagineco.doppel.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PhysicalSkillLearningEngineTest {
    @TempDir Path temp;

    @Test void fullModeAutomaticallySharesAllPhysicalSkillsAcross256Naos() {
        var engine = new PhysicalSkillLearningEngine(256, PhysicalSkillSync.Mode.FULL, 1);
        engine.practice(0, 60, 1, 1);
        assertEquals(engine.state(0), engine.state(255));
        assertEquals(1, engine.state(255).technique());
        assertEquals(0.5, engine.state(255).strength());
    }

    @Test void techniqueModeDoesNotSharePhysicalCapacity() {
        var engine = new PhysicalSkillLearningEngine(2, PhysicalSkillSync.Mode.TECHNIQUE, 1);
        engine.practice(0, 60, 1, 1);
        assertEquals(1, engine.state(1).technique());
        assertEquals(0, engine.state(1).strength());
    }

    @Test void individualModeNeverShares() {
        var engine = new PhysicalSkillLearningEngine(2, PhysicalSkillSync.Mode.INDIVIDUAL, 1);
        engine.practice(0, 60, 1, 1);
        assertEquals(0, engine.state(1).technique());
    }

    @Test void noPracticeDoesNotCreateSkills() {
        var engine = new PhysicalSkillLearningEngine(2, PhysicalSkillSync.Mode.FULL, 1);
        engine.practice(0, 0, 1, 1);
        assertEquals(0, engine.state(1).technique());
    }

    @Test void savedSkillsRestoreWithoutDuplicatingSharedLearning() throws Exception {
        var original = new PhysicalSkillLearningEngine(256, PhysicalSkillSync.Mode.FULL, 1);
        original.practice(0, 120, 0.8, 0.5);
        Path file = temp.resolve("physical.bin");
        PhysicalSkillStore.save(file, original.snapshot());
        var resumed = new PhysicalSkillLearningEngine(256, PhysicalSkillSync.Mode.FULL, 1);
        resumed.restore(PhysicalSkillStore.load(file));
        assertEquals(original.snapshot(), resumed.snapshot());
    }

    @Test void truncatedSnapshotIsRejected() throws Exception {
        Path file = temp.resolve("physical.bin");
        PhysicalSkillStore.save(file, List.of(new PhysicalSkillSync.Skill(1, 2, 3, 4, 5, 6)));
        byte[] bytes = Files.readAllBytes(file);
        Files.write(file, java.util.Arrays.copyOf(bytes, bytes.length - 2));
        assertThrows(IOException.class, () -> PhysicalSkillStore.load(file));
    }

    @Test void invalidPracticeIsRejected() {
        var engine = new PhysicalSkillLearningEngine(2, PhysicalSkillSync.Mode.FULL, 1);
        assertThrows(IllegalArgumentException.class, () -> engine.practice(0, -1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> engine.practice(0, 1, Double.NaN, 1));
    }
}
