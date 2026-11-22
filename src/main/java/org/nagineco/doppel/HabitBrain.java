package org.nagineco.doppel;

import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-character imitation model.
 * Explicit emotion/reason labels are self-reports, never inferred as facts.
 * A learned action policy is conditioned on daylight, emotion and motivation.
 */
public final class HabitBrain {
    public enum Move { STAY, NORTH, SOUTH, WEST, EAST }
    public enum Emotion { NEUTRAL, HAPPY, CURIOUS, AFRAID, ANGRY, SAD }
    public enum Reason { NONE, EXPLORE, BUILD, COLLECT, SAFETY, SOCIAL }

    private org.nagineco.doppel.core.NpcLearningState learning =
        new org.nagineco.doppel.core.NpcLearningState(
            Emotion.values().length, Reason.values().length, Move.values().length);
    private final List<Vec3d> observations = new ArrayList<>();
    private Emotion emotion = Emotion.NEUTRAL;
    private Reason reason = Reason.NONE;
    private Move lastManualMove = null;
    private Emotion lastManualEmotion = Emotion.NEUTRAL;
    private Reason lastManualReason = Reason.NONE;
    private int lastManualDayPart = 0;

    public void setEmotion(Emotion value) { emotion = java.util.Objects.requireNonNull(value); learning.setEmotion(value.ordinal()); }
    public void setReason(Reason value) { reason = java.util.Objects.requireNonNull(value); learning.setMotivation(value.ordinal()); }
    public Emotion emotion() { return emotion; }
    public Reason reason() { return reason; }

    /** Save learning evidence without serializing Minecraft position objects. */
    public org.nagineco.doppel.core.NpcLearningState.Snapshot learningSnapshot() {
        return learning.snapshot();
    }

    /** Replace evidence rather than replaying the same journal a second time. */
    public void restoreLearning(org.nagineco.doppel.core.NpcLearningState restored) {
        if (restored == null || restored.snapshot().counts()[0].length != Emotion.values().length
                || restored.snapshot().counts()[0][0].length != Reason.values().length
                || restored.snapshot().counts()[0][0][0].length != Move.values().length)
            throw new IllegalArgumentException("Incompatible NPC learning dimensions");
        learning = org.nagineco.doppel.core.NpcLearningState.restore(restored.snapshot());
        emotion = Emotion.values()[learning.emotion()];
        reason = Reason.values()[learning.motivation()];
        lastManualMove = null;
        observations.clear();
    }

    /** Reset movement continuity after AI control or teleport, without resetting learned counts. */
    public void resetObservationAnchor(Vec3d position) {
        observations.clear();
        observations.add(position);
        lastManualMove = null;
    }

    public void observe(Vec3d position) {
        observations.add(position);
        if (observations.size() < 2) return;
        Vec3d previous = observations.get(observations.size() - 2);
        Move move = classify(position.subtract(previous));
        int dayPart = ((observations.size() - 1) % 24000) >= 12000 ? 1 : 0;
        learning.observe(dayPart, emotion.ordinal(), reason.ordinal(), move.ordinal());
        lastManualMove = move;
        lastManualEmotion = emotion;
        lastManualReason = reason;
        lastManualDayPart = dayPart;
    }

    /** Reward only the last manually observed movement, never an AMP-generated move. */
    public void rewardLatestMove(int tick, double value) {
        if (lastManualMove == null || !Double.isFinite(value)) return;
        learnReward(lastManualMove, lastManualEmotion, lastManualReason, lastManualDayPart, value);
    }

    public void learnReward(Move move, Emotion e, Reason r, int part, double value) {
        learning.reward(part, e.ordinal(), r.ordinal(), move.ordinal(), value);
    }

    public Move lastManualMove() { return lastManualMove; }
    public Emotion lastManualEmotion() { return lastManualEmotion; }
    public Reason lastManualReason() { return lastManualReason; }
    public int lastManualDayPart() { return lastManualDayPart; }

    private static Move classify(Vec3d delta) {
        double x = delta.x, z = delta.z;
        if (Math.abs(x) < 0.06 && Math.abs(z) < 0.06) return Move.STAY;
        if (Math.abs(x) > Math.abs(z)) return x > 0 ? Move.EAST : Move.WEST;
        return z > 0 ? Move.SOUTH : Move.NORTH;
    }

    /** Exact emotional context first, then a fallback across known contexts. */
    public Move decide(int dayTick) {
        return Move.values()[learning.decide(dayTick >= 12000 ? 1 : 0)];
    }

    /**
     * One shared personality scores the combined demonstrations of all 24 Nao.
     * Individual memory gives the current character extra influence, while the
     * shared profile controls motivation affinity. No separate personality exists.
     */
    public static Move sharedDecision(List<HabitBrain> all, int characterIndex,
                                      int dayTick, PersonalityMemoryStore personality) {
        return sharedDecision(all, characterIndex, dayTick, personality, false);
    }

    public static Move sharedDecision(List<HabitBrain> all, int characterIndex,
                                      int dayTick, PersonalityMemoryStore personality,
                                      boolean rewardEnabled) {
        if (all.isEmpty()) return Move.STAY;
        int part = dayTick >= 12000 ? 1 : 0;
        double[] scores = new double[Move.values().length];
        for (int j = 0; j < all.size(); j++) {
            HabitBrain brain = all.get(j);
            double[] preferences = new double[Reason.values().length];
            for (Reason reason : Reason.values())
                preferences[reason.ordinal()] = personality.preference(reason);
            brain.learning.accumulateScores(part, preferences, rewardEnabled, scores);
        }
        return Move.values()[org.nagineco.doppel.core.LearnedActionPolicy.dominantPositive(scores)];
    }

    public static Vec3d applyMove(Vec3d position, Move move) {
        double distance = 0.12;
        return switch (move) {
            case NORTH -> position.add(0, 0, -distance);
            case SOUTH -> position.add(0, 0, distance);
            case WEST -> position.add(-distance, 0, 0);
            case EAST -> position.add(distance, 0, 0);
            default -> position;
        };
    }

    public void learnMemory(Move move, Emotion e, Reason r, int tick) {
        learning.observe(tick >= 12000 ? 1 : 0, e.ordinal(), r.ordinal(), move.ordinal());
    }

    public Move latestMove() {
        if (observations.size() < 2) return Move.STAY;
        return classify(observations.get(observations.size() - 1)
                .subtract(observations.get(observations.size() - 2)));
    }

    public Vec3d step(Vec3d position, int dayTick) {
        double distance = 0.12;
        return switch (decide(dayTick)) {
            case NORTH -> position.add(0, 0, -distance);
            case SOUTH -> position.add(0, 0, distance);
            case WEST -> position.add(-distance, 0, 0);
            case EAST -> position.add(distance, 0, 0);
            default -> position;
        };
    }

    public int samples() { return learning.samples(); }
}
