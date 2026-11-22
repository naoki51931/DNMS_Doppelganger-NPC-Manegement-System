package org.nagineco.doppel.core;

import java.util.Locale;

/** Pure decision policy: classify an NPC's long-term ambition without game APIs. */
public final class GoalClassifier {
    public enum Goal { BUILD, SOCIAL, COLLECT, EXPLORE }

    private GoalClassifier() {}

    public static Goal classify(String longTermGoal, String occupation) {
        String ambition = (String.valueOf(longTermGoal) + " " + String.valueOf(occupation))
            .toUpperCase(Locale.ROOT);
        if (ambition.matches(".*(WRITE|BOOK|RESEARCH|STUDY|BUILD|CRAFT|ENGINEER).*"))
            return Goal.BUILD;
        if (ambition.matches(".*(FRIEND|FAMILY|LOVE|CARE|CHILD|TEACH|SOCIAL).*"))
            return Goal.SOCIAL;
        if (ambition.matches(".*(FARM|COLLECT|FOOD|TRADE|COOK).*"))
            return Goal.COLLECT;
        return Goal.EXPLORE;
    }
}
