package com.dolfin.zombies.custody;

import com.dolfin.zombies.*;
import com.dolfin.zombies.custody.CustodyLedger.*;
import com.dolfin.zombies.custody.CustodyState.*;
import com.dolfin.zombies.kinds.MimoZombie;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.saveddata.*;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Overworld SavedData and server-thread coordinator for item custody, mailbox fallback,
 * protected Contagiado chests, Mimo spawn windows, and `/zombies dondeesta`.
 */
public final class CustodyManager extends SavedData {
    public static final ItemAccounting.Stacks<ItemStack> STACKS=new ItemAccounting.Stacks<>(){
        public boolean isEmpty(ItemStack s){return s==null||s.isEmpty();}
        public int count(ItemStack s){return s==null?0:s.getCount();}
        public String key(ItemStack s){return BuiltInRegistries.ITEM.getKey(s.getItem())+"#"+ItemStack.hashItemAndComponents(s);}
    };
    public static final Codec<ItemAccounting.Packet<ItemStack>> PACKET_CODEC=ItemAccounting.Packet.codec(ItemStack.OPTIONAL_CODEC);
    public static final Codec<PendingMimo<ItemStack>> PENDING_MIMO_CODEC=PendingMimo.codec(ItemStack.OPTIONAL_CODEC);
    public static final Codec<CustodyManager> CODEC=RecordCodecBuilder.create(i->i.group(
        CustodyLedger.CODEC.fieldOf("ledger").forGetter(m->m.ledger),
        PACKET_CODEC.listOf().optionalFieldOf("mailbox",List.of()).forGetter(m->List.copyOf(m.mailbox.values())),
        ProtectedChest.CODEC.listOf().optionalFieldOf("chests",List.of()).forGetter(m->List.copyOf(m.chests)),
        PENDING_MIMO_CODEC.listOf().optionalFieldOf("mimos",List.of()).forGetter(m->List.copyOf(m.mimos))
    ).apply(i,CustodyManager::new));
    public static final SavedDataType<CustodyManager> TYPE=new SavedDataType<>(DolfinZombies.id("custody"),CustodyManager::new,CODEC,null);

    private static MinecraftServer server;
    private static CustodyManager instance;

    private final CustodyLedger ledger;
    private final LinkedHashMap<UUID,ItemAccounting.Packet<ItemStack>> mailbox=new LinkedHashMap<>();
    private final List<ProtectedChest> chests=new ArrayList<>();
    private final List<PendingMimo<ItemStack>> mimos=new ArrayList<>();

    public CustodyManager(){this(new CustodyLedger(),List.of(),List.of(),List.of());}
    public CustodyManager(CustodyLedger ledger,List<ItemAccounting.Packet<ItemStack>> mail,List<ProtectedChest> chests,List<PendingMimo<ItemStack>> mimos){
        this.ledger=ledger;
        for(var p:mail)this.mailbox.put(p.custody(),p);
        this.chests.addAll(chests);
        this.mimos.addAll(mimos);
        this.ledger.listener((from,r,why)->{
            setDirty();
            DolfinZombies.LOGGER.info("event=item_custody id={} from={} to={} owner={} ownerName={} kind={} items=count:{},stacks:{} detail={} entity={} pos={} reason={}",
                r.id(),from,r.holder(),r.owner(),r.ownerName(),r.kind(),r.items(),r.stacks(),r.summary(),r.entity().map(UUID::toString).orElse("none"),r.position(),why);
        });
    }

