package br.com.kraftcity.industry;

import br.com.kraftcity.builder.BuilderData;
import br.com.kraftcity.employment.EmploymentData;
import br.com.kraftcity.workforce.WorkerRecord;
import br.com.kraftcity.workforce.WorkforceData;
import br.com.kraftcity.employment.Profession;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import br.com.residenciasaldeoes.data.ResidenceData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.TallFlowerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.Logger;

public final class LumberjackWork {
    static final boolean TRACE = Boolean.getBoolean("kraftcity.lumberjackDebug");
    static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<Villager, String> LAST_STATE = new WeakHashMap<>();
    private static final String JOB = "lumberjack_tree";
    public static final String CHOPPING_AXE = "KCLumberjackChopping";
    private static final int SEARCH_INTERVAL = 60;
    private static final double WORK_REACH = 3;
    private static final Map<Villager, Session> SESSIONS = new WeakHashMap<>();

    private static final class Session {
        long scanAfter;
        long lastTick = Long.MIN_VALUE;
        int elapsed;
        BlockPos chopping;
        ItemStack axe = ItemStack.EMPTY;
        Path path;
        long routeAfter;
        long stockAfter;
        BlockPos planting;
        int seed = -1;
        int selectedWidth;
        long plantCheckAfter;
        int crackStage = -1;
        long depositAfter;
        boolean depositRoute;

        void suspendCut(Villager worker) {
            if(crackStage>=0&&chopping!=null&&worker.level instanceof ServerLevel level)
                level.destroyBlockProgress(worker.getId(),chopping,-1);
            crackStage=-1;
        }

        void resetCut(Villager worker) {
            suspendCut(worker);
            chopping = null;
            elapsed = 0;
            axe = ItemStack.EMPTY;
        }
    }

    public static AABB bounds(BlockPos origin, int width) {
        int min = -width / 2;
        int max = min + width;
        int vertical=IndustrialRules.LUMBERJACK_HEIGHT/2;
        return new AABB(origin.offset(min,-vertical,min), origin.offset(max,vertical+1,max));
    }

    public static int cutTicks(ItemStack tool) {
        if (!(tool.getItem() instanceof AxeItem axe)) return 0;
        var tier = axe.getTier();
        if (tier == Tiers.WOOD) return 300;
        if (tier == Tiers.STONE) return 250;
        if (tier == Tiers.IRON) return 200;
        if (tier == Tiers.GOLD) return 150;
        if (tier == IndustrialTools.EMERALD) return 100;
        if (tier == Tiers.DIAMOND) return 50;
        if (tier == Tiers.NETHERITE) return 40;
        return 0;
    }

    public static boolean sapling(ItemStack item) {
        return item.getItem() instanceof BlockItem && (item.is(ItemTags.SAPLINGS) || item.is(Items.MANGROVE_PROPAGULE));
    }

    static void trace(Villager worker, String state) {
        if (TRACE && !state.equals(LAST_STATE.put(worker, state))) {
            LOGGER.info("Lumberjack worker={} {}", worker.getUUID(), state);
        }
    }

    public static void pause(Villager worker) {
        var session = SESSIONS.remove(worker);
        if (session != null) {
            stop(worker,session);
            session.resetCut(worker);
            FarmerLogistics.release(worker);
        }
        IndustrialWork.restoreHand(worker);
    }

    static void unloaded(Villager worker){
        if(SESSIONS.containsKey(worker))pause(worker);
        else FarmerLogistics.release(worker);
    }

