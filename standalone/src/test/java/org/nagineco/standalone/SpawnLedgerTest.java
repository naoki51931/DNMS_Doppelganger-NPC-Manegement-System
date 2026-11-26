package org.nagineco.standalone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import org.nagineco.doppel.core.DnmsWorldApi;
import static org.junit.jupiter.api.Assertions.*;
class SpawnLedgerTest {
 @TempDir Path dir;
 static class World implements DnmsWorldApi {
  final List<Npc> list=new ArrayList<>();
  int calls; boolean fail=true;
  public List<Npc> npcs(){return List.copyOf(list);}
  public long worldTick(){return 0;}
  public SpawnResult spawnNpc(UUID id,Position p){
   calls++;
   if(list.stream().anyMatch(n->n.id().equals(id)))return SpawnResult.ALREADY_EXISTS;
   list.add(new Npc(id,p,20));
   if(fail)throw new IllegalStateException("lost acknowledgement");
   return SpawnResult.CREATED;
  }
 }
 @Test void retryRequiresServerAcknowledgement()throws Exception{
  var world=new World();var file=dir.resolve("pending");var ledger=new SpawnLedger(file);
  assertThrows(IllegalStateException.class,()->ledger.next(world));
  assertTrue(Files.exists(file));var id=world.list.getFirst().id();
  assertEquals(DnmsWorldApi.SpawnResult.ALREADY_EXISTS,ledger.next(world));
  assertEquals(2,world.calls);assertEquals(1,world.list.size());assertEquals(id,world.list.getFirst().id());assertFalse(Files.exists(file));
 }
}
