package org.nagineco.doppel;

import java.util.*;

/** Two-step goal planner that combines integrated memory evidence and action outcomes. */
public final class NaoGoalPlanner {
    public record Plan(NaoLifeStore.Goal primary, NaoLifeStore.Goal fallback,
                       double score, String evidence) {}
    public static Plan choose(NaoLifeStore.Nao nao, SharedMemoryReader memory) {
        if (nao.energy < 20) return new Plan(NaoLifeStore.Goal.REST, NaoLifeStore.Goal.SAFETY, 1, "low energy");
        List<NaoLifeStore.Goal> candidates = List.of(NaoLifeStore.Goal.COLLECT,
            NaoLifeStore.Goal.BUILD, NaoLifeStore.Goal.EXPLORE, NaoLifeStore.Goal.SOCIAL);
        List<Plan> ranked = new ArrayList<>();
        for (NaoLifeStore.Goal goal : candidates) {
            NaoActionLearning.Action action = switch (goal) {
                case COLLECT -> NaoActionLearning.Action.COLLECT;
                case BUILD -> NaoActionLearning.Action.BUILD;
                case SOCIAL -> NaoActionLearning.Action.TALK;
                default -> NaoActionLearning.Action.EXPLORE;
            };
            double success = (nao.actionSuccesses.getOrDefault(action.name(),0) + 1.0)
                / (nao.actionAttempts.getOrDefault(action.name(),0) + 2.0);
            int observations = memory == null ? 0 : memory.evidence(goal);
            double score = success * 2 + Math.log1p(observations) * 0.3
                + NaoRlpPatch.preference(nao, goal) * 0.2;
            ranked.add(new Plan(goal, NaoLifeStore.Goal.REST, score,
                "MIP=" + observations + ", success=" + String.format(Locale.ROOT,"%.2f",success)));
        }
        ranked.sort(Comparator.comparingDouble(Plan::score).reversed());
        Plan first = ranked.get(0), second = ranked.get(1);
        return new Plan(first.primary(), second.primary(), first.score(), first.evidence());
    }
    /** A deterministic, independently evolving plan derived from the Nao's persistent ambition. */
    public static Plan choosePersonal(NaoLifeStore.Nao nao, SharedMemoryReader memory, long tick) {
        if (nao.energy < 20) return new Plan(NaoLifeStore.Goal.REST, NaoLifeStore.Goal.SAFETY, 1, "recovery");
        String ambition = (nao.longTermGoal + " " + nao.occupation).toUpperCase(Locale.ROOT);
        NaoLifeStore.Goal target;
        if (ambition.matches(".*(WRITE|BOOK|RESEARCH|STUDY|BUILD|CRAFT|ENGINEER).*"))
            target = NaoLifeStore.Goal.BUILD;
        else if (ambition.matches(".*(FRIEND|FAMILY|LOVE|CARE|CHILD|TEACH|SOCIAL).*"))
            target = NaoLifeStore.Goal.SOCIAL;
        else if (ambition.matches(".*(FARM|COLLECT|FOOD|TRADE|COOK).*"))
            target = NaoLifeStore.Goal.COLLECT;
        else target = NaoLifeStore.Goal.EXPLORE;
        if (tick >= nao.planStepUntilTick) {
            String[] steps = {"OBSERVE", "PREPARE", "PRACTICE", "REVIEW"};
            int index = Arrays.asList(steps).indexOf(nao.planStep);
            nao.planStep = steps[(index + 1) % steps.length];
            nao.planStepUntilTick = tick + 1200;
        }
        return new Plan(target, NaoLifeStore.Goal.REST, 1.0,
            "ambition=" + nao.longTermGoal + " step=" + nao.planStep
                + " memory_evidence=" + (memory == null ? 0 : memory.evidence(target)));
    }

}
