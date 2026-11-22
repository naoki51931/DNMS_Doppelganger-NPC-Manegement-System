package org.nagineco.doppel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Filesystem restart simulation: publish a complete generation, recover it
 * into a fresh memory directory, and verify no journal replay occurs.
 */
class LearningGenerationIntegrationTest {
    @TempDir Path temp;

    @Test void generationRoundTripRestoresEachNaoOnce() throws Exception {
        Path live = temp.resolve("live");
        PersonalityMemoryStore original = new PersonalityMemoryStore(live);
        HabitBrain first = new HabitBrain();
        first.learnMemory(HabitBrain.Move.EAST, HabitBrain.Emotion.HAPPY,
            HabitBrain.Reason.EXPLORE, 100);
        HabitBrain second = new HabitBrain();
        second.learnMemory(HabitBrain.Move.WEST, HabitBrain.Emotion.NEUTRAL,
            HabitBrain.Reason.SAFETY, 13000);
        original.remember(0, 1, 100, new PersonalityMemoryStore.VecRecord(
            0, 0, 0, "EAST", HabitBrain.Emotion.HAPPY, HabitBrain.Reason.EXPLORE));
        original.flush();
        NpcLearningCheckpoint.save(live, List.of(first, second));
        Path recording = temp.resolve("recordings.json");
        Files.writeString(recording, "{}", StandardCharsets.UTF_8);
        Path root = temp.resolve("generations");
        GenerationCheckpointStore.publish(root, recording, live, null, null);
        Path generation = GenerationCheckpointStore.latest(root);
        assertNotNull(generation);
        assertEquals("2", Files.readString(generation.resolve("learning_count.txt")).trim());

        Path restarted = temp.resolve("restarted");
        GenerationCheckpointStore.restoreMemories(generation, restarted);
        PersonalityMemoryStore recovered = new PersonalityMemoryStore(restarted);
        assertEquals(1, NpcLearningCheckpoint.load(restarted, 0, recovered).samples());
        assertEquals(1, NpcLearningCheckpoint.load(restarted, 1, recovered).samples());
    }

    @Test void missingSnapshotInvalidatesGenerationRatherThanReplayingJournal() throws Exception {
        Path live = temp.resolve("live");
        new PersonalityMemoryStore(live);
        NpcLearningCheckpoint.save(live, List.of(new HabitBrain(), new HabitBrain()));
        Path recording = temp.resolve("recordings.json");
        Files.writeString(recording, "{}", StandardCharsets.UTF_8);
        Path root = temp.resolve("generations");
        GenerationCheckpointStore.publish(root, recording, live, null, null);
        Path generation = GenerationCheckpointStore.latest(root);
        Files.delete(generation.resolve("memories").resolve(NpcLearningCheckpoint.fileName(1)));
        assertThrows(IOException.class, () -> GenerationCheckpointStore.latest(root));
    }

    @Test void oldGenerationWithoutLearningManifestStillLoads() throws Exception {
        Path live = temp.resolve("legacy");
        new PersonalityMemoryStore(live);
        Path recording = temp.resolve("recordings.json");
        Files.writeString(recording, "{}", StandardCharsets.UTF_8);
        Path root = temp.resolve("generations");
        GenerationCheckpointStore.publish(root, recording, live, null, null);
        assertNotNull(GenerationCheckpointStore.latest(root));
    }
}