    static boolean tick(ServerLevel level, EmploymentData.Station station, Villager worker) {
        if (worker.isNoAi() || worker.isTrading() || worker.isPassenger() || worker.isInWater()) {
            trace(worker, "blocked: no AI, trading, riding or in water");
            pause(worker);
            return false;
        }
        var session = SESSIONS.computeIfAbsent(worker, key -> new Session());
        if (session.lastTick == level.getGameTime()) return session.chopping != null;
        if (session.lastTick != level.getGameTime() - 1) session.resetCut(worker);
        session.lastTick = level.getGameTime();
        AABB area = IndustrialWork.area(level, station);
        int width=(int)area.getXsize();
        if (session.selectedWidth!=width) {
            session.selectedWidth=width;
            session.scanAfter=0;
            session.planting=null;
            stop(worker,session);
            session.resetCut(worker);
        }
        var axe = station.getItem(EmploymentData.TOOL);
        if(level.getGameTime()>=session.scanAfter&&cutTicks(axe)==0
                &&(station.industry.contains(JOB)||IndustrialWork.find(station,LumberjackWork::sapling,0,EmploymentData.INPUT_A+1)<0)){
            IndustrialWork.alert(level,station,"lumberjack_axe","Falta um machado válido para cortar árvores.");
        }
        var job = station.industry.getCompound(JOB);
        if(job.hasUUID("worker")&&!worker.getUUID().equals(job.getUUID("worker"))){
            clear(station,worker,session);
            job=new CompoundTag();
        }
        if (!job.isEmpty() && !jobLoaded(level,job)) {
            stop(worker,session);
            session.resetCut(worker);
            IndustrialWork.displayTool(worker,ItemStack.EMPTY);
            return false;
        }
        if(!job.isEmpty()&&!rootsInArea(area,job)){
            stop(worker,session);
            session.resetCut(worker);
            IndustrialWork.displayTool(worker,ItemStack.EMPTY);
            return false;
        }
        if (!job.isEmpty() && level.getGameTime()%20==0 && !validJob(level, area, job)) {
            clear(station, worker, session);
            job = new CompoundTag();
        }
        boolean activeCut = !job.isEmpty() && (!job.contains("started") || job.getBoolean("started")) && cutTicks(axe) > 0;
        if (activeCut) {
            // A full inventory can block the next cut. Finish that delivery before resuming.
            if (session.depositRoute || level.getGameTime() < session.stockAfter) {
                session.depositRoute = FarmerLogistics.deliverCollected(level, station, worker);
                if (session.depositRoute || level.getGameTime() < session.stockAfter) {
                    IndustrialWork.displayTool(worker, ItemStack.EMPTY);
                    return true;
                }
            }
            if (chop(level, station, worker, session, area, job, axe)) return true;
        }
        if(FarmerLogistics.collect(level,station,worker)){
            session.suspendCut(worker);
            session.path=null;
            IndustrialWork.displayTool(worker,ItemStack.EMPTY);
            return true;
        }
        if(session.depositRoute||level.getGameTime()>=session.depositAfter){
            session.depositRoute=FarmerLogistics.deliverCollected(level,station,worker);
            session.depositAfter=level.getGameTime()+40;
            if(session.depositRoute){
                session.suspendCut(worker);
                session.path=null;
                IndustrialWork.displayTool(worker,ItemStack.EMPTY);
                return true;
            }
        }
        if (!job.isEmpty() && cutTicks(axe) > 0) return !activeCut && chop(level, station, worker, session, area, job, axe);
        session.resetCut(worker);
        if (level.getGameTime() >= session.scanAfter) {
            if (TRACE) LOGGER.info("Lumberjack worker={} on duty, axe={}, cut ticks={}, bounds={}",
                    worker.getUUID(), axe, cutTicks(axe), area);
            session.scanAfter = level.getGameTime() + SEARCH_INTERVAL;
            if (cutTicks(axe) > 0 && job.isEmpty()
                    && level.getGameTime() >= station.industry.getLong("lumberjack_next_tree")) {
                var tree = selectTree(level, station, worker, session, area);
                if (tree != null) {
                    job = write(level,tree,worker);
                    job.putBoolean("started", false);
                    station.industry.put(JOB, job);
                    station.setChanged();
                    session.planting = null;
                    return chop(level, station, worker, session, area, job, axe);
                }
            }
            if (session.planting == null && job.isEmpty()) {
                var planting = findPlanting(level, station, worker, session, area);
                if (planting != null) {
                    session.planting = planting.pos();
                    session.seed = planting.slot();
                }
            }
        }
        if (session.planting != null && job.isEmpty() && plant(level, station, worker, session, area)) return true;
        stop(worker, session);
        IndustrialWork.displayTool(worker, ItemStack.EMPTY);
        return false;
    }

