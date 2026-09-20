package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.regestry.ThermalIce;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.EntityEvents;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.registry.particle.TensuraParticleTypes;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.ability.SkillUtils;
import io.github.manasmods.tensura.ability.magic.Element;
import io.github.manasmods.tensura.ability.TensuraSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.ability.skill.resist.ResistSkill;
import io.github.manasmods.tensura.damage.TensuraDamageSource;
import io.github.manasmods.tensura.damage.TensuraDamageTypes;
import io.github.manasmods.tensura.entity.projectile.magic.IceLanceProjectile;
import io.github.manasmods.tensura.entity.magic.spike.IcePillarEntity;
import io.github.manasmods.tensura.registry.effect.TensuraMobEffects;
import io.github.manasmods.tensura.registry.skill.ResistanceSkills;
import io.github.manasmods.tensura.util.ObjectSelectionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** State is kept on the skill instance, so stolen/copied instances retain their own state. */
public final class HalfColdHalfHot extends Skill {
    private static final String STATE = "thermal_state", JET = "jet_kindling", AEGIR = "glacial_aegir";
    private static final String HELD_ACTION = "thermal_held_action";
    private static final String FLASH_START = "thermal_flash_start", FLASH_LAST = "thermal_flash_last";
    private static final String FLAME_TARGET = "thermal_flame_target", FLAME_TICKS = "thermal_flame_ticks";
    private static final int FLASHFIRE_MAX_TICKS = 60, FLASHFIRE_INTERVAL = 5;
    private static final float FLASHFIRE_TICK_DAMAGE = 25;
    private static final String WALL_ID = "tracadamia_thermal_wall", WALL_HITS = "tracadamia_thermal_wall_hits";
    private static final int COLD = 0, HOT = 1, PHOSPHOR = 2;
    private static final String[] ACTIONS = {"switch", "ice_wall", "ice_glide", "ice_spikes",
            "flashfire_fist", "jet_kindling", "wall_of_flames", "pale_blade", "glacial_aegir", "phosphor"};
    private static final java.util.List<JetBurst> JET_BURSTS = new java.util.ArrayList<>();
    private static final java.util.List<PaleBlade> PALE_BLADES = new java.util.ArrayList<>();
    private static final java.util.List<AegirWave> AEGIR_WAVES = new java.util.ArrayList<>();
    private static final java.util.Map<java.util.UUID, ActiveCaster> ACTIVE_CASTERS = new java.util.HashMap<>();
    private static final String READY_PREFIX = "thermal_ready_";

    private record ActiveCaster(LivingEntity owner, ServerLevel level, ManasSkillInstance instance) {}

