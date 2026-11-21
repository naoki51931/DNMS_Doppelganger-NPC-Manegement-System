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
}
