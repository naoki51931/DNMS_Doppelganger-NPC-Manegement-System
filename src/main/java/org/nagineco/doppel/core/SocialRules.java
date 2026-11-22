package org.nagineco.doppel.core;

import java.util.Set;

/** Game-independent validation of DNMS social relationship roles. */
public final class SocialRules {
    private static final Set<String> NAO_ROLES =
        Set.of("FRIEND", "FAMILY", "COLLEAGUE", "PARTNER", "SPOUSE", "PARENT", "CHILD");
    private static final Set<String> PERSON_ROLES =
        Set.of("FRIEND", "FAMILY", "COLLEAGUE", "PARTNER", "SPOUSE", "PARENT", "CHILD", "HOUSEMATE");

    private SocialRules() {}

    public static boolean validNaoRelationship(String role) {
        return NAO_ROLES.contains(role);
    }

    public static boolean validPersonRelationship(String role) {
        return PERSON_ROLES.contains(role);
    }

    public static boolean validExternalPersonId(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{1,48}");
    }
}
