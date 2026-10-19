package com.dolfin.zombies;

import java.util.function.DoubleSupplier;

/**
 * Pure blood-moon calendar (no Minecraft types, so the night arithmetic is unit-tested).
 * Night N is the night of day N-1: {@code night(time)=floor(time/24000)+1}. Each night is
 * decided exactly once, as soon as its day is first observed (so sleeping or a thunderstorm nap
 * before dusk cannot dodge it), and the decision is persisted; time rewinds never reroll.
 */
public final class BloodMoonSchedule {
    public static final long DAY=24000, NIGHT_START=13000, NIGHT_END=23000;
    public boolean active;
    public long lastNight=-1, activeNight=-1, scheduledNight=-1;

    public enum Step {NONE, START, END_DAWN, END_DISABLED}
    /** evaluatedNight>0 when this call decided a new night; missedNight>0 when a selected night was skipped by a time jump. */
    public record Outcome(Step step,String trigger,long night,long evaluatedNight,boolean selected,double roll,long missedNight) {}

    public static long night(long time){return Math.floorDiv(time,DAY)+1;}
    public static boolean isNight(long time){long t=Math.floorMod(time,DAY);return t>=NIGHT_START&&t<NIGHT_END;}
    /** cadaNNoches wins when positive (deterministic by absolute night number); otherwise a roll in [0,1) below the chance. */
    public static boolean selected(long night,int every,double chance,double roll){return every>0?night%every==0:roll<chance;}
    public static String trigger(int every){return every>0?"every_n":"random";}

    public BloodMoonSchedule(){}
    public BloodMoonSchedule(boolean active,long lastNight,long activeNight,long scheduledNight){
        this.active=active;this.lastNight=lastNight;this.activeNight=activeNight;this.scheduledNight=scheduledNight;
    }

    /** Advance to the observed overworld clock. The roll supplier is only consulted for random nights. */
    public Outcome advance(long time,boolean enabled,int every,double chance,DoubleSupplier roll){
        long night=night(time);boolean dark=isNight(time);
        if(active&&(!enabled||!dark||night!=activeNight)){
            long ended=activeNight;active=false;activeNight=-1;
            return new Outcome(enabled?Step.END_DAWN:Step.END_DISABLED,enabled?"dawn":"disabled",ended,-1,false,Double.NaN,-1);
        }
        long evaluated=-1,missed=-1;boolean picked=false;double r=Double.NaN;
        if(night>lastNight){
            if(scheduledNight>0&&scheduledNight<night)missed=scheduledNight;
            lastNight=night;evaluated=night;
            if(enabled){if(every<=0)r=roll.getAsDouble();picked=selected(night,every,chance,r);}
            scheduledNight=picked?night:-1;
        }
        if(!active&&enabled&&dark&&night==scheduledNight){
            active=true;activeNight=night;scheduledNight=-1;
            return new Outcome(Step.START,trigger(every),night,evaluated,picked,r,missed);
        }
        return new Outcome(Step.NONE,null,night,evaluated,picked,r,missed);
    }

    /** Command start; the caller moves the clock into the night first. False when already active. */
    public boolean forceStart(long time){
        if(active)return false;
        long night=night(time);active=true;activeNight=night;lastNight=Math.max(lastNight,night);scheduledNight=-1;return true;
    }
    /** Command stop: ends the active moon or cancels tonight's pending one. False when there was nothing to stop. */
    public boolean forceStop(){
        if(!active&&scheduledNight<0)return false;
        active=false;activeNight=-1;scheduledNight=-1;return true;
    }
    /** Active now, or tonight is already selected (blocks pre-dusk/thunderstorm naps that would skip it). */
    public boolean sleepBlocked(long time){return active||scheduledNight==night(time);}
    public boolean pendingTonight(long time){return !active&&scheduledNight==night(time);}
}
