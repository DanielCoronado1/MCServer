package com.dolfin.zombies;
import java.util.Arrays;
/** Bounded measurement window. Passing requires 25 measured entities near a real player on EVERY frame. */
public final class TickBudgetWindow {
 public static final long BUDGET_NANOS=2_000_000;
 private final long[] samples;
 private int size;
 private long total,totalServer;
 private int minEntities=Integer.MAX_VALUE,minNear=Integer.MAX_VALUE;
 public TickBudgetWindow(int capacity){if(capacity<1)throw new IllegalArgumentException("capacity");samples=new long[capacity];}
 public void add(long specialNanos,long serverNanos,int entities,int near){if(size==samples.length)throw new IllegalStateException("window full");samples[size++]=Math.max(0,specialNanos);total+=Math.max(0,specialNanos);totalServer+=Math.max(0,serverNanos);minEntities=Math.min(minEntities,entities);minNear=Math.min(minNear,near);}
 public int size(){return size;}
 public boolean full(){return size==samples.length;}
 public double meanMs(){return size==0?0:total/(double)size/1e6;}
 public double serverMeanMs(){return size==0?0:totalServer/(double)size/1e6;}
 public double percentileMs(double quantile){if(size==0)return 0;var copy=Arrays.copyOf(samples,size);Arrays.sort(copy);return copy[Math.max(0,Math.min(size-1,(int)Math.ceil(quantile*size)-1))]/1e6;}
 public int minEntities(){return size==0?0:minEntities;}
 public int minNear(){return size==0?0:minNear;}
 public boolean withinBudget(){return full()&&minEntities>=25&&minNear>=25&&total<=BUDGET_NANOS*(long)size;}
}
