package org.nagineco.doppel.core;
/** Engine-neutral policy preserving the original World fixture thresholds. */
public final class WorldLifePolicy {
 private WorldLifePolicy(){}
 public enum Goal { COLLECT, BUILD, REST, EAT, FORAGE, SLEEP }
 public static Goal goal(double health,int satiety,int food,long tick,long nextHarvest,boolean sleeping,int fatigue,int built,int collected){
  if(health>0&&satiety<=12000&&food>0)return Goal.EAT;
  if(health>0&&food==0&&satiety<=18000&&tick>=nextHarvest)return Goal.FORAGE;
  if(health>0&&(sleeping||fatigue>=16000||(fatigue>=10000&&tick%24000>=12000&&tick%24000<22000)))return Goal.SLEEP;
  return built==8?Goal.REST:collected==8?Goal.BUILD:Goal.COLLECT;
 }
 public static int clockTick(int hour,int minute){
  if(hour<0||hour>23||minute<0||minute>59)throw new IllegalArgumentException("clock");
  return Math.floorMod((hour*60+minute-360)*24000/1440,24000);
 }
}
