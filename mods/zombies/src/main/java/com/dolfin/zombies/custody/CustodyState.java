package com.dolfin.zombies.custody;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;

/**
 * Pure, unit-testable state and placement helpers for Contagiado chests and Mimo spawn windows.
 * Keeps no Minecraft dependencies so JUnit can verify chest protection, valid block search, and Mimo eligibility.
 */
public final class CustodyState {
    private CustodyState(){}

    /** Protected Contagiado chest at primary (and optional secondary) coordinates. */
    public record ProtectedChest(UUID custody,UUID owner,String ownerName,CustodyLedger.Position primary,
                                 Optional<CustodyLedger.Position> secondary,long expiresAtTick){
        public static final Codec<ProtectedChest> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.STRING.xmap(UUID::fromString,UUID::toString).fieldOf("custody").forGetter(ProtectedChest::custody),
            Codec.STRING.xmap(UUID::fromString,UUID::toString).fieldOf("owner").forGetter(ProtectedChest::owner),
            Codec.STRING.fieldOf("ownerName").forGetter(ProtectedChest::ownerName),
            CustodyLedger.Position.CODEC.fieldOf("primary").forGetter(ProtectedChest::primary),
            CustodyLedger.Position.CODEC.optionalFieldOf("secondary").forGetter(ProtectedChest::secondary),
            Codec.LONG.fieldOf("expiresAtTick").forGetter(ProtectedChest::expiresAtTick)
        ).apply(i,ProtectedChest::new));

        public boolean matches(String dimension,int x,int y,int z){
            if(primary.dimension().equals(dimension)&&primary.x()==x&&primary.y()==y&&primary.z()==z)return true;
            return secondary.filter(s->s.dimension().equals(dimension)&&s.x()==x&&s.y()==y&&s.z()==z).isPresent();
        }
        public boolean locked(long nowTick){return nowTick<expiresAtTick;}
        public boolean canAccess(UUID actor,long nowTick,BiPredicate<UUID,UUID> sameParty){
            if(!locked(nowTick))return true;
            if(owner.equals(actor))return true;
            return sameParty!=null&&sameParty.test(owner,actor);
        }
    }

    @FunctionalInterface
    public interface BiPredicate<A,B> { boolean test(A a,B b); }

    /** Pending Mimo spawn window recorded when a player dies (unless killed by Contagiado). */
    public record PendingMimo<T>(UUID id,UUID player,String playerName,CustodyLedger.Position deathPos,
                                 double playerMaxHealth,List<T> armor,long createdTick,long expiresAtTick,String appearanceJson){
        public PendingMimo(UUID id,UUID player,String playerName,CustodyLedger.Position deathPos,double health,List<T> armor,long created,long expires){this(id,player,playerName,deathPos,health,armor,created,expires,"");}
        public PendingMimo{armor=List.copyOf(armor);}
        public static <T> Codec<PendingMimo<T>> codec(Codec<T> itemCodec){
            return RecordCodecBuilder.create(i->i.group(
                Codec.STRING.xmap(UUID::fromString,UUID::toString).fieldOf("id").forGetter(PendingMimo<T>::id),
                Codec.STRING.xmap(UUID::fromString,UUID::toString).fieldOf("player").forGetter(PendingMimo<T>::player),
                Codec.STRING.fieldOf("playerName").forGetter(PendingMimo<T>::playerName),
                CustodyLedger.Position.CODEC.fieldOf("deathPos").forGetter(PendingMimo<T>::deathPos),
                Codec.DOUBLE.fieldOf("playerMaxHealth").forGetter(PendingMimo<T>::playerMaxHealth),
                itemCodec.listOf().fieldOf("armor").forGetter(PendingMimo<T>::armor),
                Codec.LONG.fieldOf("createdTick").forGetter(PendingMimo<T>::createdTick),
                Codec.LONG.fieldOf("expiresAtTick").forGetter(PendingMimo<T>::expiresAtTick),
                Codec.STRING.optionalFieldOf("appearanceJson","").forGetter(PendingMimo<T>::appearanceJson)
            ).apply(i,PendingMimo::new));
        }
        public boolean expired(long nowTick){return nowTick>expiresAtTick;}
        public boolean withinRange(String dimension,double x,double y,double z,double maxRadius){
            if(!deathPos.dimension().equals(dimension))return false;
            double dx=x-(deathPos.x()+.5),dy=y-deathPos.y(),dz=z-(deathPos.z()+.5);
            return dx*dx+dy*dy+dz*dz<=maxRadius*maxRadius;
        }
    }

    /** Chosen placement for a single or double chest. */
    public record ChestSite(int x,int y,int z,Optional<int[]> second){}

    @FunctionalInterface
    public interface SiteValidator { boolean valid(int x,int y,int z); }

    private static final int[][] CARDINALS={{1,0},{-1,0},{0,1},{0,-1}};

    /**
     * Searches within {@code radius} around {@code (cx,cy,cz)} for a valid single or double chest site,
     * preferring the closest position to the zombie's death location.
     */
    public static Optional<ChestSite> findChestSite(int cx,int cy,int cz,int radius,boolean needDouble,SiteValidator ok){
        int bestDist=Integer.MAX_VALUE;
        ChestSite bestSingle=null,bestDouble=null;
        int bestSingleDist=Integer.MAX_VALUE;
        int r=Math.max(0,radius);
        for(int dy=-r;dy<=r;dy++)for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++){
            int x=cx+dx,y=cy+dy,z=cz+dz,d=dx*dx+dy*dy+dz*dz;
            if(!ok.valid(x,y,z))continue;
            if(d<bestSingleDist){bestSingleDist=d;bestSingle=new ChestSite(x,y,z,Optional.empty());}
            if(needDouble&&d<bestDist){
                for(int[] dir:CARDINALS){
                    int nx=x+dir[0],nz=z+dir[1];
                    if(ok.valid(nx,y,nz)){bestDist=d;bestDouble=new ChestSite(x,y,z,Optional.of(new int[]{nx,y,nz}));break;}
                }
            }
        }
        if(needDouble&&bestDouble!=null)return Optional.of(bestDouble);
        return Optional.ofNullable(bestSingle);
    }

    /** Mimo max health is 1.5x the dead player's max health (fallback to configured default when invalid). */
    public static double mimoHealth(double playerMaxHealth,double configuredFallback){
        return playerMaxHealth>0&&Double.isFinite(playerMaxHealth)?playerMaxHealth*1.5:configuredFallback;
    }
}
