package org.nagineco.doppel.core;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.text.Normalizer;
public final class SharedMemorySearch {
 private static final Pattern EVENT=Pattern.compile("^- nao=(\\d+) day=(\\d+) tick=(\\d+) position=\\(([^)]*)\\) action=([A-Z_]+) emotion=([A-Z_]+) reason=([A-Z_]+)$");
 public record Event(int nao,int day,int tick,String position,String action,String emotion,String reason){}
 private final List<Event> events=new ArrayList<>();
 private final Map<String,BitSet> index=new HashMap<>();
 private static final Map<String,String> ALIASES=Map.ofEntries(Map.entry("採集","COLLECT"),Map.entry("建築","BUILD"),Map.entry("探索","EXPLORE"),Map.entry("安全","SAFETY"),Map.entry("交流","SOCIAL"),Map.entry("恐怖","AFRAID"),Map.entry("北","NORTH"),Map.entry("南","SOUTH"),Map.entry("東","EAST"),Map.entry("西","WEST"));
 public SharedMemorySearch(Path file)throws IOException{
  if(!Files.exists(file))return;
  try(var reader=Files.newBufferedReader(file)){String line;while((line=reader.readLine())!=null){
   var m=EVENT.matcher(line);if(!m.matches())continue;
   var e=new Event(Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3)),m.group(4),m.group(5),m.group(6),m.group(7));
   int id=events.size();events.add(e);
   for(String key:List.of(e.action(),e.emotion(),e.reason()))index.computeIfAbsent(key,k->new BitSet()).set(id);
  }}
 }
 public List<Event> hazardEvents(){
  var unique=new LinkedHashMap<DnmsWorldApi.Position,Event>();
  for(var e:events){
   if(!e.action().equals("STAY")||!e.emotion().equals("AFRAID")||!e.reason().equals("SAFETY"))continue;
   String[] xyz=e.position().split(",");
   if(xyz.length!=3)continue;
   try{
    var p=new DnmsWorldApi.Position(Integer.parseInt(xyz[0].trim()),Integer.parseInt(xyz[1].trim()),Integer.parseInt(xyz[2].trim()));
    unique.put(p,e);
   }catch(NumberFormatException ignored){}
  }
  return List.copyOf(unique.values());
 }
 public List<Event> search(String query,int limit){
  if(limit<1||limit>100)throw new IllegalArgumentException("limit");
  String q=Normalizer.normalize(query,Normalizer.Form.NFKC).toUpperCase(Locale.ROOT);
  Set<String> keys=new HashSet<>();
  ALIASES.forEach((a,b)->{if(q.contains(a))keys.add(b);});
  for(String word:q.split("[^A-Z_]+"))if(index.containsKey(word))keys.add(word);
  if(keys.isEmpty())return List.of();
  BitSet matches=new BitSet();matches.set(0,events.size());
  for(String key:keys){var bits=index.get(key);if(bits==null)return List.of();matches.and(bits);}
  List<Event> result=new ArrayList<>();
  for(int i=matches.length()-1;i>=0&&result.size()<limit;i=matches.previousSetBit(i-1))result.add(events.get(i));
  return List.copyOf(result);
 }
}
