package org.nagineco.doppel.core;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
public final class WorldIdentityPolicy {
 private WorldIdentityPolicy(){}
 public static final int MAX_POPULATION=256;
 public static UUID issue(){return UUID.randomUUID();}
 public static UUID legacySlot(int slot){return UUID.nameUUIDFromBytes(("dnms-world-legacy-slot:"+slot).getBytes(StandardCharsets.UTF_8));}
 public static DnmsWorldApi.Position anchor(int slot){return new DnmsWorldApi.Position((slot%16)*12+4,1,(slot/16)*12+4);}
}
