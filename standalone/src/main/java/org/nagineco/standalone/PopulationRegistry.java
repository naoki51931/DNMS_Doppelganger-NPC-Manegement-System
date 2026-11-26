package org.nagineco.standalone;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import org.nagineco.doppel.core.DnmsWorldApi;
/** Atomic observed population snapshot, scoped by an explicitly selected world UUID. */
public final class PopulationRegistry {
 private PopulationRegistry(){}
 public static void capture(Path root,UUID worldId,DnmsWorldApi world)throws IOException{
  Path dir=root.toAbsolutePath().resolve(worldId.toString());
  Files.createDirectories(dir);
  try(var channel=java.nio.channels.FileChannel.open(dir.resolve("registry.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.lock()){
   var npcs=world.npcs();
   if(npcs.size()>256)throw new IOException("Population exceeds limit");
   var ids=new HashSet<UUID>();
   var out=new StringBuilder("DNMS-REGISTRY-1\n").append(worldId).append('\n');
   for(int slot=0;slot<npcs.size();slot++){
    var npc=npcs.get(slot);
    if(npc.id()==null||!ids.add(npc.id()))throw new IOException("Invalid or duplicate UUID");
    out.append(slot).append(',').append(npc.id()).append('\n');
   }
   var tmp=Files.createTempFile(dir,"registry-",".tmp");
   try{Files.writeString(tmp,out);Files.move(tmp,dir.resolve("population.csv"),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
   finally{Files.deleteIfExists(tmp);}
  }
 }
}