    private static LumberjackTrees.Tree selectTree(ServerLevel level, EmploymentData.Station station, Villager worker, Session session, AABB area) {
        var bases = new ArrayList<BlockPos>();
        int minX=(int)area.minX, maxX=(int)area.maxX-1;
        int minY=Math.max(level.getMinBuildHeight(),(int)area.minY);
        int maxY=Math.min(level.getMaxBuildHeight(),(int)area.maxY)-1;
        int minZ=(int)area.minZ, maxZ=(int)area.maxZ-1;
        for(int cx=minX>>4;cx<=maxX>>4;cx++)for(int cz=minZ>>4;cz<=maxZ>>4;cz++){
            var chunk=level.getChunkSource().getChunkNow(cx,cz);
            if(chunk==null)continue;
            int x0=cx<<4, z0=cz<<4;
            for(int sy=minY>>4;sy<=maxY>>4;sy++){
                int y0=sy<<4;
                var section=chunk.getSection(level.getSectionIndex(y0));
                if(!section.maybeHas(state->state.is(BlockTags.LOGS)))continue;
                for(int x=Math.max(minX,x0);x<=Math.min(maxX,x0+15);x++)
                    for(int y=Math.max(minY,y0);y<=Math.min(maxY,y0+15);y++)
                        for(int z=Math.max(minZ,z0);z<=Math.min(maxZ,z0+15);z++){
                            if(!section.getBlockState(x&15,y&15,z&15).is(BlockTags.LOGS))continue;
                            var pos=new BlockPos(x,y,z);
                            if(LumberjackTrees.rootSupport(level.getBlockState(pos.below())))bases.add(pos);
                        }
            }
        }
        if (TRACE) LOGGER.info("Lumberjack worker={} level={} candidate roots={}", worker.getUUID(), IndustrialRules.level(level,station), bases.size());
        bases.sort(Comparator.<BlockPos>comparingDouble(pos -> Vec3.atBottomCenterOf(pos).distanceToSqr(worker.position())).thenComparingInt(BlockPos::getY));
        for (var base : bases) {
            var tree = LumberjackTrees.identify(level, base, area, station.pos);
            if (tree == null) continue;
            if (TRACE) LOGGER.info("Lumberjack worker={} found tree at {} with {} logs",
                    worker.getUUID(), base, tree.logs().size());
            if (overlapsJob(level, station, tree)) continue;
            var first = tree.logs().get(0).pos();
            if (!IndustrialWork.allowed(level, station, worker, first, null, "lumberjack_select")) continue;
            session.routeAfter = 0;
            if (chopReachable(level, worker, area, first, base, station.pos) || route(level, worker, session, area, first, base, true)) return tree;
            if (TRACE) LOGGER.info("Lumberjack worker={} cannot reach tree at {}", worker.getUUID(), base);
        }
        return null;
    }

    private static boolean overlapsJob(ServerLevel level, EmploymentData.Station station, LumberjackTrees.Tree tree) {
        for (var other : EmploymentData.get(level).stations()) {
            if (other == station || !other.active || other.worker == null || other.profession != Profession.LUMBERJACK
                    || !other.dimension.equals(station.dimension) || !other.industry.contains(JOB)) continue;
            var logs = other.industry.getCompound(JOB).getList("logs", Tag.TAG_COMPOUND);
            for (var entry : logs) {
                long pos = ((CompoundTag) entry).getLong("pos");
                if (tree.logs().stream().anyMatch(log -> log.pos().asLong() == pos)) return true;
            }
        }
        return false;
    }

    private static CompoundTag write(ServerLevel level,LumberjackTrees.Tree tree,Villager worker) {
        var job = new CompoundTag();
        job.putUUID("worker",worker.getUUID());
        job.putLong("base", tree.base().asLong());
        var roots = tree.logs().stream().map(LumberjackTrees.Log::pos)
                .filter(pos -> pos.getY()==tree.base().getY()).mapToLong(BlockPos::asLong).toArray();
        job.putLongArray("roots",roots);
        job.putString("species", tree.logs().get(0).state().getString("Name"));
        var logs = new ListTag();
        for (var log : tree.logs()) {
            var row = new CompoundTag();
            row.putLong("pos", log.pos().asLong());
            row.put("state", log.state());
            logs.add(row);
        }
        job.put("logs", logs);
        job.putLongArray("canopy", LumberjackCanopy.snapshot(level, tree.logs()));
        return job;
    }

    private static boolean jobLoaded(ServerLevel level,CompoundTag job) {
        for (var row : job.getList("logs",Tag.TAG_COMPOUND)) {
            if (!level.hasChunkAt(BlockPos.of(((CompoundTag)row).getLong("pos")))) return false;
        }
        return true;
    }

    private static boolean rootsInArea(AABB area, CompoundTag job){
        if(!area.contains(Vec3.atBottomCenterOf(BlockPos.of(job.getLong("base")))))return false;
        for(long root:job.getLongArray("roots"))
            if(!area.contains(Vec3.atBottomCenterOf(BlockPos.of(root))))return false;
        return true;
    }

