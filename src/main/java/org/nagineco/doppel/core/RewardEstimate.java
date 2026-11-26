package org.nagineco.doppel.core;
/** Standalone equivalent of the upstream NaoRlpPatch goal estimator. */
public final class RewardEstimate {
 private int count;
 private double mean;
 public void record(double reward){
  if(!Double.isFinite(reward))return;
  if(count<Integer.MAX_VALUE)count++;
  double bounded=Math.max(-10,Math.min(10,reward));
  mean+=(bounded-mean)/Math.min(100,count);
 }
 public int count(){return count;}
 public double mean(){return mean;}
 public double preference(){return mean*Math.min(1.0,count/10.0);}
 public void restore(int count,double mean){
  if(count<0||!Double.isFinite(mean)||Math.abs(mean)>10||(count==0&&mean!=0))throw new IllegalArgumentException("Invalid reward estimate");
  this.count=count;this.mean=mean;
 }
}
