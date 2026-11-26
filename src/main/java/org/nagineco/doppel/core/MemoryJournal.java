package org.nagineco.doppel.core;
import java.nio.file.*;
import java.io.IOException;
import java.util.Locale;
import java.nio.charset.StandardCharsets;
public final class MemoryJournal {
 private MemoryJournal(){}
 public static Path path(Path dir,int slot){
  if(slot<0||slot>=256)throw new IllegalArgumentException("Nao index");
  return dir.resolve(String.format(Locale.ROOT,"nao_%02d.md",slot+1));
 }
 public static void ensure(Path file,String initial)throws IOException{
  try{Files.writeString(file,initial,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
  catch(FileAlreadyExistsException e){if(!Files.isRegularFile(file))throw e;}
 }
 public static String worldProfile(int value){
  if(value<0||value>10)throw new IllegalArgumentException("preference");
  var out=new StringBuilder("# Nao\n");
  for(String key:new String[]{"none","explore","build","collect","safety","social"})out.append("- preference.").append(key).append(": ").append(value).append('\n');
  return out.toString();
 }
 public static void initializeWorld(Path dir,int count,int preference)throws IOException{
  if(count<1||count>256)throw new IllegalArgumentException("count");
  Files.createDirectories(dir);
  ensure(dir.resolve("nao_personality.md"),worldProfile(preference));
  for(int i=0;i<count;i++)ensure(path(dir,i),"# Nao "+(i+1)+"\n");
 }
 public static void append(Path dir,int slot,long tick,int x,int y,int z,String action,String emotion,String reason)throws IOException{
  Files.writeString(path(dir,slot),String.format(Locale.ROOT,"- day=%d tick=%d position=(%d,%d,%d) action=%s emotion=%s reason=%s%n",tick/24000+1,tick%24000,x,y,z,action,emotion,reason),StandardCharsets.UTF_8,StandardOpenOption.APPEND);
 }
}
