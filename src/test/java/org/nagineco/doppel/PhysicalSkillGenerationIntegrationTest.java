package org.nagineco.doppel;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nagineco.doppel.core.*;
import static org.junit.jupiter.api.Assertions.*;

class PhysicalSkillGenerationIntegrationTest {
    @TempDir Path temp;

    private Path publishWithSkills(int count) throws Exception {
        Path memory = temp.resolve("memory");
        new PersonalityMemoryStore(memory);
        var brains = new java.util.ArrayList<HabitBrain>();
        for (int i = 0; i < count; i++) brains.add(new HabitBrain());
        NpcLearningCheckpoint.save(memory, brains);
        var engine = new PhysicalSkillLearningEngine(count, PhysicalSkillSync.Mode.FULL, 1);
        engine.practice(0, 60, 1, 1);
        Path physical = temp.resolve("physical.bin");
        PhysicalSkillStore.save(physical, engine.snapshot());
        Path recording = temp.resolve("recordings.json");
        Files.writeString(recording, "{}");
        Path root = temp.resolve("generations");
        GenerationCheckpointStore.publish(root, recording, memory, null, null, null, null, physical);
        return root;
    }

    @Test void fullSkillsSurviveGenerationFor256Naos() throws Exception {
        Path root = publishWithSkills(256);
        Path generation = GenerationCheckpointStore.latest(root);
        var restored = PhysicalSkillStore.load(generation.resolve("physical-skills.bin"));
        assertEquals(256, restored.size());
        assertEquals(restored.get(0), restored.get(255));
        assertEquals(1, restored.get(255).technique());
    }

    @Test void corruptPhysicalSkillsInvalidateGeneration() throws Exception {
        Path root = publishWithSkills(2);
        Path physical = GenerationCheckpointStore.latest(root).resolve("physical-skills.bin");
        byte[] bytes = Files.readAllBytes(physical);
        Files.write(physical, java.util.Arrays.copyOf(bytes, bytes.length - 3));
        assertThrows(IOException.class, () -> GenerationCheckpointStore.latest(root));
    }

    @Test void mismatchedPhysicalSkillCountRejectsPublish() throws Exception {
        Path memory = temp.resolve("memory");
        new PersonalityMemoryStore(memory);
        NpcLearningCheckpoint.save(memory, List.of(new HabitBrain(), new HabitBrain()));
        Path physical = temp.resolve("physical.bin");
        PhysicalSkillStore.save(physical, List.of(new PhysicalSkillSync.Skill(0, 0, 0, 0, 0, 0)));
        Path recording = temp.resolve("recordings.json");
        Files.writeString(recording, "{}");
        assertThrows(IOException.class, () -> GenerationCheckpointStore.publish(
            temp.resolve("generations"), recording, memory, null, null, null, null, physical));
    }

    @Test void oldGenerationWithoutPhysicalSkillsRemainsReadable() throws Exception {
        Path memory = temp.resolve("memory");
        new PersonalityMemoryStore(memory);
        NpcLearningCheckpoint.save(memory, List.of(new HabitBrain()));
        Path recording = temp.resolve("recordings.json");
        Files.writeString(recording, "{}");
        Path root = temp.resolve("legacy");
        GenerationCheckpointStore.publish(root, recording, memory, null, null);
        assertFalse(Files.exists(GenerationCheckpointStore.latest(root).resolve("physical-skills.bin")));
    }
}