    public HalfColdHalfHot() { super(SkillType.UNIQUE); }
    public static void registerSkillEvents() {
        NeoForge.EVENT_BUS.register(HalfColdHalfHot.class);
        EntityEvents.LIVING_POST_TICK.register(target -> {
            if (!(target.level() instanceof ServerLevel level) || target.tickCount % 5 != 0 || !target.isAlive()) return;
            if (target.getPersistentData().contains(WALL_HITS)) {
                var hits = target.getPersistentData().getCompound(WALL_HITS);
                for (String key : new java.util.ArrayList<>(hits.getAllKeys()))
                    if (hits.getLong(key) <= level.getGameTime()) hits.remove(key);
                if (hits.isEmpty()) target.getPersistentData().remove(WALL_HITS);
            }
            for (IcePillarEntity pillar : level.getEntitiesOfClass(IcePillarEntity.class,
                    target.getBoundingBox().inflate(0.3), p -> p.getPersistentData().contains(WALL_ID))) {
                if (pillar.getOwner() instanceof LivingEntity owner && owner != target)
                    wallContact(level, owner, target, pillar.getPersistentData().getString(WALL_ID));
            }
        });
    }
    private static void wallContact(ServerLevel level, LivingEntity owner, LivingEntity target, String wallId) {
        var hits = target.getPersistentData().getCompound(WALL_HITS);
        if (hits.getLong(wallId) > level.getGameTime()) return;
        hits.putLong(wallId, level.getGameTime() + 1205);
        target.getPersistentData().put(WALL_HITS, hits);
        if (hurt(level, owner, target, 100, true, false)) {
            target.addEffect(new MobEffectInstance(TensuraMobEffects.getReference(TensuraMobEffects.FROST), 1200, 0));
            target.addEffect(new MobEffectInstance(TensuraMobEffects.getReference(TensuraMobEffects.WEBBED), 1200, 0,
                    true, false, true));
        }
    }
    @Override public int getMaxMastery() { return 5000; }
    @Override public double getDefaultAcquiringMagiculeCost() { return 300000; }
    @Override public double getAuraCost(LivingEntity owner, ManasSkillInstance instance, int mode) { return 0; }
    @Override public ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/halfhothalfcold.png");
    }
    @Override public boolean canTick(ManasSkillInstance instance, LivingEntity owner) { return true; }
    @Override public void onTick(ManasSkillInstance instance, LivingEntity owner) {
        if (!(owner.level() instanceof ServerLevel)) return;
        var tag = instance.getOrCreateTag();
        if (instance.getCooldownList().size() < 9) {
            var cooldowns = new java.util.ArrayList<>(instance.getCooldownList());
            while (cooldowns.size() < 9) cooldowns.add(0);
            instance.setCoolDownList(cooldowns);
            instance.markDirty();
        }
        if (tag.getInt(STATE) == PHOSPHOR && instance.getMastery() < 5000) {
            tag.putInt(STATE, COLD);
            tag.remove(AEGIR);
            tag.remove(JET);
            tag.remove(HELD_ACTION);
            instance.markDirty();
        }
        // Manas calls this callback once per 100 game ticks, not once per tick.
        // Restore persistent toggles here; short attacks use the server tick listener.
        if (!ACTIVE_CASTERS.containsKey(owner.getUUID())) {
            tag.remove(FLAME_TARGET);
            tag.remove(FLAME_TICKS);
        }
        if (state(instance) == PHOSPHOR || tag.getBoolean(JET))
            trackCaster(owner, instance);
    }

    private static void trackCaster(LivingEntity owner, ManasSkillInstance instance) {
        if (owner.level() instanceof ServerLevel level)
            ACTIVE_CASTERS.put(owner.getUUID(), new ActiveCaster(owner, level, instance));
    }

    @SubscribeEvent
    public static void tickActiveCasts(ServerTickEvent.Post event) {
        for (ActiveCaster caster : new java.util.ArrayList<>(ACTIVE_CASTERS.values())) {
            LivingEntity owner = caster.owner();
            ManasSkillInstance instance = caster.instance();
            if (!owner.isAlive() || owner.isRemoved() || owner.level() != caster.level()
                    || SkillAPI.getSkillsFrom(owner).getSkill(instance.getSkill().getRegistryName()).orElse(null) != instance) {
                clearCaster(caster);
                continue;
            }
            tickVisualsAndAttacks(caster.level(), owner, instance);
            java.util.UUID id = owner.getUUID();
            var tag = instance.getOrCreateTag();
            if (state(instance) != PHOSPHOR && !tag.getBoolean(JET)
                    && tag.getInt(HELD_ACTION) != 4
                    && !tag.hasUUID(FLAME_TARGET)
                    && !hasQueuedAttack(id)) ACTIVE_CASTERS.remove(id);
        }
        // Pale Blade is a launched projectile, not a state that depends on its caster continuing
        // to receive a skill callback. Tick it independently so its hitbox remains active for its
        // entire flight (including when the caster changes state immediately after firing).
        tickPaleBlades();
    }

    private static void clearCaster(ActiveCaster caster) {
        java.util.UUID id = caster.owner().getUUID();
        ACTIVE_CASTERS.remove(id);
        JET_BURSTS.removeIf(b -> b.owner.equals(id));
        PALE_BLADES.removeIf(b -> b.owner.equals(id));
        AEGIR_WAVES.removeIf(b -> b.owner.equals(id));
        var tag = caster.instance().getOrCreateTag();
        tag.remove(FLAME_TARGET);
        tag.remove(FLAME_TICKS);
        stopFlashfire(caster.instance());
        caster.instance().markDirty();
    }

    /** Avoids allocating three stream pipelines for every active caster each server tick. */
    private static boolean hasQueuedAttack(java.util.UUID ownerId) {
        for (JetBurst burst : JET_BURSTS) if (burst.owner.equals(ownerId)) return true;
        for (PaleBlade blade : PALE_BLADES) if (blade.owner.equals(ownerId)) return true;
        for (AegirWave wave : AEGIR_WAVES) if (wave.owner.equals(ownerId)) return true;
        return false;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        for (ActiveCaster caster : new java.util.ArrayList<>(ACTIVE_CASTERS.values())) clearCaster(caster);
    }

    private static void tickVisualsAndAttacks(ServerLevel level, LivingEntity owner, ManasSkillInstance instance) {
        int currentState = state(instance);
        var tag = instance.getOrCreateTag();
        if (currentState != COLD && tag.getBoolean(JET) && owner.tickCount % 5 == 0) {
            Vec3 side = horizontal(owner).cross(new Vec3(0, 1, 0)).scale(0.4);
            Vec3 hand = owner.position().add(side).add(0, owner.getBbHeight() * 0.6, 0);
            level.sendParticles(ParticleTypes.FLAME, hand.x, hand.y, hand.z, 3, 0.08, 0.15, 0.08, 0.01);
        }
        if (currentState == PHOSPHOR && owner.tickCount % 3 == 0) phosphorAura(level, owner);
        if (currentState == PHOSPHOR && tag.getBoolean(AEGIR) && owner.tickCount % 2 == 0)
            aegirCharge(level, owner);
        tickFlameConvergence(level, owner, instance);
        tickJetBursts(level, owner);
        tickAegirWaves(level, owner);
        expireFlashfireIfReleased(level, owner, instance);
    }

    private static int state(ManasSkillInstance instance) {
        int state = instance.getOrCreateTag().getInt(STATE);
        return state == HOT ? HOT : state == PHOSPHOR && instance.getMastery() >= 5000 ? PHOSPHOR : COLD;
    }
    @Override public int getModes(ManasSkillInstance instance) {
        return state(instance) == PHOSPHOR ? 9 : instance.getMastery() >= getMaxMastery() ? 5 : 4;
    }
    @Override public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), getModes(instance));
    }
    private static int action(ManasSkillInstance instance, int mode) {
        if (mode < 0 || mode >= getModeCount(instance)) return -1;
        if (state(instance) != PHOSPHOR && mode == 4 && instance.getMastery() >= 5000) return 9;
        return mode > 0 && state(instance) == HOT ? mode + 3 : mode;
    }
    private static int getModeCount(ManasSkillInstance instance) {
        return state(instance) == PHOSPHOR ? 9 : instance.getMastery() >= 5000 ? 5 : 4;
    }

    private static void synchronizeCooldowns(ServerLevel level, ManasSkillInstance instance) {
        var cooldowns = new java.util.ArrayList<Integer>();
        var tag = instance.getOrCreateTag();
        for (int mode = 0; mode < 9; mode++) {
            int move = action(instance, mode);
            long remaining = move < 0 ? 0 : tag.getLong(READY_PREFIX + move) - level.getGameTime();
            cooldowns.add((int) Math.max(0, (remaining + 19) / 20));
        }
        instance.setCoolDownList(cooldowns);
        instance.markDirty();
    }

    private static void startCooldown(ServerLevel level, ManasSkillInstance instance, int action, int seconds) {
        instance.getOrCreateTag().putLong(READY_PREFIX + action, level.getGameTime() + seconds * 20L);
        synchronizeCooldowns(level, instance);
    }
    @Override public String getModeId(ManasSkillInstance instance, int mode) {
        int action = action(instance, mode);
        return action < 0 ? super.getModeId(instance, mode) : "half_cold_half_hot." + ACTIONS[action];
    }

    private static ResistSkill resistance(ManasSkillInstance instance) {
        return switch (state(instance)) {
            case HOT -> ResistanceSkills.HEAT_RESISTANCE.get();
            case PHOSPHOR -> ResistanceSkills.THERMAL_FLUCTUATION_RESISTANCE.get();
            default -> ResistanceSkills.COLD_RESISTANCE.get();
        };
    }
    private static ManasSkillInstance passiveInstance(ResistSkill skill) {
        ManasSkillInstance passive = new TensuraSkillInstance(skill);
        passive.setMastery(0);
        passive.setToggled(true);
        return passive;
    }
    private static boolean alreadyResistant(LivingEntity owner, ResistSkill skill) {
        return SkillUtils.isSkillToggled(owner, skill)
                || SkillUtils.isSkillToggled(owner, ResistanceSkills.THERMAL_FLUCTUATION_RESISTANCE.get())
                || SkillUtils.isSkillToggled(owner, ResistanceSkills.THERMAL_FLUCTUATION_NULLIFICATION.get());
    }
    @Override public boolean onBeingDamaged(ManasSkillInstance instance, LivingEntity owner, DamageSource source, float amount) {
        ResistSkill resistance = resistance(instance);
        return alreadyResistant(owner, resistance)
                || resistance.onBeingDamaged(passiveInstance(resistance), owner, source, amount);
    }
    @Override public boolean onTakenDamage(ManasSkillInstance instance, LivingEntity owner, DamageSource source, Changeable<Float> amount) {
        ResistSkill resistance = resistance(instance);
        return alreadyResistant(owner, resistance)
                || resistance.onTakenDamage(passiveInstance(resistance), owner, source, amount);
    }
    @Override public boolean onEffectAdded(ManasSkillInstance instance, LivingEntity owner, Entity source,
                                           Changeable<MobEffectInstance> effect) {
        ResistSkill resistance = resistance(instance);
        return alreadyResistant(owner, resistance)
                || resistance.onEffectAdded(passiveInstance(resistance), owner, source, effect);
    }

    @Override public void onPressed(ManasSkillInstance instance, LivingEntity owner, int key, int mode) {
        if (!(owner.level() instanceof ServerLevel level)) return;
        int action = action(instance, mode);
        if (action < 0) return;
        synchronizeCooldowns(level, instance);
        var tag = instance.getOrCreateTag();
        if (tag.getLong(READY_PREFIX + action) > level.getGameTime()) return;
        trackCaster(owner, instance);
        switch (action) {
            case 0 -> {
                int next = owner.isShiftKeyDown() && instance.getMastery() >= getMaxMastery()
                        && state(instance) != PHOSPHOR ? PHOSPHOR : state(instance) == COLD ? HOT : COLD;
                tag.putInt(STATE, next);
                tag.remove(HELD_ACTION);
                tag.remove(AEGIR);
                if (next == COLD) tag.remove(JET);
                instance.markDirty();
                stateShift(level, owner, next);
                synchronizeCooldowns(level, instance);
                message(owner, next == COLD ? "cold" : next == HOT ? "hot" : "phosphor");
            }
            case 1 -> {
                if (iceWall(level, owner, instance)) {
                    startCooldown(level, instance, action, 20);
                    instance.addMasteryPoint(owner);
                }
            }
            case 2 -> {
                if (tag.getInt(HELD_ACTION) == 2 || !payAura(owner, 20)) return;
                tag.putInt(HELD_ACTION, action);
                if (owner.onGround() && level.noCollision(owner, owner.getBoundingBox().move(0, 1, 0)))
                    owner.teleportTo(owner.getX(), owner.getY() + 1, owner.getZ());
            }
            case 3 -> {
                if (!payAura(owner, 200)) return;
                iceSpikes(level, owner, instance);
                startCooldown(level, instance, action, 2);
                instance.addMasteryPoint(owner);
            }
            case 4 -> {
                if (tag.getInt(HELD_ACTION) == 4 || !payAura(owner, 1000)) return;
                tag.putInt(HELD_ACTION, action);
                tag.putLong(FLASH_START, level.getGameTime());
                tag.putLong(FLASH_LAST, level.getGameTime());
                instance.markDirty();
                startCooldown(level, instance, action, 30);
                flashfireBeam(level, owner);
                instance.addMasteryPoint(owner);
            }
            case 5 -> {
                boolean enabled = !tag.getBoolean(JET);
                tag.putBoolean(JET, enabled);
                instance.markDirty();
                message(owner, enabled ? "jet_on" : "jet_off");
            }
            case 6 -> {
                if (flameBall(level, owner, instance)) {
                    startCooldown(level, instance, action, 10);
                    instance.addMasteryPoint(owner);
                }
            }
            case 7 -> {
                if (!payAura(owner, 2000)) return;
                paleBlade(level, owner);
                startCooldown(level, instance, action, 30);
                instance.addMasteryPoint(owner);
            }
            case 8 -> {
                if (tag.getBoolean(AEGIR) || !payAura(owner, 2000)) return;
                tag.putBoolean(AEGIR, true);
                startCooldown(level, instance, action, 20);
                instance.markDirty();
                aegirCharge(level, owner);
                message(owner, "aegir_ready");
            }
            case 9 -> {
                tag.putInt(STATE, PHOSPHOR);
                synchronizeCooldowns(level, instance);
                tag.remove(HELD_ACTION);
                instance.markDirty();
                phosphorActivation(level, owner);
                level.playSound(null, owner.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.4F, 1.2F);
                message(owner, "phosphor");
            }
        }
    }

    @Override public boolean onHeld(ManasSkillInstance instance, LivingEntity owner, int heldTicks, int mode) {
        int action = action(instance, mode);
        if (action != 2 && action != 4) return false;
        if (!(owner.level() instanceof ServerLevel level)) return true;
        var tag = instance.getOrCreateTag();
        if (action != tag.getInt(HELD_ACTION)) return false;
        if (!owner.isAlive()) return false;
        if (action == 2) {
            if (heldTicks > 0 && heldTicks % 20 == 0 && !payAura(owner, 20)) {
                tag.remove(HELD_ACTION);
                return false;
            }
            glide(level, owner);
            if (heldTicks % 20 == 0) instance.addMasteryPoint(owner);
            return true;
        }
        tag.putLong(FLASH_LAST, level.getGameTime());
        if (heldTicks >= FLASHFIRE_MAX_TICKS
                || level.getGameTime() - tag.getLong(FLASH_START) >= FLASHFIRE_MAX_TICKS) {
            stopFlashfire(instance);
            return false;
        }
        if (heldTicks > 0 && heldTicks % FLASHFIRE_INTERVAL == 0) {
            flashfireBeam(level, owner);
            instance.addMasteryPoint(owner);
        } else renderFlashfireBeam(level, owner);
        return true;
    }
    @Override public void onRelease(ManasSkillInstance instance, LivingEntity owner, int key, int mode, int heldTicks) {
        stopFlashfire(instance);
        instance.getOrCreateTag().remove(HELD_ACTION);
        instance.markDirty();
    }
    @Override public boolean shouldTriggerReleaseOnHeldInterrupt(ManasSkillInstance instance, LivingEntity owner, int key, int mode) {
        return true;
    }

    private static void message(LivingEntity owner, String key) {
        if (owner instanceof Player player)
            player.displayClientMessage(Component.translatable("tracadamia.skill.half_cold_half_hot." + key), true);
    }

    private static boolean payAura(LivingEntity owner, double cost) {
        var existence = TensuraStorages.getExistenceFrom(owner);
        double available = existence.getAura();
        if (available < cost) {
            message(owner, "not_enough_aura");
            return false;
        }
        existence.setAura(available - cost);
        existence.markDirty();
        return true;
    }
    private static Vec3 horizontal(LivingEntity owner) {
        double yaw = Math.toRadians(owner.getYRot());
        return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
    }
    private static Vec3 end(ServerLevel level, LivingEntity owner, Vec3 origin, Vec3 direction, double range) {
        return level.clip(new ClipContext(origin, origin.add(direction.scale(range)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner)).getLocation();
    }
    private static boolean hurt(ServerLevel level, LivingEntity owner, LivingEntity target, float damage, boolean cold, boolean bypass) {
        DamageSource source = level.damageSources().source(cold ? TensuraDamageTypes.ICE_BREATH : TensuraDamageTypes.HEAT_WAVE, owner);
        ((TensuraDamageSource) source).tensura$setSkillType(SkillType.UNIQUE);
        ((TensuraDamageSource) source).tensura$setElement(cold ? Element.WATER : Element.FLAME);
        if (bypass) ((TensuraDamageSource) source).tensura$setResistanceBypassLevel(1);
        target.invulnerableTime = 0;
        return target.hurt(source, damage);
    }

    private static boolean iceWall(ServerLevel level, LivingEntity owner, ManasSkillInstance instance) {
        LivingEntity target = ObjectSelectionHelper.getTargetingEntity(owner, 30, false);
        if (target == null || target == owner || !target.onGround()) {
            message(owner, "ground_target");
            return false;
        }
        if (!payAura(owner, 1000)) return false;
        Vec3 center = target.position(), forward = horizontal(owner);
        Vec3 side = new Vec3(-forward.z, 0, forward.x);
        String wallId = java.util.UUID.randomUUID().toString();
        wallContact(level, owner, target, wallId);

        // A widening wedge of jagged pillars reads as one enormous ice eruption from the user's direction.
        double distance = Math.max(6, Math.min(28, owner.position().distanceTo(center)));
        int rows = Math.max(6, (int) Math.ceil(distance / 1.7));
        for (int row = 0; row <= rows; row++) {
            double progress = row / (double) rows;
            Vec3 rowCenter = owner.position().add(forward.scale(2.5 + progress * (distance - 1.5)));
            int halfWidth = Math.min(6, 1 + row / 2);
            for (int lane = -halfWidth; lane <= halfWidth; lane++) {
                if ((row + lane) % 2 != 0 && lane != 0) continue;
                Vec3 point = rowCenter.add(side.scale(lane * 1.05));
                int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        (int) Math.floor(point.x), (int) Math.floor(point.z));
                double centerBias = 1.0 - Math.abs(lane) / (double) (halfWidth + 1);
                float height = (float) (2.5 + Math.pow(progress, 1.35) * 13 * centerBias
                        + level.random.nextDouble() * 2.5);
                spawnIcePillar(level, owner, instance, wallId,
                        new Vec3(point.x, groundY, point.z), height, 0.85F + (float) progress * 0.45F,
                        4 + row / 2);
            }
        }
        // Crown the impact point with several tall, tightly grouped spikes.
        for (int i = 0; i < 9; i++) {
            double angle = i * Math.PI * 2 / 9;
            Vec3 point = center.add(Math.cos(angle) * 1.7, 0, Math.sin(angle) * 1.7);
            int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    (int) Math.floor(point.x), (int) Math.floor(point.z));
            spawnIcePillar(level, owner, instance, wallId, new Vec3(point.x, groundY, point.z),
                    i == 0 ? 15 : 10 + level.random.nextInt(4), i == 0 ? 1.8F : 1.25F, 12);
        }
        level.playSound(null, target.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 2, 0.5F);
        return true;
    }

    private static void spawnIcePillar(ServerLevel level, LivingEntity owner, ManasSkillInstance instance,
                                       String wallId, Vec3 position, float height, float size, int delay) {
        IcePillarEntity pillar = new IcePillarEntity(level, owner);
        pillar.setPos(position);
        pillar.setBlockState(Blocks.BLUE_ICE.defaultBlockState());
        pillar.setHeight(height);
        pillar.setSize(size);
        pillar.setExtendingTick(delay);
        pillar.setLife(1200);
        pillar.setDamage(0);
        pillar.setContactDamage(0);
        pillar.setContactSecondaryDamage(0);
        pillar.setAoeDamage(0);
        pillar.setPushEntityUp(false);
        pillar.setYRot(owner.getYRot() + 180);
        pillar.setXRot(-32 - level.random.nextFloat() * 18);
        pillar.setSkill(instance);
        pillar.getPersistentData().putString(WALL_ID, wallId);
        level.addFreshEntity(pillar);
    }

    private static void glide(ServerLevel level, LivingEntity owner) {
        BlockPos feet = BlockPos.containing(owner.getX(), owner.getY() - 0.05, owner.getZ());
        // Only extend a path near solid ground; this cannot turn into flight over a ravine.
        if (level.getBlockState(feet).isAir() && level.getBlockState(feet.below()).isAir()) return;
        Vec3 look = owner.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0, look.z).normalize();
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        if (Math.abs(owner.xxa) > 0.05 || Math.abs(owner.zza) > 0.05) {
            double forwardInput = Math.abs(owner.zza) > 0.05 ? owner.zza : 0.35;
            forward = forward.scale(forwardInput).add(right.scale(owner.xxa)).normalize();
            right = new Vec3(-forward.z, 0, forward.x);
        }
        for (int step = 0; step <= 2; step++) {
            for (double lane = -0.6; lane <= 0.6; lane += 0.6) {
                BlockPos pos = BlockPos.containing(owner.position().add(forward.scale(step))
                        .add(right.scale(lane)).add(0, -0.05, 0));
                ThermalIce.place(level, pos, 200);
            }
        }
        Vec3 motion = owner.getDeltaMovement();
        double speed = Math.min(1.9, Math.max(0.55, Math.sqrt(motion.horizontalDistanceSqr()) + 0.1));
        owner.setDeltaMovement(forward.x * speed, Math.min(0, motion.y), forward.z * speed);
        owner.hurtMarked = true;
        if (owner.tickCount % 3 == 0) level.sendParticles(ParticleTypes.SNOWFLAKE,
                owner.getX(), owner.getY() + 0.2, owner.getZ(), 5, 0.3, 0.1, 0.3, 0.02);
    }

    private static void iceSpikes(ServerLevel level, LivingEntity owner, ManasSkillInstance instance) {
        Vec3 origin = owner.getEyePosition(), forward = owner.getLookAngle();
        Vec3 side = forward.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 up = side.cross(forward).normalize();
        for (int pellet = 0; pellet < 15; pellet++) {
            double horizontalSpread = (level.random.nextDouble() - 0.5) * 0.52;
            double verticalSpread = (level.random.nextDouble() - 0.5) * 0.34;
            Vec3 direction = forward.add(side.scale(horizontalSpread)).add(up.scale(verticalSpread)).normalize();
            IceLanceProjectile spike = new IceLanceProjectile(level, owner);
            spike.setSkill(instance);
            spike.setDamage(75);
            spike.setSecondaryDamage(0);
            spike.setChillBonusDamage(0);
            spike.setFrostBonusDamage(0);
            spike.setLife(40);
            spike.setSpeed(1.8F);
            spike.setDelayTick(0);
            spike.setExplosionRadius(0);
            spike.setIceBreaker(false);
            spike.setPos(origin.add(direction.scale(0.45)));
            spike.shootFromRot(direction);
            level.addFreshEntity(spike);
        }
        level.playSound(null, owner.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1, 1.4F);
    }

    private static boolean flameBall(ServerLevel level, LivingEntity owner, ManasSkillInstance instance) {
        if (instance.getOrCreateTag().hasUUID(FLAME_TARGET)) return false;
        LivingEntity target = ObjectSelectionHelper.getTargetingEntity(owner, 30, false);
        if (target == null || target == owner) { message(owner, "target"); return false; }
        if (!payAura(owner, 500)) return false;
        // Store the target on the skill instance so the fire visibly closes in before the area hit lands.
        instance.getOrCreateTag().putUUID(FLAME_TARGET, target.getUUID());
        renderWallOfFlames(level, target, 8);
        instance.getOrCreateTag().putInt(FLAME_TICKS, 7);
        instance.markDirty();
        level.playSound(null, target.blockPosition(), TensuraSoundEvents.CAST_FIRE.get(), SoundSource.PLAYERS, 1.2F, 0.9F);
        return true;
    }

    private static Vec3 bodyCenter(LivingEntity owner) {
        return owner.position().add(0, owner.getBbHeight() * 0.55, 0);
    }

    private static void expireFlashfireIfReleased(ServerLevel level, LivingEntity owner, ManasSkillInstance instance) {
        if (instance.getOrCreateTag().getInt(HELD_ACTION) != 4) return;
        long last = instance.getOrCreateTag().getLong(FLASH_LAST);
        long start = instance.getOrCreateTag().getLong(FLASH_START);
        if (last == 0 || level.getGameTime() - last > 2
                || start > 0 && level.getGameTime() - start >= FLASHFIRE_MAX_TICKS) {
            stopFlashfire(instance);
            instance.getOrCreateTag().remove(HELD_ACTION);
            instance.markDirty();
        }
    }

    private static void stopFlashfire(ManasSkillInstance instance) {
        if (instance.getOrCreateTag().getInt(HELD_ACTION) == 4) instance.getOrCreateTag().remove(HELD_ACTION);
        instance.getOrCreateTag().remove(FLASH_START);
        instance.getOrCreateTag().remove(FLASH_LAST);
        instance.markDirty();
    }

    private static void flashfireBeam(ServerLevel level, LivingEntity owner) {
        Vec3 origin = bodyCenter(owner);
        Vec3 destination = end(level, owner, origin, owner.getLookAngle(), 30);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(origin, destination).inflate(1.0), e -> e != owner && e.isAlive())) {
            if (!target.getBoundingBox().inflate(0.8).contains(origin)
                    && target.getBoundingBox().inflate(0.8).clip(origin, destination).isEmpty()) continue;
            hurt(level, owner, target, FLASHFIRE_TICK_DAMAGE, false, false);
        }
        renderFlashfireBeam(level, owner);
        level.playSound(null, owner.blockPosition(), TensuraSoundEvents.CAST_FIRE.get(),
                SoundSource.PLAYERS, 1.0F, 1.15F);
    }

    private static void renderFlashfireBeam(ServerLevel level, LivingEntity owner) {
        Vec3 origin = bodyCenter(owner);
        Vec3 destination = end(level, owner, origin, owner.getLookAngle(), 30);
        Vec3 direction = destination.subtract(origin);
        double length = direction.length();
        if (length < 0.01) return;
        direction = direction.scale(1.0 / length);
        Vec3 side = direction.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 0.01) side = new Vec3(1, 0, 0);
        else side = side.normalize();
        Vec3 up = side.cross(direction).normalize();
        for (double d = 0; d <= length; d += 0.55) {
            Vec3 center = origin.add(direction.scale(d));
            level.sendParticles(TensuraParticleTypes.RED_FIRE.get(), center.x, center.y, center.z,
                    3, 0.12, 0.12, 0.12, 0.004);
            level.sendParticles(ParticleTypes.FLAME, center.x, center.y, center.z,
                    4, 0.08, 0.08, 0.08, 0.01);
            if ((int) d % 2 == 0)
                level.sendParticles(TensuraParticleTypes.HEAT_EFFECT.get(), center.x, center.y, center.z,
                        1, 0.05, 0.05, 0.05, 0);
        }
        Vec3 tip = origin.add(direction.scale(Math.min(1.2, length)));
        for (int i = 0; i < 6; i++) {
            double angle = i * Math.PI * 2 / 6;
            Vec3 point = tip.add(side.scale(Math.cos(angle) * 0.25)).add(up.scale(Math.sin(angle) * 0.25));
            level.sendParticles(ParticleTypes.FLAME, point.x, point.y, point.z, 1, 0.02, 0.02, 0.02, 0.004);
        }
    }

    private static void tickFlameConvergence(ServerLevel level, LivingEntity owner, ManasSkillInstance instance) {
        var tag = instance.getOrCreateTag();
        if (!tag.hasUUID(FLAME_TARGET)) return;
        Entity rawTarget = level.getEntity(tag.getUUID(FLAME_TARGET));
        int ticks = tag.getInt(FLAME_TICKS);
        if (!(rawTarget instanceof LivingEntity target) || !target.isAlive() || ticks <= 0) {
            tag.remove(FLAME_TARGET);
            tag.remove(FLAME_TICKS);
            instance.markDirty();
            return;
        }
        Vec3 center = target.getBoundingBox().getCenter();
        renderWallOfFlames(level, target, ticks);
        if (--ticks == 0) {
            AABB area = new AABB(center, center).inflate(6, 4, 6);
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, area, e -> e != owner && e.isAlive()))
                hurt(level, owner, victim, 200, false, false);
            sphere(level, center, 3.5, false);
            tag.remove(FLAME_TARGET);
            tag.remove(FLAME_TICKS);
        } else tag.putInt(FLAME_TICKS, ticks);
        instance.markDirty();
    }

    private static void renderWallOfFlames(ServerLevel level, LivingEntity target, int remaining) {
        Vec3 center = target.getBoundingBox().getCenter();
        double progress = Math.clamp((8 - remaining) / 7.0, 0, 1);
        double radius = 6 - progress * 4.5;
        // Overlapping outer and inner flames enclose the target from the first frame.
        // Directed particles travel inward instead of leaving a hollow moving curtain.
        for (int i = 0; i < 192; i++) {
            double y = 1 - 2 * (i + 0.5) / 192;
            double angle = i * 2.399963229728653 + progress * 0.4;
            double ring = Math.sqrt(1 - y * y);
            Vec3 offset = new Vec3(Math.cos(angle) * ring * radius,
                    y * radius * 0.67, Math.sin(angle) * ring * radius);
            Vec3 point = center.add(offset);
            level.sendParticles(i % 4 == 0 ? TensuraParticleTypes.RED_FIRE.get() : ParticleTypes.FLAME, point.x, point.y, point.z,
                    0, -offset.x, -offset.y, -offset.z, 0.12);
            Vec3 inner = center.add(offset.scale(0.55));
            level.sendParticles(ParticleTypes.FLAME, inner.x, inner.y, inner.z,
                    2, 0.25, 0.25, 0.25, 0.01);
        }
        level.sendParticles(ParticleTypes.FLAME, center.x, center.y, center.z,
                60, radius * 0.25, radius * 0.18, radius * 0.25, 0.015);
    }

    private static void phosphorAura(ServerLevel level, LivingEntity owner) {
        Vec3 center = bodyCenter(owner);
        Vec3 side = horizontal(owner).cross(new Vec3(0, 1, 0)).normalize();
        for (double d = -1.3; d <= 1.3; d += 0.22) {
            Vec3 warm = center.add(side.scale(d)).add(0, d * 0.72, 0);
            Vec3 cold = center.add(side.scale(d)).add(0, -d * 0.72, 0);
            level.sendParticles(ParticleTypes.FLAME, warm.x, warm.y, warm.z, 1, 0.02, 0.02, 0.02, 0.005);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, cold.x, cold.y, cold.z, 1, 0.02, 0.02, 0.02, 0.005);
        }
        level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 4, 0.12, 0.12, 0.12, 0.01);
    }

    private static void stateShift(ServerLevel level, LivingEntity owner, int state) {
        Vec3 center = bodyCenter(owner);
        for (int i = 0; i < 36; i++) {
            double angle = i * Math.PI * 2 / 36;
            Vec3 point = center.add(Math.cos(angle) * 1.25, (i % 6) * 0.18 - 0.45, Math.sin(angle) * 1.25);
            var particle = state == COLD ? ParticleTypes.SNOWFLAKE
                    : state == HOT ? ParticleTypes.FLAME : i % 2 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME;
            level.sendParticles(particle, point.x, point.y, point.z, 2, 0.05, 0.08, 0.05, 0.015);
        }
    }

    private static void phosphorActivation(ServerLevel level, LivingEntity owner) {
        Vec3 center = bodyCenter(owner);
        for (int ring = 0; ring < 3; ring++) for (int i = 0; i < 48; i++) {
            double angle = i * Math.PI * 2 / 48;
            double radius = 1.2 + ring * 0.9;
            Vec3 point = center.add(Math.cos(angle) * radius, (ring - 1) * 0.6, Math.sin(angle) * radius);
            level.sendParticles((i + ring) % 2 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME,
                    point.x, point.y, point.z, 2, 0.08, 0.12, 0.08, 0.02);
        }
        level.sendParticles(ParticleTypes.FLASH, center.x, center.y, center.z, 2, 0, 0, 0, 0);
        phosphorAura(level, owner);
    }

    private static void aegirCharge(ServerLevel level, LivingEntity owner) {
        Vec3 forward = owner.getLookAngle();
        Vec3 fist = bodyCenter(owner).add(forward.scale(0.65));
        for (int i = 0; i < 16; i++) {
            double angle = i * Math.PI * 2 / 16 + owner.tickCount * 0.22;
            double radius = 0.25 + (i % 4) * 0.08;
            Vec3 point = fist.add(Math.cos(angle) * radius, Math.sin(angle) * radius, 0);
            level.sendParticles(i % 2 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SNOWFLAKE,
                    point.x, point.y, point.z, 1, 0.04, 0.04, 0.04, 0.01);
        }
    }

    private static void paleBlade(ServerLevel level, LivingEntity owner) {
        Vec3 direction = owner.getLookAngle().normalize();
        Vec3 start = bodyCenter(owner).add(direction.scale(1.2));
        PaleBlade blade = new PaleBlade(level, owner.getUUID(), start, direction);
        // Apply the damage on cast over the complete unobstructed flight path. This makes the
        // attack authoritative even if a later visual tick is delayed or skipped by the server.
        strikePaleBlade(level, owner, blade, start, end(level, owner, start, direction, 36.0D));
        PALE_BLADES.add(blade);
        renderPaleBlade(level, start, direction, 0);
        level.playSound(null, owner.blockPosition(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.5F, 1.25F);
    }

    private static void strikePaleBlade(ServerLevel level, LivingEntity owner, PaleBlade blade,
                                        Vec3 previous, Vec3 next) {
        AABB search = new AABB(previous, next).inflate(6.5D, 3.5D, 6.5D);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, search,
                entity -> entity != owner && entity.isAlive())) {
            if (!bladeHits(previous, next, blade.direction, victim)) continue;
            blade.lastHit.put(victim.getUUID(), blade.age);
            hurt(level, owner, victim, 200, true, false);
            hurt(level, owner, victim, 200, false, false);
            victim.push(blade.direction.x * 2.2D, 0.45D, blade.direction.z * 2.2D);
            victim.hurtMarked = true;
        }
    }

    private static void renderPaleBlade(ServerLevel level, Vec3 center, Vec3 direction, int age) {
        Vec3 side = direction.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 0.01) side = new Vec3(1, 0, 0);
        else side = side.normalize();
        Vec3 up = side.cross(direction).normalize();
        double radius = 5.5;
        for (int i = -24; i <= 24; i++) {
            double angle = i * Math.PI / 48;
            Vec3 point = center.add(side.scale(Math.sin(angle) * radius))
                    .add(up.scale(Math.cos(angle) * radius * 0.45));
            level.sendParticles(i % 2 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SNOWFLAKE,
                    point.x, point.y, point.z, 2, 0.08, 0.08, 0.08, 0.015);
            Vec3 inner = center.add(point.subtract(center).scale(0.65));
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, inner.x, inner.y, inner.z,
                    1, 0.16, 0.16, 0.16, 0.01);
        }
        level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 3, 0.25, 0.25, 0.25, 0.01);
    }
    private static void sphere(ServerLevel level, Vec3 center, double radius, boolean cold) {
        for (int i = 0; i < 100; i++) {
            double y = 1 - 2 * (i + 0.5) / 100, angle = i * 2.399963229728653;
            double ring = Math.sqrt(1 - y * y);
            Vec3 point = center.add(Math.cos(angle) * ring * radius, y * radius, Math.sin(angle) * ring * radius);
            level.sendParticles(cold ? ParticleTypes.SNOWFLAKE : ParticleTypes.FLAME, point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }
        level.playSound(null, BlockPos.containing(center), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.5F, cold ? 1.5F : 0.8F);
    }

    @Override public boolean onDamageEntity(ManasSkillInstance instance, LivingEntity owner, LivingEntity target,
                                           DamageSource source, Changeable<Float> amount) {
        if (!(owner.level() instanceof ServerLevel level) || source.getDirectEntity() != owner
                || !source.is(DamageTypes.PLAYER_ATTACK) && !source.is(DamageTypes.MOB_ATTACK)) return true;
        boolean aegir = state(instance) == PHOSPHOR && instance.getOrCreateTag().getBoolean(AEGIR);
        boolean jet = state(instance) != COLD && instance.getOrCreateTag().getBoolean(JET);
        if (!aegir && !jet) return true;
        trackCaster(owner, instance);
        jet = jet && payAura(owner, 500);
        // Consume the armed attack before dealing secondary damage, preventing recursive activations.
        if (aegir) { instance.getOrCreateTag().remove(AEGIR); instance.markDirty(); }
        Vec3 center = target.getBoundingBox().getCenter();
        if (aegir) {
            AegirWave wave = new AegirWave(level, owner.getUUID(), center);
            AEGIR_WAVES.add(wave);
            AABB area = new AABB(center, center).inflate(15, 8, 15);
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, area,
                    e -> e != owner && e.isAlive())) {
                Vec3 offset = victim.getBoundingBox().getCenter().subtract(center);
                if (Math.sqrt(offset.x * offset.x + offset.z * offset.z) > 15) continue;
                strikeAegirVictim(level, owner, victim, wave.hit);
            }
            level.sendParticles(ParticleTypes.FLASH, center.x, center.y, center.z, 3, 0, 0, 0, 0);
            level.playSound(null, target.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.2F, 0.55F);
        }
        if (jet) startJetBurst(level, owner, target);
        instance.addMasteryPoint(owner);
        return true;
    }

    private static void startJetBurst(ServerLevel level, LivingEntity owner, LivingEntity target) {
        Vec3 center = target.getBoundingBox().getCenter();
        JetBurst burst = new JetBurst(level, owner.getUUID(), center);
        burst.hit.add(target.getUUID());
        hurt(level, owner, target, 75, false, false);
        level.sendParticles(ParticleTypes.FLASH, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.END_ROD, center.x, center.y, center.z, 12, 0.18, 0.18, 0.18, 0.03);
        renderJetShell(level, center, 0.35, 0);
        JET_BURSTS.add(burst);
        level.playSound(null, target.blockPosition(), TensuraSoundEvents.CAST_FIRE.get(), SoundSource.PLAYERS, 1, 1.25F);
    }

    private static void tickJetBursts(ServerLevel level, LivingEntity owner) {
        for (var iterator = JET_BURSTS.iterator(); iterator.hasNext();) {
            JetBurst burst = iterator.next();
            if (burst.level != level || !burst.owner.equals(owner.getUUID())) continue;
            double radius = 0.85 + burst.age * 0.68;
            renderJetShell(level, burst.center, radius, burst.age);
            LivingEntity source = level.getEntity(burst.owner) instanceof LivingEntity living ? living : null;
            if (source == null) { iterator.remove(); continue; }
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(burst.center, burst.center).inflate(radius + 0.65),
                    e -> e != source && e.isAlive() && !burst.hit.contains(e.getUUID()))) {
                if (victim.getBoundingBox().getCenter().distanceTo(burst.center) > radius + 0.75) continue;
                burst.hit.add(victim.getUUID());
                hurt(level, source, victim, 75, false, false);
            }
            if (++burst.age >= 4) iterator.remove();
        }
    }

    private static void renderJetShell(ServerLevel level, Vec3 center, double radius, int age) {
        level.sendParticles(ParticleTypes.FLAME, center.x, center.y, center.z,
                24, radius * 0.35, radius * 0.35, radius * 0.35, 0.02);
        for (int i = 0; i < 48; i++) {
            double y = 1 - 2 * (i + 0.5) / 48, angle = i * 2.399963229728653 + age * 0.55;
            double ring = Math.sqrt(1 - y * y);
            Vec3 point = center.add(Math.cos(angle) * ring * radius, y * radius, Math.sin(angle) * ring * radius);
            Vec3 outward = point.subtract(center).normalize().scale(0.08);
            level.sendParticles(i % 5 == 0 ? ParticleTypes.END_ROD : ParticleTypes.FLAME,
                    point.x, point.y, point.z, 2, outward.x, outward.y, outward.z, 0.12);
        }
    }

    private static void tickPaleBlades() {
        for (var iterator = PALE_BLADES.iterator(); iterator.hasNext();) {
            PaleBlade blade = iterator.next();
            LivingEntity owner = blade.level.getEntity(blade.owner) instanceof LivingEntity living ? living : null;
            if (owner == null || !owner.isAlive() || owner.level() != blade.level) {
                iterator.remove();
                continue;
            }
            Vec3 previous = blade.center;
            Vec3 desired = blade.center.add(blade.direction.scale(3.0));
            // Stop at solid terrain, while still allowing victims reached before that block to be hit.
            Vec3 next = blade.level.clip(new ClipContext(previous, desired,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner)).getLocation();
            for (int step = 1; step <= 4; step++)
                renderPaleBlade(blade.level, blade.center.add(blade.direction.scale(step * 0.75)),
                        blade.direction, blade.age);
            // Search the blade's whole swept volume; a point-only check lets this fast projectile
            // skip living entities between ticks.
            AABB search = new AABB(previous, next).inflate(10.0, 6.0, 10.0);
            for (LivingEntity victim : blade.level.getEntitiesOfClass(LivingEntity.class, search,
                    e -> e != owner && e.isAlive())) {
                if (!bladeHits(previous, next, blade.direction, victim)) continue;
                int last = blade.lastHit.getOrDefault(victim.getUUID(), Integer.MIN_VALUE);
                if (blade.age - last < 20) continue;
                blade.lastHit.put(victim.getUUID(), blade.age);
                hurt(blade.level, owner, victim, 200, true, false);
                hurt(blade.level, owner, victim, 200, false, false);
                victim.push(blade.direction.x * 2.2, 0.45, blade.direction.z * 2.2);
                victim.hurtMarked = true;
            }
            blade.center = next;
            if (++blade.age >= 12 || next.distanceToSqr(desired) > 1.0E-4) iterator.remove();
        }
    }

    private static boolean bladeHits(Vec3 previous, Vec3 next, Vec3 direction, LivingEntity victim) {
        Vec3 start = previous;
        Vec3 delta = next.subtract(previous);
        double length = delta.length();
        Vec3 travel = length < 1.0E-4 ? direction : delta.scale(1.0 / length);
        AABB bounds = victim.getBoundingBox().inflate(0.5); // Increased padding for better hit detection
        Vec3 center = bounds.getCenter();
        Vec3 to = center.subtract(start);
        double along = Math.clamp(to.dot(travel), -0.5, length + 0.5);
        Vec3 closest = start.add(travel.scale(Math.max(0, Math.min(length, along))));
        double radius = 5.5 + Math.max(bounds.getXsize(), bounds.getZsize()) * 0.5;
        return closest.distanceToSqr(center) <= radius * radius
                || bounds.clip(start, next).isPresent()
                || bounds.intersects(new AABB(start, next).inflate(2.0)); // Increased from 1.2 to 2.0
    }

    private static void tickAegirWaves(ServerLevel level, LivingEntity owner) {
        for (var iterator = AEGIR_WAVES.iterator(); iterator.hasNext();) {
            AegirWave wave = iterator.next();
            if (wave.level != level || !wave.owner.equals(owner.getUUID())) continue;
            double radius = 1.5 + wave.age * 1.5;
            int points = Math.max(36, (int) (radius * 18));
            for (int i = 0; i < points; i++) {
                double angle = i * Math.PI * 2 / points;
                double x = wave.center.x + Math.cos(angle) * radius;
                double z = wave.center.z + Math.sin(angle) * radius;
                double crest = 0.5 + Math.sin((i + wave.age * 4) * 0.45) * 0.35;
                level.sendParticles(i % 3 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SNOWFLAKE,
                        x, wave.center.y + crest, z, 2, 0.18, 0.45, 0.18, 0.025);
                double innerRadius = Math.max(0, radius - 0.75);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        wave.center.x + Math.cos(angle) * innerRadius, wave.center.y + crest,
                        wave.center.z + Math.sin(angle) * innerRadius,
                        1, 0.2, 0.3, 0.2, 0.015);
                if (i % 7 == 0)
                    level.sendParticles(ParticleTypes.END_ROD, x, wave.center.y + crest + 0.5, z,
                            1, 0.08, 0.2, 0.08, 0.01);
            }
            AABB area = new AABB(wave.center, wave.center).inflate(15 + 1.5, 8, 15 + 1.5);
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, area,
                    e -> e != owner && e.isAlive() && !wave.hit.contains(e.getUUID()))) {
                Vec3 offset = victim.getBoundingBox().getCenter().subtract(wave.center);
                if (Math.sqrt(offset.x * offset.x + offset.z * offset.z) > 15 + 1.5) continue;
                strikeAegirVictim(level, owner, victim, wave.hit);
            }
            if (++wave.age >= 10) {
                sphere(level, wave.center, 15, true);
                iterator.remove();
            }
        }
    }

    private static void strikeAegirVictim(ServerLevel level, LivingEntity owner, LivingEntity victim,
                                          java.util.Set<java.util.UUID> hit) {
        if (!hit.add(victim.getUUID())) return;
        hurt(level, owner, victim, 300, true, false);
        hurt(level, owner, victim, 100, false, true);
    }

    private static final class JetBurst {
        private final ServerLevel level;
        private final java.util.UUID owner;
        private final Vec3 center;
        private final java.util.Set<java.util.UUID> hit = new java.util.HashSet<>();
        private int age;

        private JetBurst(ServerLevel level, java.util.UUID owner, Vec3 center) {
            this.level = level;
            this.owner = owner;
            this.center = center;
        }
    }

    private static final class PaleBlade {
        private final ServerLevel level;
        private final java.util.UUID owner;
        private final Vec3 direction;
        private final java.util.Map<java.util.UUID, Integer> lastHit = new java.util.HashMap<>();
        private Vec3 center;
        private int age;

        private PaleBlade(ServerLevel level, java.util.UUID owner, Vec3 center, Vec3 direction) {
            this.level = level;
            this.owner = owner;
            this.center = center;
            this.direction = direction;
        }
    }

    private static final class AegirWave {
        private final ServerLevel level;
        private final java.util.UUID owner;
        private final Vec3 center;
        private final java.util.Set<java.util.UUID> hit = new java.util.HashSet<>();
        private int age;

        private AegirWave(ServerLevel level, java.util.UUID owner, Vec3 center) {
            this.level = level;
            this.owner = owner;
            this.center = center;
        }
    }
}
