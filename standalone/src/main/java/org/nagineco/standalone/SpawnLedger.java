package org.nagineco.standalone;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import org.nagineco.doppel.core.DnmsWorldApi;
import org.nagineco.doppel.core.WorldIdentityPolicy;
/** Persistent pending request, scoped to a user-selected world ledger. */
public final class SpawnLedger {
 private final Path file;
 public SpawnLedger(Path file){this.file=file.toAbsolutePath();}
 public DnmsWorldApi.SpawnResult next(DnmsWorldApi world)throws IOException{
  return next(world,()->{});
 }
 @FunctionalInterface public interface Commit {void run()throws IOException;}
 public DnmsWorldApi.SpawnResult next(DnmsWorldApi world,Commit commit)throws IOException{
  Files.createDirectories(file.getParent());
  try(var channel=java.nio.channels.FileChannel.open(file.resolveSibling(file.getFileName()+".lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.lock()){
   var ids=world.npcs();
   UUID id; int slot;
   if(Files.exists(file)){
    var parts=Files.readString(file).trim().split(",");
    if(parts.length!=2)throw new IOException("Invalid pending spawn");
    id=UUID.fromString(parts[0]);slot=Integer.parseInt(parts[1]);
    if(slot<0||slot>=256)throw new IOException("Invalid slot");
    if(ids.stream().noneMatch(n->n.id().equals(id))&&ids.size()!=slot)throw new IOException("Population changed; pending request retained");
   }else{
    slot=ids.size();if(slot>=256)return DnmsWorldApi.SpawnResult.CAPACITY;
    id=WorldIdentityPolicy.issue();
    var tmp=Files.createTempFile(file.getParent(),"spawn-",".tmp");
    try{Files.writeString(tmp,id+","+slot);try(var durable=java.nio.channels.FileChannel.open(tmp,StandardOpenOption.WRITE)){durable.force(true);}Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE);}finally{Files.deleteIfExists(tmp);}
   }
   var result=world.spawnNpc(id,WorldIdentityPolicy.anchor(slot));
   if(result==DnmsWorldApi.SpawnResult.CREATED||result==DnmsWorldApi.SpawnResult.ALREADY_EXISTS){commit.run();Files.delete(file);}
   return result;
  }
 }
}