    public static void init(){
        ServerLifecycleEvents.SERVER_STARTED.register(s->{
            server=s;
            instance=s.overworld().getDataStorage().computeIfAbsent(TYPE);
            DolfinZombies.LOGGER.info("event=custody_loaded active={} mailbox={} chests={} pendingMimos={}",
                instance.ledger.active().size(),instance.mailbox.size(),instance.chests.size(),instance.mimos.size());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(s->{if(instance!=null)s.overworld().getDataStorage().saveAndJoin();});
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{instance=null;server=null;});
        ServerPlayerEvents.JOIN.register(player->deliverMailbox(player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer,newPlayer,alive)->deliverMailbox(newPlayer));
        ServerTickEvents.END_SERVER_TICK.register(s->tick(s));
        UseBlockCallback.EVENT.register(CustodyManager::onUseBlock);
        PlayerBlockBreakEvents.BEFORE.register((level,player,pos,state,be)->onBreakBlock(level,player,pos));
    }

    public static CustodyManager get(MinecraftServer s){
        if(instance!=null)return instance;
        if(s!=null){server=s;instance=s.overworld().getDataStorage().computeIfAbsent(TYPE);}
        return instance;
    }
    public CustodyLedger ledger(){return ledger;}
    public List<ProtectedChest> chests(){return Collections.unmodifiableList(chests);}
    public List<PendingMimo<ItemStack>> pendingMimos(){return Collections.unmodifiableList(mimos);}

    public static Position posOf(ServerLevel level,BlockPos pos){
        return new Position(level.dimension().identifier().toString(),pos.getX(),pos.getY(),pos.getZ());
    }

    /** Opens a new custody record on the server thread and flushes SavedData dirty flag. */
    public Result openCustody(ItemAccounting.Packet<ItemStack> packet,Holder holder,UUID entityId,String kind,ServerLevel level,BlockPos pos,String reason){
        long now=level.getGameTime();
        var rec=new CustodyLedger.Record(
            packet.custody(),packet.owner(),packet.ownerName(),holder,Optional.ofNullable(entityId),kind,
            posOf(level,pos),ItemAccounting.count(STACKS,packet.items()),ItemAccounting.stacks(STACKS,packet.items()),
            ItemAccounting.detail(STACKS,packet.items(),96),now,now);
        return ledger.open(rec,reason);
    }

    /** Updates tracked coordinates for `/zombies dondeesta` without emitting a state transition. */
    public void trackEntity(UUID custodyId,ServerLevel level,BlockPos pos){
        if(custodyId!=null&&ledger.track(custodyId,posOf(level,pos),level.getGameTime()))setDirty();
    }

    /** Drops held items as unlimited-lifetime, fire-immune ItemEntities, or falls back to mailbox if in void/lava. */
    public void releaseToGroundOrMailbox(ItemAccounting.Packet<ItemStack> packet,Holder expected,ServerLevel level,BlockPos pos,boolean unsafe,String reason){
        if(packet==null||packet.items().isEmpty())return;
        if(unsafe||pos.getY()<level.getMinY()+1||level.getFluidState(pos).is(FluidTags.LAVA)){
            storeInMailbox(packet,expected,level,pos,reason+"_mailbox_fallback");
            return;
        }
        var res=ledger.move(packet.custody(),expected,Holder.GROUND,Optional.empty(),posOf(level,pos),level.getGameTime(),reason);
        if(!res.ok()){
            DolfinZombies.LOGGER.warn("event=custody_release_rejected id={} expected={} previous={} reason={}",packet.custody(),expected,res.previous(),res.reason());
            return;
        }
        spawnPersistentDrops(level,pos,packet.items());
    }

    public void storeInMailbox(ItemAccounting.Packet<ItemStack> packet,Holder expected,ServerLevel level,BlockPos pos,String reason){
        if(packet==null||packet.items().isEmpty())return;
        var res=ledger.move(packet.custody(),expected,Holder.MAILBOX,Optional.empty(),posOf(level,pos),level.getGameTime(),reason);
        if(!res.ok()){
            DolfinZombies.LOGGER.warn("event=custody_mailbox_rejected id={} expected={} previous={} reason={}",packet.custody(),expected,res.previous(),res.reason());
            return;
        }
        List<ItemStack> copies=packet.items().stream().filter(s->s!=null&&!s.isEmpty()).map(ItemStack::copy).toList();
        mailbox.put(packet.custody(),new ItemAccounting.Packet<>(packet.custody(),packet.owner(),packet.ownerName(),copies));
        setDirty();
        var online=level.getServer().getPlayerList().getPlayer(packet.owner());
        if(online!=null&&online.isAlive())deliverMailbox(online);
    }

    public static void deliverMailbox(ServerPlayer player){
        var mgr=get(player.level().getServer());
        if(mgr==null||player.isDeadOrDying())return;
        var entries=new ArrayList<>(mgr.mailbox.values());
        for(var pkt:entries){
            if(!pkt.owner().equals(player.getUUID()))continue;
            var res=mgr.ledger.move(pkt.custody(),Holder.MAILBOX,Holder.RETURNED,Optional.of(player.getUUID()),posOf(player.level(),player.blockPosition()),player.level().getGameTime(),"mailbox_delivery");
            mgr.mailbox.remove(pkt.custody());
            mgr.setDirty();
            if(!res.ok())continue;
            for(ItemStack stack:pkt.items()){
                if(stack==null||stack.isEmpty())continue;
                ItemStack copy=stack.copy();
                if(!player.getInventory().add(copy)&&!copy.isEmpty()){
                    spawnPersistentDrops(player.level(),player.blockPosition(),List.of(copy));
                }
            }
            notifyOwner(player.level().getServer(),pkt.owner(),Component.translatable("message.dolfinzombies.custody_returned"));
        }
    }

    /** Places a single or double Contagiado chest, or falls back to persistent ground drops / mailbox. */
    public void releaseToChestOrFallback(ItemAccounting.Packet<ItemStack> packet,ServerLevel level,BlockPos origin,boolean chestEnabled,int searchRadius,int protectMinutes,boolean unsafeDeath,String reason){
        if(packet==null||packet.items().isEmpty())return;
        if(!chestEnabled||!level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING)){
            releaseToGroundOrMailbox(packet,Holder.INFECTED,level,origin,unsafeDeath,chestEnabled?"mob_griefing_false":"chest_disabled");
            return;
        }
        List<ItemStack> real=packet.items().stream().filter(s->s!=null&&!s.isEmpty()).map(ItemStack::copy).toList();
        boolean needDouble=real.size()>ItemAccounting.CHEST_SLOTS;
        var siteOpt=CustodyState.findChestSite(origin.getX(),origin.getY(),origin.getZ(),searchRadius,needDouble,(x,y,z)->validChestBlock(level,new BlockPos(x,y,z),packet.owner()));
        if(siteOpt.isEmpty()){
            DolfinZombies.LOGGER.info("event=contagiado_chest_no_site id={} owner={} origin={} radius={} fallback=ground_or_mailbox",packet.custody(),packet.owner(),origin,searchRadius);
            releaseToGroundOrMailbox(packet,Holder.INFECTED,level,origin,unsafeDeath,"no_valid_chest_site");
            return;
        }
        ChestSite site=siteOpt.get();
        BlockPos primaryPos=new BlockPos(site.x(),site.y(),site.z());
        var res=ledger.move(packet.custody(),Holder.INFECTED,Holder.CHEST,Optional.empty(),posOf(level,primaryPos),level.getGameTime(),reason);
        if(!res.ok()){
            DolfinZombies.LOGGER.warn("event=contagiado_chest_rejected id={} previous={} reason={}",packet.custody(),res.previous(),res.reason());
            return;
        }
        Optional<BlockPos> secondPos=site.second().map(a->new BlockPos(a[0],a[1],a[2]));
        var plan=ItemAccounting.chestPlan(STACKS,real,secondPos.isPresent());
        placeAndFillChest(level,primaryPos,secondPos,packet.ownerName(),plan);
        long expires=level.getGameTime()+Math.max(0,protectMinutes)*1200L;
        chests.add(new ProtectedChest(packet.custody(),packet.owner(),packet.ownerName(),posOf(level,primaryPos),secondPos.map(p->posOf(level,p)),expires));
        setDirty();
        if(!plan.overflow().isEmpty()){
            spawnPersistentDrops(level,primaryPos.above(),plan.overflow());
        }
        notifyOwner(level.getServer(),packet.owner(),Component.translatable("message.dolfinzombies.contagiado_chest",packet.ownerName(),primaryPos.getX(),primaryPos.getY(),primaryPos.getZ(),protectMinutes));
    }

