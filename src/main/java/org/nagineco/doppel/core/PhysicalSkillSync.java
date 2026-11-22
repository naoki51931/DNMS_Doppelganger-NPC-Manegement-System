package org.nagineco.doppel.core;

import java.util.List;
import java.util.Locale;

/**
 * Optional physical skill transfer policy, independent of Minecraft.
 * Skill technique and physical capacity are distinct state variables.
 */
public final class PhysicalSkillSync {
    private PhysicalSkillSync() {}

    public enum Mode {
        INDIVIDUAL, TECHNIQUE, FULL;

        public static Mode parse(String value) {
            return Mode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
    }

    public record Skill(double technique, double coordination, double proficiency,
                        double strength, double agility, double endurance) {
        public Skill {
            if (!valid(technique) || !valid(coordination) || !valid(proficiency)
                    || !valid(strength) || !valid(agility) || !valid(endurance))
                throw new IllegalArgumentException("Skill values must be finite in [0, 100]");
        }
        private static boolean valid(double value) {
            return Double.isFinite(value) && value >= 0 && value <= 100;
        }
    }

    /** Immutable transfer; never changes the source or recipient in place. */
    public static Skill transfer(Skill source, Skill recipient, Mode mode, double ratio) {
        if (source == null || recipient == null || mode == null
                || !Double.isFinite(ratio) || ratio < 0 || ratio > 1)
            throw new IllegalArgumentException("Invalid skill transfer");
        if (mode == Mode.INDIVIDUAL) return recipient;
        return new Skill(
            mix(recipient.technique(), source.technique(), ratio),
            mix(recipient.coordination(), source.coordination(), ratio),
            mix(recipient.proficiency(), source.proficiency(), ratio),
            mode == Mode.FULL ? mix(recipient.strength(), source.strength(), ratio) : recipient.strength(),
            mode == Mode.FULL ? mix(recipient.agility(), source.agility(), ratio) : recipient.agility(),
            mode == Mode.FULL ? mix(recipient.endurance(), source.endurance(), ratio) : recipient.endurance());
    }

    private static double mix(double original, double learned, double ratio) {
        return original * (1 - ratio) + learned * ratio;
    }

    /** One-way broadcast from a selected source, without recursive amplification. */
    public static List<Skill> broadcast(List<Skill> skills, int sourceIndex,
                                        Mode mode, double ratio) {
        if (skills == null || skills.isEmpty() || skills.size() > 256
                || sourceIndex < 0 || sourceIndex >= skills.size())
            throw new IllegalArgumentException("Invalid NPC skill group");
        Skill source = skills.get(sourceIndex);
        return java.util.stream.IntStream.range(0, skills.size())
            .mapToObj(i -> i == sourceIndex ? source : transfer(source, skills.get(i), mode, ratio))
            .toList();
    }
}
