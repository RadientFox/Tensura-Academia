package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.entity.DarkShadow;
import com.radient.tensuraacadamia.regestry.DarkShadowEntities;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import dev.architectury.event.EventResult;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.api.SkillEvents;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.ability.magic.Element;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.event.TensuraSkillEvents;
import io.github.manasmods.tensura.world.TensuraGameRules;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.ArrayList;
import java.util.List;

public final class DarkShadowQuirk extends Skill {
    public static final int COMMAND = 0, ABYSS = 1, CLAWS = 2, ARMS = 3, ANGEL = 4, SABBATH = 5,
            WOMB = 6, RELEASE = 7, RAGNAROK = 8, FLEETING = 9, BALDUR = 10;
    public static final int[] UNLOCK = {0, 10, 20, 30, 40, 50, 50, 60, 75, 90, 100};
    public static final int[] COST = {0, 1000, 5000, 7500, 10000, 5000, 10000, 20000, 30000, 25000, 10000};
    public static final int[] COOLDOWN = {0, 0, 10, 15, 0, 5, 30, 120, 120, 45, 300};
    public static final String SHADOW = "DarkShadowEntity", SNAPSHOT = "DarkShadowSnapshot", FUSED = "DarkShadowFused",
            DEATH_READY = "DarkShadowDeathReady";
    public DarkShadowQuirk() { super(SkillType.UNIQUE); }
    @Override public int getMaxMastery() { return 10000; }
    @Override public ResourceLocation getSkillIcon() { return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/darkshadowicon.png"); }
    @Override public double getDefaultAcquiringMagiculeCost() { return 0; }
    @Override public boolean checkAcquiringRequirement(Player player, double cost) { return false; }
    @Override public boolean canBeToggled(ManasSkillInstance instance, LivingEntity owner) { return true; }
    @Override public double getAuraCost(LivingEntity owner, ManasSkillInstance instance, int mode) {
        var modes = availableModes(instance);
        if (mode < 0 || mode >= modes.size()) return 0;
        int ability = modes.get(mode);
        DarkShadow shadow = shadow(owner);
        return ability == ABYSS && fused(instance) || ability == ARMS && shadow != null && shadow.isGrabbing() ? 0 : COST[ability];
    }
    @Override public boolean canIgnoreCoolDown(ManasSkillInstance instance, LivingEntity owner, int mode) {
        var modes = availableModes(instance); DarkShadow shadow = shadow(owner);
        return mode >= 0 && mode < modes.size() && modes.get(mode) == ARMS && shadow != null && shadow.isGrabbing();
    }
    public static int percentage(ManasSkillInstance instance) { return Math.clamp((int) (instance.getMastery() / 100), 1, 100); }
    public static boolean lowLight(LivingEntity owner) { return owner.level().getMaxLocalRawBrightness(owner.blockPosition()) <= 7; }
    public static boolean fused(ManasSkillInstance instance) { return instance.getOrCreateTag().getBoolean(FUSED); }
    public List<Integer> availableModes(ManasSkillInstance instance) {
        boolean fusion = fused(instance);
        List<Integer> modes = new ArrayList<>();
        for (int mode = 0; mode < UNLOCK.length; mode++) {
            boolean abyssMove = mode >= CLAWS && mode <= SABBATH || mode >= FLEETING;
            if ((mode == ABYSS || fusion == abyssMove) && instance.getMastery() >= UNLOCK[mode] * 100) modes.add(mode);
        }
        return modes;
    }
    @Override public int getModes(ManasSkillInstance instance) { return availableModes(instance).size(); }
    @Override public int nextMode(LivingEntity owner, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), getModes(instance));
    }
    @Override public String getModeId(ManasSkillInstance instance, int mode) {
        var modes = availableModes(instance);
        mode = mode >= 0 && mode < modes.size() ? modes.get(mode) : COMMAND;
        return "dark_shadow." + switch (mode) {
            case ABYSS -> "black_abyss"; case CLAWS -> "piercing_twilight_claws"; case ARMS -> "covert_black_ops_arms";
            case ANGEL -> "black_fallen_angel"; case SABBATH -> "sabbath"; case WOMB -> "womb";
            case RELEASE -> "total_release"; case RAGNAROK -> "ragnarok"; case FLEETING -> "fleeting_blow";
            case BALDUR -> "light_of_baldur"; default -> "shadow_command";
        };
    }
    public static ManasSkillInstance instance(LivingEntity owner) {
        return SkillAPI.getSkillsFrom(owner).getSkill(QuirkSkills.DARK_SHADOW.get()).orElse(null);
    }
    public static DarkShadow shadow(LivingEntity owner) {
        ManasSkillInstance instance = instance(owner);
        if (instance == null || !instance.getOrCreateTag().hasUUID(SHADOW)) return null;
        Entity entity = owner.level() instanceof ServerLevel server ? server.getEntity(instance.getOrCreateTag().getUUID(SHADOW))
                : owner.level().getEntity(instance.getOrCreateTag().getInt("DarkShadowRuntimeId"));
        return entity instanceof DarkShadow shadow && shadow.isAlive() && (owner.level().isClientSide ? shadow.creator() == owner
                : owner.getUUID().equals(shadow.creatorId())) ? shadow : null;
    }
    @Override public void onToggleOn(ManasSkillInstance instance, LivingEntity owner) {
        if (!(owner.level() instanceof ServerLevel server)) return;
        if (instance.getOrCreateTag().getLong(DEATH_READY) > server.getGameTime()) {
            instance.setToggled(false); message(owner, "recovering"); instance.markDirty(); return;
        }
        if (shadow(owner) == null) summon(instance, owner);
    }
    private static void summon(ManasSkillInstance instance, LivingEntity owner) {
        ServerLevel server = (ServerLevel) owner.level();
        DarkShadow shadow = DarkShadowEntities.SHADOW.get().create(server);
        if (shadow == null) return;
        shadow.configure(owner, instance);
        if (instance.getOrCreateTag().contains(SNAPSHOT)) shadow.restoreSession(instance.getOrCreateTag().getCompound(SNAPSHOT));
        shadow.setPos(owner.position().add(owner.getLookAngle().multiply(-1, 0, -1)));
        if (!server.addFreshEntity(shadow)) { instance.setToggled(false); instance.markDirty(); return; }
        // Tensura initializes new mobs' energy attributes when they join the level.
        shadow.updateStats(owner, instance);
        TensuraStorages.getExistenceFrom(shadow).setSkippingEPDrop(true);
        instance.getOrCreateTag().putUUID(SHADOW, shadow.getUUID());
        instance.getOrCreateTag().putInt("DarkShadowRuntimeId", shadow.getId());
        instance.getOrCreateTag().remove(SNAPSHOT);
        instance.markDirty();
    }
    @Override public void onToggleOff(ManasSkillInstance instance, LivingEntity owner) {
        DarkShadow shadow = shadow(owner);
        if (shadow != null && shadow.berserk()) {
            instance.setToggled(true); instance.markDirty(); message(owner, "berserk_locked"); return;
        }
        dismiss(instance, owner, false);
    }
    @Override public void onForgetSkill(ManasSkillInstance instance, LivingEntity owner) {
        DarkShadow shadow = shadow(owner);
        if (shadow == null || !shadow.berserk()) dismiss(instance, owner, false);
    }
    public static void dismiss(ManasSkillInstance instance, LivingEntity owner, boolean save) {
        DarkShadow shadow = shadow(owner);
        if (shadow != null) {
            instance.getOrCreateTag().put(SNAPSHOT, shadow.session());
            shadow.releaseCaptive(); shadow.discard();
        }
        instance.getOrCreateTag().remove(SHADOW);
        instance.getOrCreateTag().remove("DarkShadowRuntimeId");
        if (!save) { instance.getOrCreateTag().putBoolean(FUSED, false); instance.setToggled(false); QuirkSkills.DARK_SHADOW.get().resetPool(instance, owner); }
        instance.markDirty();
    }
    public static void shadowDied(DarkShadow shadow, LivingEntity owner) {
        ManasSkillInstance instance = instance(owner);
        if (instance == null) return;
        instance.getOrCreateTag().putLong(DEATH_READY, owner.level().getGameTime() + 2400);
        instance.getOrCreateTag().remove(SHADOW); instance.getOrCreateTag().remove(SNAPSHOT);
        instance.getOrCreateTag().remove("DarkShadowRuntimeId");
        instance.getOrCreateTag().putBoolean(FUSED, false); instance.setToggled(false);
        QuirkSkills.DARK_SHADOW.get().resetPool(instance, owner); instance.markDirty();
        message(owner, "fallen");
    }
    public static void registerSkillEvents() {
        SkillEvents.TOGGLE_SKILL.register((change, owner) -> {
            DarkShadow shadow = shadow(owner);
            if (change.get().getSkill() == QuirkSkills.DARK_SHADOW.get() && shadow != null && shadow.berserk()) {
                message(owner, "berserk_locked"); return EventResult.interruptFalse();
            }
            return EventResult.pass();
        });
    }
    @Override public void onPressed(ManasSkillInstance instance, LivingEntity owner, int key, int mode) {
        if (!(owner.level() instanceof ServerLevel server) || mode < 0 || mode >= getModes(instance)) return;
        mode = availableModes(instance).get(mode);
        DarkShadow shadow = shadow(owner);
        if (shadow == null) { message(owner, "summon_required"); return; }
        if (shadow.berserk()) { message(owner, "berserk_locked"); return; }
        if (mode == COMMAND) {
            shadow.cycleCommand(instance.getMastery() >= 2500);
            message(owner, "command." + shadow.command()); return;
        }
        if (mode == ABYSS && fused(instance)) {
            instance.getOrCreateTag().putBoolean(FUSED, false);
            shadow.setFused(false); shadow.releaseCaptive(); shadow.stopFlight(); resetPool(instance, owner); instance.markDirty(); return;
        }
        if (mode == ARMS && shadow.isGrabbing()) { shadow.slam(); return; }
        if (mode != ABYSS && shadow.attacking() && shadow.action() != COMMAND) { message(owner, "winding_up"); return; }
        if (instance.getOrCreateTag().getLong("DarkShadowReady" + mode) > server.getGameTime()) return;
        if ((mode == RELEASE || mode == RAGNAROK) && !lowLight(owner)) { message(owner, "darkness_required"); return; }
        if (mode == SABBATH && !shadow.flying()) { message(owner, "flight_required"); return; }
        if (mode == ANGEL && shadow.flying() || mode == RAGNAROK && shadow.ragnarok()) return;
        var existence = TensuraStorages.getExistenceFrom(owner);
        if (existence.getAura() < COST[mode]) { message(owner, "need_aura"); return; }
        LivingEntity target = mode == ARMS || mode == WOMB || mode == BALDUR ? lookedTarget(owner, 30) : null;
        if ((mode == ARMS || mode == WOMB || mode == BALDUR) && target == null) { message(owner, "no_target"); return; }
        if ((mode == ARMS || mode == WOMB) && DarkShadow.binding(target) != null) { message(owner, "already_bound"); return; }
        shadow.cancelNormalPunch();
        switch (mode) {
            case ABYSS -> { instance.getOrCreateTag().putBoolean(FUSED, true); shadow.setFused(true); resetPool(instance, owner); }
            case CLAWS, ANGEL, SABBATH, WOMB, FLEETING, BALDUR -> shadow.queueAttack(mode, target);
            case ARMS -> shadow.grab(target);
            case RELEASE -> shadow.totalRelease();
            case RAGNAROK -> shadow.startRagnarok();
            default -> { return; }
        }
        existence.setAura(existence.getAura() - COST[mode]); existence.markDirty();
        if (COOLDOWN[mode] > 0) {
            instance.getOrCreateTag().putLong("DarkShadowReady" + mode, server.getGameTime() + COOLDOWN[mode] * 20L);
        }
        refreshCooldowns(instance, owner);
        instance.markDirty();
    }
    public static void finishAttack(DarkShadow shadow, LivingEntity owner, int mode, Vec3 direction, LivingEntity target) {
        switch (mode) {
            case CLAWS -> strikeBox(shadow, owner.getEyePosition(), direction, shadow.mastered() ? 15 : 10,
                    shadow.mastered() ? 5 : 3, shadow.mastered() ? 5 : 3, 1.5F, mode);
            case ANGEL -> shadow.startFlight();
            case SABBATH -> {
                if (!shadow.flying()) return;
                Vec3 start = owner.getEyePosition();
                owner.move(MoverType.SELF, direction.scale(10)); owner.hurtMarked = true; owner.fallDistance = 0;
                if (owner instanceof net.minecraft.server.level.ServerPlayer player)
                    player.connection.teleport(owner.getX(), owner.getY(), owner.getZ(), owner.getYRot(), owner.getXRot());
                strikeBox(shadow, start, direction, start.distanceTo(owner.getEyePosition()) + 0.5, 4, 4, 2, mode);
            }
            case WOMB -> {
                if (target != null && owner.distanceToSqr(target) <= 30 * 30 && owner.hasLineOfSight(target)
                        && DarkShadow.binding(target) == null) shadow.womb(target);
                else shadow.releaseCaptive();
            }
            case FLEETING -> strikeBox(shadow, owner.getEyePosition(), direction, 10, 2, 2, 2.5F, mode);
            case BALDUR -> {
                if (target == null || !owner.hasLineOfSight(target) || owner.distanceToSqr(target) > 30 * 30) return;
                shadow.totalRelease();
                shadow.hit(target, shadow.attackDamage() * 5, mode);
                strikeBox(shadow, target.getBoundingBox().getCenter().add(direction), direction, 20, 10, 10, 1, mode, target);
            }
            default -> { return; }
        }
        if (mode != ANGEL && mode != WOMB) {
            Vec3 impact = target == null ? owner.getEyePosition().add(direction.scale(mode == SABBATH ? 0 : 7)) : target.getBoundingBox().getCenter();
            ((ServerLevel) owner.level()).sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,
                    impact.x, impact.y, impact.z, mode == BALDUR ? 8 : 3, 1, 0.4, 1, 0);
            owner.level().playSound(null, BlockPos.containing(impact), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP,
                    net.minecraft.sounds.SoundSource.PLAYERS, mode == BALDUR ? 1.5F : 0.9F, mode == FLEETING ? 0.5F : 0.7F);
        }
    }
    public void refreshCooldowns(ManasSkillInstance instance, LivingEntity owner) {
        var cooldowns = new ArrayList<Integer>();
        for (int ability : availableModes(instance)) cooldowns.add((int) Math.max(0,
                (instance.getOrCreateTag().getLong("DarkShadowReady" + ability) - owner.level().getGameTime() + 19) / 20));
        if (!cooldowns.equals(instance.getCooldownList())) { instance.setCoolDownList(cooldowns); instance.markDirty(); }
    }
    private void resetPool(ManasSkillInstance instance, LivingEntity owner) {
        var ability = TensuraStorages.getAbilityFrom(owner);
        for (int preset = 0; preset < ability.getPresets().size(); preset++)
            for (var slot : ability.getAbilitySlots(preset)) if (slot.getSkill() == this) slot.setMode(0);
        ability.markDirty(); refreshCooldowns(instance, owner);
    }
    public static LivingEntity lookedTarget(LivingEntity owner, double range) {
        Vec3 start = owner.getEyePosition(), end = start.add(owner.getLookAngle().scale(range));
        var hit = ProjectileUtil.getEntityHitResult(owner, start, end, new AABB(start, end).inflate(1),
                entity -> entity instanceof LivingEntity living && !(living instanceof DarkShadow) && HomingQuirk.isEnemy(owner, living), range * range);
        return hit != null && owner.hasLineOfSight(hit.getEntity()) ? (LivingEntity) hit.getEntity() : null;
    }
    public static void strikeBox(DarkShadow shadow, Vec3 origin, Vec3 direction, double length, double width, double height,
                                 float multiplier, int mode, LivingEntity... exclude) {
        Vec3 forward = direction.normalize(), right = BeamGeometry.perpendicular(forward), up = forward.cross(right).normalize();
        shadow.showArms(origin.add(forward.scale(length)), mode, 10);
        var bounds = new AABB(origin, origin.add(forward.scale(length))).inflate(Math.max(width, height));
        for (LivingEntity target : shadow.level().getEntitiesOfClass(LivingEntity.class, bounds, shadow::enemy)) {
            if (List.of(exclude).contains(target) || !BeamGeometry.intersects(target.getBoundingBox(), origin, forward, right, up, length, width, height)) continue;
            if (shadow.level().clip(new ClipContext(origin, target.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, shadow)).getType() == HitResult.Type.MISS) shadow.hit(target, shadow.attackDamage() * multiplier, mode);
        }
    }
    public static DamageSource damageSource(DarkShadow shadow, int mode) {
        LivingEntity owner = shadow.creator();
        DamageSource source = TensuraDamageTypes.getIndirectEntityDamageSource(shadow.level(), Element.DARKNESS.getDefaultDamage(),
                mode == COMMAND || mode == RAGNAROK ? shadow : owner, shadow);
        var tensura = (TensuraDamageSource) source;
        tensura.tensura$setAbilityInstance(owner == null ? null : instance(owner));
        tensura.tensura$setSkillType(SkillType.UNIQUE); tensura.tensura$setElement(Element.DARKNESS);
        if (owner != null && instance(owner) != null) tensura.tensura$setAbilityMode(QuirkSkills.DARK_SHADOW.get().availableModes(instance(owner)).indexOf(mode));
        tensura.tensura$setAuraCost(COST[mode]);
        if (mode == BALDUR) tensura.tensura$setResistanceBypassLevel(1);
        return source;
    }
    public static void breakRagnarokBlocks(DarkShadow shadow, Vec3 impact) {
        if (!(shadow.level() instanceof ServerLevel server) || !TensuraGameRules.canSkillGrief(server)) return;
        var instance = shadow.creator() == null ? null : instance(shadow.creator());
        BlockPos center = BlockPos.containing(impact);
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, -2, -2), center.offset(2, 2, 2))) {
            if (!server.hasChunkAt(pos)) continue;
            var state = server.getBlockState(pos);
            if (state.isAir() || state.getDestroySpeed(server, pos) < 0) continue;
            if (!TensuraSkillEvents.SKILL_GRIEF_PRE.invoker().grief(instance, server, shadow.creator(), pos.getX(), pos.getY(), pos.getZ()).isFalse())
                server.destroyBlock(pos, false, shadow.creator());
        }
    }
    public static void message(LivingEntity owner, String key) {
        if (owner instanceof Player player) player.displayClientMessage(Component.translatable("tracadamia.skill.dark_shadow." + key), true);
    }
    @SubscribeEvent public static void ownerTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity owner) || owner instanceof DarkShadow || !(owner.level() instanceof ServerLevel)) return;
        ManasSkillInstance instance = instance(owner);
        if (instance == null || !instance.isToggled() || !owner.isAlive()) return;
        QuirkSkills.DARK_SHADOW.get().refreshCooldowns(instance, owner);
        DarkShadow shadow = shadow(owner);
        if (shadow == null && owner.level().getGameTime() >= instance.getOrCreateTag().getLong(DEATH_READY)) summon(instance, owner);
        else if (shadow != null) shadow.clampTether();
    }
    @SubscribeEvent(priority = EventPriority.LOW) public static void attackBonus(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker instanceof DarkShadow) return;
        ManasSkillInstance instance = instance(attacker);
        if (instance == null) return;
        float amount = event.getAmount();
        DarkShadow shadow = shadow(attacker);
        if (shadow != null && shadow.fused()) amount += shadow.attackDamage() * (shadow.mastered() ? 0.75F : 0.5F);
        if (lowLight(attacker)) amount *= 1.5F;
        event.setAmount(amount);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void redirect(LivingDamageEvent.Pre event) {
        DarkShadow shadow = shadow(event.getEntity());
        if (shadow == null || event.getNewDamage() <= 0 || !(shadow.fused() || shadow.command() == 2 && !shadow.berserk())) return;
        float damage = event.getNewDamage(); event.setNewDamage(0);
        shadow.absorb(damage, event.getSource());
        if (shadow.isAlive() && !shadow.fused() && event.getSource().getEntity() instanceof LivingEntity attacker) shadow.counter(attacker);
    }
    @SubscribeEvent public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim instanceof DarkShadow)) {
            ManasSkillInstance own = instance(victim);
            if (own != null) { dismiss(own, victim, false); own.getOrCreateTag().remove(SNAPSHOT); }
            if (event.getSource().getDirectEntity() instanceof DarkShadow shadow && shadow.creator() != null && victim != shadow.creator()) {
                ManasSkillInstance skill = instance(shadow.creator());
                if (skill != null) skill.addMasteryPoint(shadow.creator());
            }
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        ManasSkillInstance instance = instance(event.getEntity());
        if (instance != null && instance.isToggled()) dismiss(instance, event.getEntity(), true);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        LivingEntity owner = event.getEntity(); ManasSkillInstance instance = instance(owner);
        if (instance == null || !instance.getOrCreateTag().hasUUID(SHADOW)) return;
        for (ServerLevel level : ((ServerLevel) owner.level()).getServer().getAllLevels()) {
            Entity entity = level.getEntity(instance.getOrCreateTag().getUUID(SHADOW));
            if (entity instanceof DarkShadow shadow && owner.getUUID().equals(shadow.creatorId())) {
                instance.getOrCreateTag().put(SNAPSHOT, shadow.session()); shadow.releaseCaptive(); shadow.discard(); break;
            }
        }
        instance.getOrCreateTag().remove(SHADOW); instance.markDirty();
    }
}