    private static boolean validJob(ServerLevel level, AABB area, CompoundTag job) {
        BlockPos base = BlockPos.of(job.getLong("base"));
        if (!area.contains(Vec3.atBottomCenterOf(base)) || !LumberjackTrees.loaded(level, base)) return false;
        for (long key : job.getLongArray("roots")) {
            var root = BlockPos.of(key);
            if (!area.contains(Vec3.atBottomCenterOf(root)) || !LumberjackTrees.loaded(level,root)
                    || !LumberjackTrees.rootSupport(level.getBlockState(root.below()))) return false;
        }
        var logs = job.getList("logs", Tag.TAG_COMPOUND);
        if (logs.isEmpty() || logs.size() > 160) return false;
        for (var row : logs) {
            var entry = (CompoundTag) row;
            BlockPos pos = BlockPos.of(entry.getLong("pos"));
            if (!LumberjackTrees.loaded(level, pos) || LumberjackTrees.placed(level, pos)
                    || !entry.getCompound("state").equals(NbtUtils.writeBlockState(level.getBlockState(pos)))) return false;
        }
        return true;
    }

    private static boolean chop(ServerLevel level, EmploymentData.Station station, Villager worker, Session session,
                                AABB area, CompoundTag job, ItemStack axe) {
        var logs = job.getList("logs", Tag.TAG_COMPOUND);
        var first = logs.getCompound(0);
        BlockPos target = BlockPos.of(first.getLong("pos")), base = BlockPos.of(job.getLong("base"));
        if (!LumberjackTrees.loaded(level,target) || !area.contains(Vec3.atBottomCenterOf(base))
                || LumberjackTrees.placed(level,target)
                || !first.getCompound("state").equals(NbtUtils.writeBlockState(level.getBlockState(target)))) {
            clear(station,worker,session);
            return false;
        }
        if (!chopReachable(level, worker, area, target, base, station.pos)) {
            session.suspendCut(worker);
            IndustrialWork.displayTool(worker,axe);
            if (route(level, worker, session, area, target, base, true)) {
                if (TRACE) trace(worker, "walking to log " + target);
                return true;
            }
            if (TRACE) trace(worker, "no clear path to log " + target);
            stop(worker,session);
            IndustrialWork.displayTool(worker,ItemStack.EMPTY);
            return false;
        }
        stop(worker, session);
        if(level.getGameTime()<session.stockAfter){
            IndustrialWork.displayTool(worker,ItemStack.EMPTY);
            return true;
        }
        var displayed=axe.copy();
        displayed.getOrCreateTag().putBoolean(CHOPPING_AXE,true);
        IndustrialWork.displayTool(worker,displayed);
        var aim=Vec3.atCenterOf(target);
        worker.getLookControl().setLookAt(aim.x,aim.y,aim.z);
        double dx=aim.x-worker.getX(),dz=aim.z-worker.getZ();
        if(dx*dx+dz*dz>.01)
            worker.setYBodyRot((float)(net.minecraft.util.Mth.atan2(dz,dx)*180/Math.PI)-90);
        if (!target.equals(session.chopping) || !ItemStack.matches(axe,session.axe)) {
            if (!job.getBoolean("started")) {
                job.putBoolean("started", true);
                station.setChanged();
            }
            session.resetCut(worker);
            session.chopping = target;
            session.axe = axe.copy();
            session.elapsed = 0;
            if (TRACE) trace(worker, "cutting log " + target + " for " + cutTicks(axe) + " ticks");
            worker.swing(InteractionHand.MAIN_HAND,true);
            crack(level,worker,session,0);
            return true;
        }
        int duration=cutTicks(axe);
        if(session.crackStage<0)worker.swing(InteractionHand.MAIN_HAND,true);
        session.elapsed=Math.min(duration,session.elapsed+1);
        crack(level,worker,session,Math.min(9,session.elapsed*10/duration));
        if (session.elapsed < duration) {
            if (session.elapsed % 16 == 0) worker.swing(InteractionHand.MAIN_HAND,true);
            return true;
        }
        var old = level.getBlockState(target);
        var drops = Block.getDrops(old, level, target, null, worker, axe);
        if (!IndustrialWork.outputs(station,drops,false)) {
            session.stockAfter=level.getGameTime()+20;
            session.suspendCut(worker);
            IndustrialWork.displayTool(worker,ItemStack.EMPTY);
            IndustrialWork.alert(level,station,"lumberjack_stock","Inventário cheio. Os troncos permanecem na árvore.");
            return true;
        }
        if (!level.mayInteract(IndustrialWork.actor(level,station,worker),target)
                || !IndustrialWork.breakAllowed(level,station,worker,target) || level.getBlockState(target) != old
                || !validJob(level,area,job) || !level.destroyBlock(target,false,worker)) {
            clear(station,worker,session);
            return false;
        }
        if(level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS))for(var stack:drops){
            if(stack.isEmpty())continue;
            var drop=new ItemEntity(level,target.getX()+.5,target.getY()+.5,target.getZ()+.5,stack);
            drop.setDeltaMovement(Vec3.ZERO);
            drop.setDefaultPickUpDelay();
            drop.getPersistentData().putUUID(FarmerLogistics.HARVEST_STATION,station.id);
            level.addFreshEntity(drop);
        }
        FarmerLogistics.rescan(worker);
        axe.hurtAndBreak(1,worker,v -> v.broadcastBreakEvent(InteractionHand.MAIN_HAND));
        if (TRACE) LOGGER.info("Lumberjack worker={} broke log {} after {} ticks",worker.getUUID(),target,session.elapsed);
        station.industry.putLong("lumberjack_cut_count",station.industry.getLong("lumberjack_cut_count")+1);
        station.industry.putString("lumberjack_species",job.getString("species"));
        IndustrialRules.progress(level,station);
        logs.remove(0);
        session.resetCut(worker);
        if (logs.isEmpty()) {
            LumberjackCanopy.finish(level, station, worker, job.getLongArray("canopy"));
            station.industry.putLong("lumberjack_next_tree", level.getGameTime() + 100);
            clear(station,worker,session);
        }
        station.setChanged();
        return true;
    }

    private static void crack(ServerLevel level,Villager worker,Session session,int stage){
        if(stage==session.crackStage&&session.elapsed%20!=0)return;
        level.destroyBlockProgress(worker.getId(),session.chopping,stage);
        session.crackStage=stage;
    }

    private static void clear(EmploymentData.Station station, Villager worker, Session session) {
        station.industry.remove(JOB);
        station.setChanged();
        session.resetCut(worker);
        session.scanAfter = Math.max(session.scanAfter, worker.level.getGameTime()+10);
        stop(worker,session);
    }

    private static boolean chopReachable(ServerLevel level, Villager worker, AABB area,
                                         BlockPos target, BlockPos base, BlockPos post) {
        if (reachable(level, worker, area, target, base, true)) return true;
        // A short oak may grow around a worker whose idle walk ended on its own work post.
        // From the top of that block the worker can reach down to the adjoining root.
        return target.equals(base) && base.getY() == post.getY() && base.distManhattan(post) == 1
                && worker.isOnGround() && worker.blockPosition().equals(post.above()) && area.contains(worker.position());
    }

    private static boolean reachable(ServerLevel level, Villager worker, AABB area, BlockPos target, BlockPos base, boolean choppingTree) {
        if (!area.contains(worker.position())) return false;
        if (Math.abs(worker.getY()-base.getY()) > 2) return false;
        // Upper strokes extend only along the saved tree, with clear access from its base.
        return withinReach(worker.getX(), worker.getZ(), target, choppingTree)
                && clearStroke(level, worker, worker.getEyePosition(), target, choppingTree);
    }

    private static boolean withinReach(double x, double z, BlockPos target, boolean choppingTree) {
        double dx = Math.abs(x - target.getX() - .5), dz = Math.abs(z - target.getZ() - .5);
        if (choppingTree) {
            // Measure to the trunk face so a path ending slightly off its cell center can still work.
            dx = Math.max(0, dx - .5);
            dz = Math.max(0, dz - .5);
        }
        return dx * dx + dz * dz <= WORK_REACH * WORK_REACH;
    }

    private static boolean clearStroke(ServerLevel level, Villager worker, Vec3 eyes, BlockPos target, boolean choppingTree) {
        var center = Vec3.atCenterOf(target);
        if (clearTo(level, worker, eyes, target, center, choppingTree)) return true;
        if (!choppingTree) return false;
        // The post or an upper log may hide the center while an exposed edge can be reached.
        double x = net.minecraft.util.Mth.clamp(eyes.x, target.getX() + .0625, target.getX() + .9375);
        double z = net.minecraft.util.Mth.clamp(eyes.z, target.getZ() + .0625, target.getZ() + .9375);
        var xFace = new Vec3(x, center.y, center.z);
        var zFace = new Vec3(center.x, center.y, z);
        return clearChopFace(level, worker, eyes, target, xFace)
                || clearChopFace(level, worker, eyes, target, zFace)
                || clearTo(level, worker, eyes, target, center.add(0, -.4375, 0), true)
                || clearTo(level, worker, eyes, target, center.add(0, .4375, 0), true);
    }

    private static boolean clearChopFace(ServerLevel level, Villager worker, Vec3 eyes, BlockPos target, Vec3 face) {
        return clearTo(level, worker, eyes, target, face, true)
                || clearTo(level, worker, eyes, target, face.add(0, -.4375, 0), true)
                || clearTo(level, worker, eyes, target, face.add(0, .4375, 0), true);
    }

    private static boolean clearTo(ServerLevel level, Villager worker, Vec3 eyes, BlockPos target, Vec3 end, boolean choppingTree) {
        var hit = level.clip(new ClipContext(eyes, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, worker) {
            @Override
            public VoxelShape getBlockShape(BlockState state, BlockGetter world, BlockPos pos) {
                // A strike can reach through natural foliage; the navigation still uses its solid collision shape.
                if (choppingTree && state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT)) {
                    return Shapes.empty();
                }
                return super.getBlockShape(state, world, pos);
            }
        });
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(target);
    }

    private static boolean route(ServerLevel level, Villager worker, Session session, AABB area, BlockPos target, BlockPos base, boolean choppingTree) {
        return route(level, worker, session, area, target, base, choppingTree, List.of());
    }

    private static boolean route(ServerLevel level, Villager worker, Session session, AABB area, BlockPos target,
                                 BlockPos base, boolean choppingTree, List<BlockPos> plantingFootprint) {
        if (session.path != null && worker.getNavigation().getPath() == session.path && !worker.getNavigation().isDone()
                && session.path.getTarget().distSqr(new BlockPos(target.getX(),base.getY(),target.getZ())) <= 12) return true;
        if (level.getGameTime() < session.routeAfter) return false;
        var candidates = new ArrayList<BlockPos>();
        int radius = choppingTree ? (int) WORK_REACH : 2;
        for (var cell : BlockPos.betweenClosed(new BlockPos(target.getX()-radius,base.getY()-1,target.getZ()-radius),
                new BlockPos(target.getX()+radius,base.getY()+1,target.getZ()+radius))) {
            int dx = cell.getX() - target.getX(), dz = cell.getZ() - target.getZ();
            var feet = Vec3.atBottomCenterOf(cell);
            if ((dx != 0 || dz != 0) && withinReach(feet.x, feet.z, target, choppingTree) && walkable(level, cell, area)) {
                var eyes = feet.add(0, worker.getEyeHeight(), 0);
                if (clearStroke(level, worker, eyes, target, choppingTree)
                        && plantingFootprint.stream().allMatch(plot -> plot.equals(target) || withinReach(feet.x, feet.z, plot, false)
                            && clearStroke(level, worker, eyes, plot, false))) {
                    candidates.add(cell.immutable());
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(cell -> Vec3.atBottomCenterOf(cell).distanceToSqr(worker.position())));
        for (var cell : candidates) {
            if (cell.distSqr(worker.blockPosition()) == 0) continue;
            var path = worker.getNavigation().createPath(cell,0);
            if (path == null || !path.canReach() || !insidePath(path,area,worker)) continue;
            IndustrialIdle.interrupt(worker);
            if (worker.getNavigation().moveTo(path,.7)) {
                session.path = path;
                return true;
            }
        }
        session.routeAfter = level.getGameTime()+20;
        return false;
    }

    private static boolean insidePath(Path path, AABB area, Villager worker) {
        boolean inside = area.contains(worker.position());
        for (int i=0;i<path.getNodeCount();i++) {
            var node = path.getNode(i);
            if (area.contains(new Vec3(node.x+.5,node.y,node.z+.5))) inside = true;
            else if (inside) return false;
        }
        return inside;
    }

    private static boolean walkable(ServerLevel level, BlockPos pos, AABB area) {
        if (!area.contains(Vec3.atBottomCenterOf(pos)) || !LumberjackTrees.loaded(level,pos)) return false;
        return level.getFluidState(pos).isEmpty() && level.getBlockState(pos).getCollisionShape(level,pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level,pos.above()).isEmpty()
                && level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP)
                && !level.getBlockState(pos.below()).is(Blocks.MAGMA_BLOCK);
    }

    private static void stop(Villager worker, Session session) {
        if (session.path != null && worker.getNavigation().getPath() == session.path) worker.getNavigation().stop();
        session.path = null;
    }

    private static Iterable<BlockPos> cells(AABB area) {
        return BlockPos.betweenClosed(new BlockPos(area.minX,area.minY,area.minZ),
                new BlockPos(area.maxX-1,area.maxY-1,area.maxZ-1));
    }

    private record Planting(BlockPos pos, int slot) {}

    private static Planting findPlanting(ServerLevel level, EmploymentData.Station station, Villager worker, Session session, AABB area) {
        var seeds = new ArrayList<Integer>();
        for (int slot=0;slot<=EmploymentData.INPUT_A;slot++) {
            if (slot!=EmploymentData.TOOL && sapling(station.getItem(slot))) seeds.add(slot);
        }
        if (seeds.isEmpty()) return null;
        var species = station.industry.getString("lumberjack_species");
        seeds.sort(Comparator.comparingInt(slot -> preferred(species,station.getItem(slot))?0:1));
        var protectedAreas=protectedAreas(level,station,area);
        var candidates = new ArrayList<BlockPos>();
        for (var pos : cells(area)) {
            if (LumberjackTrees.loaded(level,pos) && low(level.getBlockState(pos))
                    && (level.getBlockState(pos.below()).is(BlockTags.DIRT) || level.getBlockState(pos.below()).is(Blocks.MUD))) candidates.add(pos.immutable());
        }
        candidates.sort(Comparator.comparingDouble(pos -> Vec3.atBottomCenterOf(pos).distanceToSqr(worker.position())));
        for (int slot : seeds) {
            var stack = station.getItem(slot);
            for (var pos : candidates) {
                if (!plantable(level,area,protectedAreas,pos,stack)) continue;
                session.routeAfter=0;
                var footprint = plantingCells(pos, stack);
                if (canPlantFromHere(level, worker, area, pos, footprint)
                        || route(level, worker, session, area, pos, pos, false, footprint)) {
                    return new Planting(pos,slot);
                }
            }
        }
        return null;
    }

    private static boolean preferred(String species, ItemStack seed) {
        var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(seed.getItem());
        return item!=null && (species.equals(item.toString().replace("_sapling","_log"))
                || seed.is(Items.MANGROVE_PROPAGULE) && species.equals("minecraft:mangrove_log"));
    }

    private static List<BlockPos> plantingCells(BlockPos pos,ItemStack seed) {
        return seed.is(Items.DARK_OAK_SAPLING)?List.of(pos,pos.east(),pos.south(),pos.south().east()):List.of(pos);
    }

    static boolean plantable(ServerLevel level, AABB area, List<AABB> protectedAreas, BlockPos pos, ItemStack seed) {
        if (!sapling(seed)) return false;
        var footprint = plantingCells(pos,seed);
        if (seed.getCount()<footprint.size()) return false;
        var plant = ((BlockItem)seed.getItem()).getBlock().defaultBlockState();
        int clearance=seed.is(Items.JUNGLE_SAPLING)||seed.is(Items.SPRUCE_SAPLING)||seed.is(Items.MANGROVE_PROPAGULE)?15:9;
        for (var root : footprint) {
            if (!plantingMargins(area,protectedAreas,root) || !LumberjackTrees.loaded(level,root)
                    || !level.getWorldBorder().isWithinBounds(root) || !low(level.getBlockState(root))
                    || !level.getFluidState(root).isEmpty() || !validSoil(level.getBlockState(root.below()),seed) || !plant.canSurvive(level,root) || level.getMaxLocalRawBrightness(root)<9) return false;
            for (int x=-6;x<=6;x++) for (int z=-6;z<=6;z++) {
                if (x*x+z*z>=49) continue;
                for (int y=0;y<clearance;y++) {
                    var test = root.offset(x,y,z);
                    if (!LumberjackTrees.loaded(level,test) || !level.getFluidState(test).isEmpty()
                            || !low(level.getBlockState(test))) return false;
                }
            }
        }
        return true;
    }

    private static List<AABB> protectedAreas(ServerLevel level,EmploymentData.Station station,AABB area){
        var nearby=area.inflate(5);
        var result=new ArrayList<AABB>();
        for(var home:ResidenceData.get(level).homes()){
            if(!home.dimension.equals(level.dimension()))continue;
            int radius=home.size/2;
            var bounds=new AABB(home.pos.offset(-radius,1,-radius),home.pos.offset(radius+1,home.size+1,radius+1));
            if(nearby.intersects(bounds))result.add(bounds);
        }
        for(var other:EmploymentData.get(level).stations()){
            if(other==station||!other.active||!other.dimension.equals(level.dimension()))continue;
            AABB bounds=IndustrialWork.industrial(other)?IndustrialWork.area(level,other):
                other.profession==Profession.SOLDIER?br.com.kraftcity.military.Military.areaBounds(other.pos):null;
            if(bounds!=null&&nearby.intersects(bounds))result.add(bounds);
            if(other.worker!=null)WorkforceData.get(level).worker(other.worker).flatMap(WorkerRecord::area)
                .filter(work->work.dimension().equals(level.dimension()))
                .map(work->new AABB(work.min(),work.max().offset(1,1,1)))
                .filter(nearby::intersects).ifPresent(result::add);
        }
        var construction=BuilderData.get(level);
        for(var job:construction.jobs.values()){
            if(job.cancelled||!job.dimension.equals(level.dimension()))continue;
            var paper=construction.papers.get(job.paper);
            var blueprint=paper==null?null:construction.library.get(paper.schema);
            if(blueprint==null||job.done.cardinality()>=blueprint.cells.size())continue;
            var bounds=new AABB(job.origin,job.origin.offset(blueprint.transformedSize(job.rotation)));
            if(nearby.intersects(bounds))result.add(bounds);
        }
        return result;
    }

    static boolean plantingMargins(AABB area,List<AABB> protectedAreas,BlockPos root){
        var point=Vec3.atCenterOf(root);
        if(!area.contains(point)||point.x<area.minX+3||point.x>area.maxX-3
                ||point.z<area.minZ+3||point.z>area.maxZ-3)return false;
        // Euclidean distance to the nearest point of each protected volume.
        for(var bounds:protectedAreas){
            double dx=Math.max(0,Math.max(bounds.minX-point.x,point.x-bounds.maxX));
            double dy=Math.max(0,Math.max(bounds.minY-point.y,point.y-bounds.maxY));
            double dz=Math.max(0,Math.max(bounds.minZ-point.z,point.z-bounds.maxZ));
            if(dx*dx+dy*dy+dz*dz<25)return false;
        }
        return true;
    }

    private static boolean validSoil(BlockState state, ItemStack seed) {
        return state.is(BlockTags.DIRT) || seed.is(Items.MANGROVE_PROPAGULE) && state.is(Blocks.MUD);
    }

    private static boolean low(BlockState state) {
        return state.isAir() || state.is(Blocks.GRASS) || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN) || state.is(Blocks.LARGE_FERN)
                || state.getBlock() instanceof FlowerBlock && !state.is(Blocks.WITHER_ROSE)
                || state.getBlock() instanceof TallFlowerBlock;
    }

    private static boolean canPlantFromHere(ServerLevel level, Villager worker, AABB area, BlockPos root, List<BlockPos> footprint) {
        return footprint.stream().allMatch(cell -> reachable(level, worker, area, cell, root, false));
    }

    private static boolean plant(ServerLevel level, EmploymentData.Station station, Villager worker, Session session, AABB area) {
        var seed = station.getItem(session.seed);
        var pos = session.planting;
        var cells = plantingCells(pos, seed);
        boolean inReach = canPlantFromHere(level, worker, area, pos, cells);
        if (!inReach && level.getGameTime()<session.plantCheckAfter && session.path!=null
                && worker.getNavigation().getPath()==session.path && !worker.getNavigation().isDone()) return true;
        session.plantCheckAfter=level.getGameTime()+10;
        var protectedAreas=protectedAreas(level,station,area);
        if (plantable(level,area,protectedAreas,pos,seed)) {
            ItemStack display=seed.copy();
            display.setCount(1);
            IndustrialWork.displayTool(worker,display);
            if (!inReach) {
                if (route(level, worker, session, area, pos, pos, false, cells)) return true;
            } else {
                var block = ((BlockItem)seed.getItem()).getBlock().defaultBlockState();
                if (cells.stream().allMatch(cell -> reachable(level,worker,area,cell,pos,false)
                        && level.mayInteract(IndustrialWork.actor(level,station,worker),cell)
                        && IndustrialWork.placeAllowed(level,station,worker,cell,block)) && plantable(level,area,protectedAreas(level,station,area),pos,seed)) {
                    var old = cells.stream().map(level::getBlockState).toList();
                    int placed = 0;
                    for (var cell : cells) {
                        if (!level.setBlock(cell,block,Block.UPDATE_ALL)) break;
                        placed++;
                    }
                    if (placed==cells.size()) {
                        seed.shrink(placed);
                        worker.swing(InteractionHand.MAIN_HAND,true);
                        station.industry.putLong("lumberjack_plant_count",station.industry.getLong("lumberjack_plant_count")+placed);
                        IndustrialRules.progress(level,station);
                    } else {
                        for (int i=0;i<placed;i++) level.setBlock(cells.get(i),old.get(i),Block.UPDATE_ALL);
                    }
                }
            }
        }
        session.planting = null;
        stop(worker,session);
        return false;
    }

    private LumberjackWork() {}
}
