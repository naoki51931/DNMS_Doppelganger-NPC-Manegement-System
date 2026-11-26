package org.nagineco.standalone;
import org.nagineco.doppel.core.DnmsWorldApi;
import com.google.gson.Gson;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.io.*;
public final class WorldClient implements DnmsWorldApi {
 private final URI uri; private final String token;
 private UUID expectedWorld,expectedEpoch;
 public UUID epoch(){return Objects.requireNonNull(expectedEpoch);}
 public void bindIdentity(){var identity=identity();expectedWorld=identity.worldId();expectedEpoch=Objects.requireNonNull(identity.epoch());}
 public void bindWorld(UUID id){expectedWorld=Objects.requireNonNull(id);}
 private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
 private final Gson gson=new Gson();
 public record Snapshot(long tick,List<Npc> npcs){}
 private record Reply(SpawnResult result){}
 public WorldClient(String base,String token){
  URI u=URI.create(base);
  if(!"http".equals(u.getScheme())||!"127.0.0.1".equals(u.getHost())||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||!(u.getPath().isEmpty()||u.getPath().equals("/")))throw new IllegalArgumentException("Loopback HTTP only");
  if(token==null||token.length()<32)throw new IllegalArgumentException("DNMS_WORLD_TOKEN required");
  this.uri=u.resolve("/api/v1/npcs");this.token=token;
 }
 private HttpResponse<String> request(String body){
  var b=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).header("Authorization","Bearer "+token);
  if(expectedWorld!=null)b.header("X-DNMS-World",expectedWorld.toString());
  if(expectedEpoch!=null)b.header("X-DNMS-Epoch",expectedEpoch.toString());
  try{return client.send(body==null?b.GET().build():b.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());}
  catch(IOException e){throw new UncheckedIOException(e);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
 }
 private record WorldIdentity(UUID worldId,UUID epoch){}
 public UUID worldId(){return identity().worldId();}
 public boolean sameIdentity(){var i=identity();return Objects.equals(expectedWorld,i.worldId())&&Objects.equals(expectedEpoch,i.epoch());}
 private WorldIdentity identity(){
  var request=HttpRequest.newBuilder(uri.resolve("/api/v1/world")).timeout(Duration.ofSeconds(10)).header("Authorization","Bearer "+token).GET().build();
  try{var response=client.send(request,HttpResponse.BodyHandlers.ofString());if(response.statusCode()!=200)throw new IllegalStateException("World identity HTTP "+response.statusCode());return Objects.requireNonNull(gson.fromJson(response.body(),WorldIdentity.class));}
  catch(IOException e){throw new UncheckedIOException(e);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
 }
 private record WireNpc(UUID id,int x,int y,int z,double health){}
 private record WireSnapshot(long tick,List<WireNpc> npcs){}
 public Snapshot snapshot(){var r=request(null);if(r.statusCode()!=200)throw new IllegalStateException("HTTP "+r.statusCode());var s=gson.fromJson(r.body(),WireSnapshot.class);return new Snapshot(s.tick(),s.npcs().stream().map(n->new Npc(n.id(),new Position(n.x(),n.y(),n.z()),n.health())).toList());}
 public List<Npc> npcs(){return snapshot().npcs();}
 public long worldTick(){return snapshot().tick();}
 public SpawnResult spawnNpc(UUID id,Position p){var r=request(id+","+p.x()+","+p.y()+","+p.z());var result=gson.fromJson(r.body(),Reply.class).result();if(result==null||r.statusCode()!=switch(result){case CREATED->201;case ALREADY_EXISTS,CAPACITY->409;case INVALID_POSITION->400;case UNSUPPORTED->501;})throw new IllegalStateException("HTTP "+r.statusCode());return result;}
}
