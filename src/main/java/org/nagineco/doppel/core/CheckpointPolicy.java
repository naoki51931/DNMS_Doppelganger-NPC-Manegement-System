package org.nagineco.doppel.core;
import java.nio.file.*;
import java.io.IOException;
import java.util.UUID;
public final class CheckpointPolicy {
 private CheckpointPolicy(){}
 public static String generation(long tick){return tick+"-"+UUID.randomUUID();}
 public static boolean valid(String id){return id!=null&&id.matches("[0-9]+-[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");}
 public static boolean due(long tick){return tick%1200==0;}
 public static void publish(Path pointer,String id)throws IOException{
  if(!valid(id))throw new IOException("Invalid generation");
  var tmp=Files.createTempFile(pointer.toAbsolutePath().getParent(),"generation-",".tmp");
  try{Files.writeString(tmp,id);Files.move(tmp,pointer,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(tmp);}
 }
}