    private static boolean validChestBlock(ServerLevel level,BlockPos pos,UUID owner){
        if(pos.getY()<level.getMinY()+1||pos.getY()>=level.getMaxY()-1)return false;
        if(ExternalIntegrations.blockedByForeignClaim(level,pos,owner))return false;
        if(!level.getFluidState(pos).isEmpty())return false;
        BlockState state=level.getBlockState(pos);
        if(!state.isAir()&&!state.canBeReplaced())return false;
        if(level.getBlockEntity(pos)!=null)return false;
        BlockState below=level.getBlockState(pos.below());
        if(below.isAir()||!below.getFluidState().isEmpty()||below.getCollisionShape(level,pos.below()).isEmpty())return false;
        for(Direction dir:Direction.Plane.HORIZONTAL){
            if(level.getBlockState(pos.relative(dir)).is(Blocks.CHEST))return false;
        }
        return true;
    }

    private static void placeAndFillChest(ServerLevel level,BlockPos firstPos,Optional<BlockPos> secondPos,String ownerName,ItemAccounting.ChestPlan<ItemStack> plan){
        Component customName=Component.translatable("container.dolfinzombies.player_chest",ownerName);
        if(secondPos.isPresent()){
            BlockPos sec=secondPos.get();
            int dx=sec.getX()-firstPos.getX(),dz=sec.getZ()-firstPos.getZ();
            Direction toSec=dx>0?Direction.EAST:dx<0?Direction.WEST:dz>0?Direction.SOUTH:Direction.NORTH;
            Direction facing=toSec.getCounterClockWise();
            BlockState firstState=Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING,facing).setValue(ChestBlock.TYPE,ChestType.LEFT).setValue(ChestBlock.WATERLOGGED,false);
            BlockState secState=Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING,facing).setValue(ChestBlock.TYPE,ChestType.RIGHT).setValue(ChestBlock.WATERLOGGED,false);
            level.setBlock(firstPos,firstState,3);
            level.setBlock(sec,secState,3);
            fillSingleChestBlock(level,firstPos,firstState,customName,plan.first());
            fillSingleChestBlock(level,sec,secState,customName,plan.second());
        }else{
            BlockState state=Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING,Direction.NORTH).setValue(ChestType.SINGLE!=null?ChestBlock.TYPE:ChestBlock.TYPE,ChestType.SINGLE).setValue(ChestBlock.WATERLOGGED,false);
            level.setBlock(firstPos,state,3);
            fillSingleChestBlock(level,firstPos,state,customName,plan.first());
        }
    }

    private static void fillSingleChestBlock(ServerLevel level,BlockPos pos,BlockState state,Component customName,List<ItemStack> items){
        if(level.getBlockEntity(pos) instanceof ChestBlockEntity chest){
            chest.applyComponents(chest.components(),DataComponentPatch.builder().set(DataComponents.CUSTOM_NAME,customName).build());
            for(int i=0;i<items.size()&&i<chest.getContainerSize();i++){
                chest.setItem(i,items.get(i).copy());
            }
            chest.setChanged();
            ExternalIntegrations.logChestToLedger(level,pos,state,chest,items,"contagiado_zombie");
        }else{
            spawnPersistentDrops(level,pos,items);
        }
    }

    public static void spawnPersistentDrops(ServerLevel level,BlockPos pos,List<ItemStack> items){
        for(ItemStack s:items){
            if(s==null||s.isEmpty())continue;
            ItemEntity entity=new ItemEntity(level,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,s.copy());
            entity.setDefaultPickUpDelay();
            entity.setUnlimitedLifetime();
            entity.setPermanentlyInvulnerable(true);
            level.addFreshEntity(entity);
        }
    }

    public static void notifyOwner(MinecraftServer s,UUID owner,Component msg){
        if(s==null||owner==null||!DolfinZombies.config().current().general().get("mensajesChat").getAsBoolean())return;
        ServerPlayer p=s.getPlayerList().getPlayer(owner);
        if(p!=null)p.sendSystemMessage(msg);
    }

    /** Queues a Mimo spawn window when a player dies (unless killed by Contagiado). */
    public void scheduleMimoIfEligible(ServerPlayer player,boolean killedByContagiado){
        var snap=DolfinZombies.config().current();
        if(killedByContagiado||!snap.abilityEnabled(ZombieKind.MIMO,"aparicionPorMuerte")||!SpawnRules.worldAllowed(player.level()))return;
        var cfg=snap.zombie(ZombieKind.MIMO).getAsJsonObject("habilidades").getAsJsonObject("aparicionPorMuerte");
        double chance=cfg.get("probabilidadAparecer").getAsDouble();
        double roll=player.level().getRandom().nextDouble();
        if(roll>=chance){
            DolfinZombies.LOGGER.debug("event=mimo_roll_skipped player={} chance={} roll={}",player.getUUID(),chance,roll);
            return;
        }
        boolean copyEnch=cfg.get("copiarEncantamientos").getAsBoolean();
        List<ItemStack> armor=MimoZombie.copyPlayerArmor(player,copyEnch);
        long now=player.level().getGameTime();
        long windowTicks=Math.max(1,cfg.get("minutosVentana").getAsInt())*1200L;
        var pending=new PendingMimo<>(UUID.randomUUID(),player.getUUID(),player.getGameProfile().name(),
            posOf(player.level(),player.blockPosition()),player.getMaxHealth(),armor,now,now+windowTicks,
            net.minecraft.world.item.component.ResolvableProfile.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,net.minecraft.world.item.component.ResolvableProfile.createResolved(player.getGameProfile())).getOrThrow().toString());
        mimos.add(pending);
        setDirty();
        DolfinZombies.LOGGER.info("event=mimo_scheduled id={} player={} playerName={} pos={} expiresAt={}",
            pending.id(),pending.player(),pending.playerName(),pending.deathPos(),pending.expiresAtTick());
    }

    private static void tick(MinecraftServer s){
        var mgr=get(s);
        if(mgr==null||s.getTickCount()%20!=0)return;
        long now=s.overworld().getGameTime();
        if(mgr.chests.removeIf(c->!c.locked(now)))mgr.setDirty();
        if(mgr.mimos.isEmpty())return;
        if(!DolfinZombies.config().current().abilityEnabled(ZombieKind.MIMO,"aparicionPorMuerte")){
            mgr.mimos.clear();mgr.setDirty();return;
        }
        var it=mgr.mimos.iterator();
        while(it.hasNext()){
            var pending=it.next();
            if(pending.expired(now)){
                DolfinZombies.LOGGER.info("event=mimo_window_expired id={} player={} pos={}",pending.id(),pending.player(),pending.deathPos());
                it.remove();mgr.setDirty();continue;
            }
            if(now<pending.createdTick()+60)continue;
            if(trySpawnMimo(s,pending)){
                it.remove();mgr.setDirty();
            }
        }
    }

    private static boolean trySpawnMimo(MinecraftServer s,PendingMimo<ItemStack> pending){
        for(ServerLevel level:s.getAllLevels()){
            String dim=level.dimension().identifier().toString();
            if(!dim.equals(pending.deathPos().dimension()))continue;
            boolean playerNear=false;
            for(ServerPlayer p:level.players()){
                if(!p.isSpectator()&&p.isAlive()&&pending.withinRange(dim,p.getX(),p.getY(),p.getZ(),48)){
                    playerNear=true;break;
                }
            }
            if(!playerNear)return false;
            BlockPos center=new BlockPos(pending.deathPos().x(),pending.deathPos().y(),pending.deathPos().z());
            if(!SpawnRules.belowCap(level,center))return false;
            BlockPos spawnPos=findMimoSpawnPos(level,center);
            var entity=DolfinZombies.type(ZombieKind.MIMO).create(level,EntitySpawnReason.EVENT);
            if(!(entity instanceof MimoZombie mimo))return false;
            mimo.snapTo(spawnPos.getX()+.5,spawnPos.getY(),spawnPos.getZ()+.5,level.getRandom().nextFloat()*360f,0f);
            mimo.finalizeSpawn(level,level.getCurrentDifficultyAt(spawnPos),EntitySpawnReason.EVENT,null);
            mimo.configureFromDeath(pending.player(),pending.playerName(),pending.playerMaxHealth(),pending.armor());
            if(!pending.appearanceJson().isEmpty()){
                try{net.minecraft.world.item.component.ResolvableProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,com.google.gson.JsonParser.parseString(pending.appearanceJson())).resultOrPartial(error->DolfinZombies.LOGGER.warn("event=mimo_profile_invalid id={} reason={}",pending.id(),error)).ifPresent(mimo::setPlayerAppearance);}
                catch(RuntimeException error){DolfinZombies.LOGGER.warn("event=mimo_profile_invalid id={} reason={}",pending.id(),error.toString());}
            }
            if(level.addFreshEntity(mimo)){
                DolfinZombies.LOGGER.info("event=mimo_spawned id={} entity={} player={} playerName={} pos={} health={}",
                    pending.id(),mimo.getUUID(),pending.player(),pending.playerName(),spawnPos,mimo.getMaxHealth());
                return true;
            }
        }
        return false;
    }

    private static BlockPos findMimoSpawnPos(ServerLevel level,BlockPos center){
        var rng=level.getRandom();
        for(int i=0;i<12;i++){
            int dx=rng.nextIntBetweenInclusive(-12,12),dz=rng.nextIntBetweenInclusive(-12,12);
            for(int dy=-4;dy<=4;dy++){
                BlockPos p=center.offset(dx,dy,dz);
                if(level.getBlockState(p).isAir()&&level.getBlockState(p.above()).isAir()&&!level.getBlockState(p.below()).isAir()&&level.getFluidState(p).isEmpty()){
                    return p;
                }
            }
        }
        return center;
    }

    public static Optional<ProtectedChest> findProtectedChest(Level level,BlockPos pos){
        if(!(level instanceof ServerLevel sl))return Optional.empty();
        var mgr=get(sl.getServer());
        if(mgr==null)return Optional.empty();
        String dim=sl.dimension().identifier().toString();
        long now=sl.getGameTime();
        for(var c:mgr.chests){
            if(c.locked(now)&&c.matches(dim,pos.getX(),pos.getY(),pos.getZ()))return Optional.of(c);
        }
        return Optional.empty();
    }

    private static InteractionResult onUseBlock(Player player,Level level,net.minecraft.world.InteractionHand hand,BlockHitResult hit){
        if(level.isClientSide()||!(level instanceof ServerLevel sl))return InteractionResult.PASS;
        var opt=findProtectedChest(sl,hit.getBlockPos());
        if(opt.isEmpty())return InteractionResult.PASS;
        var chest=opt.get();
        long now=sl.getGameTime();
        if(chest.canAccess(player.getUUID(),now,(owner,actor)->ExternalIntegrations.sameParty(sl.getServer(),owner,actor))){
            return InteractionResult.PASS;
        }
        long remMin=Math.max(1,(chest.expiresAtTick()-now+1199)/1200);
        if(player instanceof ServerPlayer sp){
            sp.sendSystemMessage(Component.translatable("message.dolfinzombies.chest_locked",chest.ownerName(),remMin));
        }
        DolfinZombies.LOGGER.debug("event=contagiado_chest_denied custody={} owner={} actor={} pos={} remainingTicks={}",
            chest.custody(),chest.owner(),player.getUUID(),hit.getBlockPos(),chest.expiresAtTick()-now);
        return InteractionResult.FAIL;
    }

    private static boolean onBreakBlock(Level level,Player player,BlockPos pos){
        if(level.isClientSide()||!(level instanceof ServerLevel sl))return true;
        var opt=findProtectedChest(sl,pos);
        if(opt.isEmpty())return true;
        var chest=opt.get();
        long now=sl.getGameTime();
        if(chest.canAccess(player.getUUID(),now,(owner,actor)->ExternalIntegrations.sameParty(sl.getServer(),owner,actor))){
            var mgr=get(sl.getServer());
            if(mgr!=null){mgr.chests.remove(chest);mgr.setDirty();}
            return true;
        }
        long remMin=Math.max(1,(chest.expiresAtTick()-now+1199)/1200);
        if(player instanceof ServerPlayer sp){
            sp.sendSystemMessage(Component.translatable("message.dolfinzombies.chest_locked",chest.ownerName(),remMin));
        }
        return false;
    }

    /** Executes `/zombies dondeesta` for the calling player, listing active thieves and converted zombies visible to them or their OPAC party. */
    public static int whereIs(net.minecraft.commands.CommandSourceStack source){
        ServerPlayer player=source.getPlayer();
        if(player==null){
            source.sendFailure(Component.translatable("message.dolfinzombies.dondeesta_player_only"));
            return 0;
        }
        var mgr=get(source.getServer());
        if(mgr==null){
            source.sendFailure(Component.translatable("message.dolfinzombies.dondeesta_empty"));
            return 0;
        }
        refreshLoadedEntityPositions(source.getServer(),mgr);
        UUID caller=player.getUUID();
        var matches=mgr.ledger.visibleTo(owner->owner.equals(caller)||ExternalIntegrations.sameParty(source.getServer(),owner,caller))
            .stream().filter(r->r.holder()==Holder.THIEF||r.holder()==Holder.INFECTED).toList();
        if(matches.isEmpty()){
            source.sendSuccess(()->Component.translatable("message.dolfinzombies.dondeesta_empty"),false);
            return 0;
        }
        for(var r:matches){
            String key=r.holder()==Holder.THIEF?"message.dolfinzombies.dondeesta_ladron":"message.dolfinzombies.dondeesta_contagiado";
            source.sendSuccess(()->Component.translatable(key,r.ownerName(),r.position().dimension(),r.position().x(),r.position().y(),r.position().z(),r.items()),false);
        }
        DolfinZombies.LOGGER.info("event=command_dondeesta actor={} matches={}",player.getName().getString(),matches.size());
        return matches.size();
    }

    private static void refreshLoadedEntityPositions(MinecraftServer s,CustodyManager mgr){
        for(var r:mgr.ledger.active()){
            if(r.entity().isEmpty())continue;
            UUID eid=r.entity().get();
            for(ServerLevel level:s.getAllLevels()){
                var e=level.getEntity(eid);
                if(e!=null){
                    mgr.ledger.track(r.id(),posOf(level,e.blockPosition()),level.getGameTime());
                    break;
                }
            }
        }
    }
}
