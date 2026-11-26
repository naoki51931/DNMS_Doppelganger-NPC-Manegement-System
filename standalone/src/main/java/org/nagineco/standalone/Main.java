package org.nagineco.standalone;
import java.util.UUID;
import org.nagineco.doppel.core.DnmsWorldApi.Position;
public final class Main {
 public static void main(String[] args){
  var client=new WorldClient(System.getenv().getOrDefault("DNMS_WORLD_URL","http://127.0.0.1:8080"),System.getenv("DNMS_WORLD_TOKEN"));
  if(args.length==0||args[0].equals("list")){System.out.println(client.snapshot());return;}
  if(args.length==5&&args[0].equals("spawn")){var result=client.spawnNpc(UUID.fromString(args[1]),new Position(Integer.parseInt(args[2]),Integer.parseInt(args[3]),Integer.parseInt(args[4])));System.out.println(result);if(result!=org.nagineco.doppel.core.DnmsWorldApi.SpawnResult.CREATED)System.exit(2);return;}
  if(args.length==2&&args[0].equals("next")){try{System.out.println(new SpawnLedger(java.nio.file.Path.of(args[1])).next(client));return;}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}}
  throw new IllegalArgumentException("list | spawn UUID x y z | next ledger-path");
 }
}
