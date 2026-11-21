package org.nagineco.doppel;

import java.util.List;
import java.util.Map;

/** Deterministic, per-Nao decisions over a common work plan; no shared inventory. */
public final class NaoCooperationPatch {
    private NaoCooperationPatch() {}
    public static final String PLAN = "STOCKPILE_LOGS";
    public static String role(NaoLifeStore.Nao nao) {
        if (nao.energy < 20) return "REST";
        if (nao.collection >= nao.exploration && nao.collection >= nao.building) return "GATHER";
        if (nao.building > nao.collection) return "BUILDER";
        return "SCOUT";
    }
    public static boolean transfer(NaoLifeStore.Nao sender, NaoLifeStore.Nao receiver) {
        if (sender == receiver || sender.inventory == null || receiver.inventory == null) return false;
        if (!"GATHER".equals(role(sender)) || !"BUILDER".equals(role(receiver))) return false;
        int receiverTotal = receiver.inventory.values().stream().mapToInt(Integer::intValue).sum();
        if (receiverTotal >= 64) return false;
        for (Map.Entry<String,Integer> entry : sender.inventory.entrySet()) {
            if (!entry.getKey().endsWith("_log") || entry.getValue() == null || entry.getValue() <= 1) continue;
            String material = entry.getKey();
            entry.setValue(entry.getValue() - 1);
            receiver.inventory.merge(material, 1, Integer::sum);
            sender.social++;
            receiver.social++;
            sender.lastAction = "TRANSFER";
            sender.lastOutcome = "GAVE 1 " + material + " TO Nao " + receiver.number;
            receiver.lastAction = "RECEIVE";
            receiver.lastOutcome = "RECEIVED 1 " + material + " FROM Nao " + sender.number;
            return true;
        }
        return false;
    }
    public static int cooperate(List<NaoLifeStore.Nao> participants) {
        int transfers = 0;
        for (NaoLifeStore.Nao receiver : participants) {
            if (!"BUILDER".equals(role(receiver))) continue;
            for (NaoLifeStore.Nao sender : participants) {
                if (transfer(sender, receiver)) { transfers++; break; }
            }
        }
        return transfers;
    }
}
