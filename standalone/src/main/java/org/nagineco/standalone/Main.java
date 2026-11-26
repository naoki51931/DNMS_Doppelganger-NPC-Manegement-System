package org.nagineco.standalone;
import java.util.UUID;
import org.nagineco.doppel.core.DnmsWorldApi.Position;
public final class Main {
 public static void main(String[] args){
  var client=new WorldClient(System.getenv().getOrDefault("DNMS_WORLD_URL","http://127.0.0.1:8080"),System.getenv("DNMS_WORLD_TOKEN"));
  client.bindWorld(client.worldId());
  if(args.length==0||args[0].equals("list")){System.out.println(client.snapshot());return;}
  if(args.length==5&&args[0].equals("spawn")){var result=client.spawnNpc(UUID.fromString(args[1]),new Position(Integer.parseInt(args[2]),Integer.parseInt(args[3]),Integer.parseInt(args[4])));System.out.println(result);if(result!=org.nagineco.doppel.core.DnmsWorldApi.SpawnResult.CREATED)System.exit(2);return;}
  if((args.length==2||args.length==3)&&args[0].equals("next-world")){
   try{
    var root=java.nio.file.Path.of(args[1]);var id=client.worldId();
    if(args.length==3&&!id.equals(UUID.fromString(args[2])))throw new IllegalArgumentException("World UUID mismatch");
    client.bindWorld(id);
    var result=new SpawnLedger(root.resolve(id.toString()).resolve("spawn.pending")).next(client,()->{
     if(!id.equals(client.worldId()))throw new IllegalStateException("World changed during request; inspect server state");
     PopulationRegistry.capture(root,id,client);
    });
    System.out.println(result);
    if(result!=org.nagineco.doppel.core.DnmsWorldApi.SpawnResult.CREATED&&result!=org.nagineco.doppel.core.DnmsWorldApi.SpawnResult.ALREADY_EXISTS)System.exit(2);
    return;
   }catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
  }
  if(args.length==2&&args[0].equals("next")){throw new IllegalArgumentException("Use next-world root; legacy ledger is not world-scoped");}
  if((args.length==2||args.length==3)&&args[0].equals("registry")){try{var id=client.worldId();if(args.length==3&&!id.equals(UUID.fromString(args[2])))throw new IllegalArgumentException("World UUID mismatch");client.bindWorld(id);PopulationRegistry.capture(java.nio.file.Path.of(args[1]),id,client);return;}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}}
  throw new IllegalArgumentException("list | spawn UUID x y z | next ledger-path | next-world root [expected-world-uuid] | registry root [expected-world-uuid]");
 }
}
