package org.nagineco.doppel.core;
import com.sun.net.httpserver.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.io.IOException;
/** Shared HTTP protocol owned by DNMS; engine callbacks provide synchronized state. */
public final class DnmsWorldHttpApi {
 private DnmsWorldHttpApi(){}
 @FunctionalInterface public interface Spawn {
  DnmsWorldApi.SpawnResult apply(UUID id,DnmsWorldApi.Position pos)throws IOException;
 }
 public static String snapshot(DnmsWorldApi api){
  var out=new StringBuilder("{\"tick\":").append(api.worldTick()).append(",\"npcs\":[");
  boolean first=true;
  for(var n:api.npcs()){
   if(!first)out.append(',');first=false;
   out.append("{\"id\":\"").append(n.id()).append("\",\"x\":").append(n.position().x()).append(",\"y\":").append(n.position().y()).append(",\"z\":").append(n.position().z()).append(",\"health\":").append(n.health()).append('}');
  }
  return out.append("]}").toString();
 }
 private static void json(HttpExchange e,int status,String value)throws IOException{
  byte[] bytes=value.getBytes(StandardCharsets.UTF_8);
  e.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");
  e.getResponseHeaders().set("Cache-Control","no-store");
  e.sendResponseHeaders(status,bytes.length);
  try(var body=e.getResponseBody()){body.write(bytes);}
 }
 public static void register(HttpServer server,String token,java.util.function.Supplier<String> snapshot,Spawn spawn){
  register(server,token,snapshot,spawn,null);
 }
 public static void register(HttpServer server,String token,java.util.function.Supplier<String> snapshot,Spawn spawn,UUID worldId){
  if(token==null||token.length()<32)return;
  server.createContext("/api/v1/npcs",e->{
   if(!e.getRequestURI().getPath().equals("/api/v1/npcs")){json(e,404,"{}");return;}
   String auth=e.getRequestHeaders().getFirst("Authorization");
   if(auth==null||!java.security.MessageDigest.isEqual(auth.getBytes(StandardCharsets.UTF_8),("Bearer "+token).getBytes(StandardCharsets.UTF_8))){json(e,401,"{}");return;}
   String expected=e.getRequestHeaders().getFirst("X-DNMS-World");
   if((worldId!=null&&!worldId.toString().equals(expected))||(worldId==null&&expected!=null)){json(e,409,"{}");return;}
   if(e.getRequestMethod().equals("GET")){json(e,200,snapshot.get());return;}
   if(!e.getRequestMethod().equals("POST")){json(e,405,"{}");return;}
   try{
    byte[] body=e.getRequestBody().readNBytes(129);
    if(body.length>128){json(e,413,"{}");return;}
    String[] p=new String(body,StandardCharsets.UTF_8).split(",",-1);
    if(p.length!=4)throw new IllegalArgumentException("spawn");
    UUID id=UUID.fromString(p[0]);if(!id.toString().equalsIgnoreCase(p[0]))throw new IllegalArgumentException("uuid");
    var result=spawn.apply(id,new DnmsWorldApi.Position(Integer.parseInt(p[1]),Integer.parseInt(p[2]),Integer.parseInt(p[3])));
    int code=switch(result){case CREATED->201;case ALREADY_EXISTS,CAPACITY->409;case INVALID_POSITION->400;case UNSUPPORTED->501;};
    json(e,code,"{\"result\":\""+result+"\"}");
   }catch(IllegalArgumentException ex){json(e,400,"{}");}catch(java.io.IOException ex){json(e,500,"{}");}
  });
 }

}
