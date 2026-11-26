package org.nagineco.standalone;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.nagineco.doppel.core.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.*;
class WorldClientTest {
 @Test void connection()throws Exception{
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  var token="01234567890123456789012345678901";
  var list=new ArrayList<DnmsWorldApi.Npc>();
  DnmsWorldHttpApi.register(server,token,()->"{\"tick\":0,\"npcs\":[]}",(id,p)->{if(list.stream().anyMatch(n->n.id().equals(id)))return DnmsWorldApi.SpawnResult.ALREADY_EXISTS;list.add(new DnmsWorldApi.Npc(id,p,20));return DnmsWorldApi.SpawnResult.CREATED;});
  server.start();
  try{var c=new WorldClient("http://127.0.0.1:"+server.getAddress().getPort(),token);assertEquals(0,c.worldTick());var id=UUID.randomUUID();var p=new DnmsWorldApi.Position(4,1,4);assertEquals(DnmsWorldApi.SpawnResult.CREATED,c.spawnNpc(id,p));assertEquals(DnmsWorldApi.SpawnResult.ALREADY_EXISTS,c.spawnNpc(id,p));assertEquals(id,list.get(0).id());}finally{server.stop(0);}
 }
}
