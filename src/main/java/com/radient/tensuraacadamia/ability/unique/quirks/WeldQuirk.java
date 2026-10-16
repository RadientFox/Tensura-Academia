package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import dev.architectury.event.EventResult;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.manascore.skill.api.SkillEvents;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.effect.template.TensuraMobEffect;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class WeldQuirk extends Skill {

    private static final QuirkSkillsConfig.Weld CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Weld;
    private static final QuirkSkillsConfig.Grabbing GRAB_CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).Grabbing;

    private static final int WELD = 0;
    private static final int ARREST = 1;
    private static final int WELDCRAFT = 2;
    private static final int ATTACH = 3;
    private static final int TRANSPORT = 4;
    private static final int MODES = 5;

    private static final ResourceLocation ANCHORED = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "weld_anchor");
    private static final List<Holder<Attribute>> MOVEMENT = List.of(Attributes.MOVEMENT_SPEED, Attributes.JUMP_STRENGTH, Attributes.FLYING_SPEED);
    private static final DustParticleOptions SPARK = new DustParticleOptions(new Vector3f(1.0F, 0.55F, 0.1F), 0.7F);
    private static final double ANCHOR_SLACK = 0.05D;
    private static final double SINK_SPEED = 0.4D;
    private static final double ATTACH_BREAK_DISTANCE = 64.0D;
    private static final int MARKER_TICKS = 10;

    private static final class Anchor {
        private final LivingEntity target;
        private final UUID owner;
        private final int mode;
        private final long until;
        private Vec3 pos;

        private Anchor(LivingEntity target, UUID owner, int mode, long until) {
            this.target = target;
            this.owner = owner;
            this.mode = mode;
            this.until = until;
            this.pos = target.position();
        }
    }

    private record Attach(ManasSkillInstance instance, LivingEntity owner, LivingEntity target, Vec3 offset) {}

    private record Carried(LivingEntity target, ManasSkillInstance instance, long start) {}

    private static final List<Anchor> ANCHORS = new ArrayList<>();
    private static final List<Attach> ATTACHES = new ArrayList<>();
    private static final Map<LivingEntity, List<Carried>> TRANSPORTS = new HashMap<>();
    private static final Map<Integer, List<Integer>> CLIENT_TRANSPORTS = new HashMap<>();
    private static Set<LivingEntity> rooted = new HashSet<>();
    private static boolean clientRooted;

    public WeldQuirk() {
        super(Skill.SkillType.UNIQUE);
    }

    @Override
    public int getMaxMastery() {
        return (int) CONFIG.masteryPoints;
    }

    @Override
    public double getAuraCost(LivingEntity entity, ManasSkillInstance instance, int mode) {
        if (entity.isShiftKeyDown() && mode != ARREST) {
            return 0.0D;
        }

        return switch (mode) {
            case WELD -> CONFIG.weldAuraCost;
            case ARREST -> CONFIG.arrestAuraCost;
            case WELDCRAFT -> CONFIG.weldcraftAuraCost;
            case ATTACH -> CONFIG.attachAuraCost;
            case TRANSPORT -> CONFIG.transportAuraCost;
            default -> 0.0D;
        };
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return MODES;
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), MODES);
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case WELD -> "weld.weld";
            case ARREST -> "weld.arrest";
            case WELDCRAFT -> "weld.weldcraft";
            case ATTACH -> "weld.attach";
            case TRANSPORT -> "weld.transport";
            default -> super.getModeId(instance, mode);
        };
    }

    @Override
    public boolean canIgnoreCoolDown(ManasSkillInstance instance, LivingEntity entity, int mode) {
        return entity.isShiftKeyDown() && mode != ARREST || super.canIgnoreCoolDown(instance, entity, mode);
    }

    public static Optional<ManasSkillInstance> getWeld(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.WELD.get()).filter(instance -> instance.getMastery() >= 0.0D);
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        switch (mode) {
            case WELD, WELDCRAFT -> {
                if (entity.isShiftKeyDown()) {
                    release(level, entity, mode);
                } else {
                    anchor(level, instance, entity, mode);
                }
            }
            case ARREST -> anchor(level, instance, entity, ARREST);
            case ATTACH -> {
                if (entity.isShiftKeyDown()) {
                    detach(level, instance, entity);
                } else {
                    attach(level, instance, entity);
                }
            }
            case TRANSPORT -> {
                if (entity.isShiftKeyDown()) {
                    dropTransported(level, entity);
                } else {
                    transport(level, instance, entity);
                }
            }
        }
    }

    @Override
    public void onForgetSkill(ManasSkillInstance instance, LivingEntity entity) {
        super.onForgetSkill(instance, entity);
        ANCHORS.removeIf(anchor -> {
            boolean mine = anchor.owner.equals(entity.getUUID());
            if (mine) {
                free(anchor);
            }
            return mine;
        });
        ATTACHES.removeIf(attach -> attach.owner() == entity);
        releaseTransported(entity);
    }

    // Weld, Arrest and Weldcraft

    private static void anchor(ServerLevel level, ManasSkillInstance instance, LivingEntity entity, int mode) {
        LivingEntity target = MultiArms.getTarget(entity, CONFIG.targetRange);
        if (target == null) {
            fail(entity, Component.translatable("tensura.targeting.not_targeted"));
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, mode)) {
            return;
        }

        int seconds = switch (mode) {
            case ARREST -> CONFIG.arrestSeconds;
            case WELDCRAFT -> CONFIG.weldcraftSeconds;
            default -> CONFIG.weldSeconds;
        };

        ANCHORS.removeIf(anchor -> anchor.target == target && anchor.mode == mode);
        ANCHORS.add(new Anchor(target, entity.getUUID(), mode, level.getGameTime() + seconds * 20L));
        if (mode == WELDCRAFT) {
            TensuraMobEffect.addEffect(target, new MobEffectInstance(MobEffects.BLINDNESS, seconds * 20, 0, false, true, true), entity, instance.getSkill(), mode);
        }

        if (target instanceof ServerPlayer player && mode != ARREST) {
            stopFlying(player);
        }

        entity.swing(InteractionHand.MAIN_HAND, true);
        sparks(level, target, mode, 16);
        level.sendParticles(ParticleTypes.LAVA, target.getX(), target.getY() + target.getBbHeight() * 0.3D, target.getZ(), 4, target.getBbWidth() * 0.4D, 0.1D, target.getBbWidth() * 0.4D, 0.0D);
        playSound(level, target.position(), SoundEvents.ANVIL_USE, 0.6F, 1.5F);
        playSound(level, target.position(), SoundEvents.FIRECHARGE_USE, 0.5F, 1.8F);
        instance.setCoolDown(switch (mode) {
            case ARREST -> CONFIG.arrestCooldown;
            case WELDCRAFT -> CONFIG.weldcraftCooldown;
            default -> CONFIG.weldCooldown;
        }, mode);
        instance.addMasteryPoint(entity);
    }

    private static void release(ServerLevel level, LivingEntity entity, int mode) {
        List<Anchor> mine = ANCHORS.stream().filter(anchor -> anchor.mode == mode && anchor.owner.equals(entity.getUUID())).toList();
        if (mine.isEmpty()) {
            fail(entity, Component.translatable("tracadamia.skill.weld.nothing_welded"));
            return;
        }

        ANCHORS.removeAll(mine);
        for (Anchor anchor : mine) {
            free(anchor);
            playSound(level, anchor.target.position(), SoundEvents.CHAIN_BREAK, 0.8F, 1.2F);
        }

        message(entity, "tracadamia.skill.weld.released");
    }

    private static void free(Anchor anchor) {
        if (anchor.mode == WELDCRAFT && ANCHORS.stream().noneMatch(other -> other != anchor && other.target == anchor.target && other.mode == WELDCRAFT)) {
            anchor.target.removeEffect(MobEffects.BLINDNESS);
        }
    }

    public static boolean isRooted(LivingEntity entity) {
        return ANCHORS.stream().anyMatch(anchor -> anchor.target == entity && anchor.mode != ARREST);
    }

    public static boolean isArrested(LivingEntity entity) {
        return ANCHORS.stream().anyMatch(anchor -> anchor.target == entity && anchor.mode == ARREST);
    }

    // Weldcraft stops every active skill, Arrest only stops quirks
    // might change to skills in general?
    private static @Nullable String getBlock(ManasSkillInstance instance, LivingEntity entity) {
        if (entity.level().isClientSide) {
            return null;
        }

        boolean quirk = instance.getSkill().getRegistryName().getNamespace().equals(TensuraAcadamia.MODID);
        for (Anchor anchor : ANCHORS) {
            if (anchor.target == entity && anchor.mode == WELDCRAFT) {
                return "tracadamia.skill.weld.limbs_welded";
            }

            if (anchor.target == entity && anchor.mode == ARREST && quirk) {
                return "tracadamia.skill.weld.arms_welded";
            }
        }

        return null;
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> SkillEvents.ACTIVATE_SKILL.register((instance, entity, keyNumber, mode) -> {
            String block = getBlock(instance.get(), entity);
            if (block == null) {
                return EventResult.pass();
            }

            if (entity instanceof Player player) {
                player.displayClientMessage(Component.translatable(block).withStyle(ChatFormatting.RED), true);
            }
            return EventResult.interruptFalse();
        }));
    }

    private static void pin(Anchor anchor) {
        LivingEntity target = anchor.target;
        if (MultiArms.isHeld(target) || target.isPassenger()) {
            anchor.pos = target.position();
            return;
        }

        if (target instanceof ServerPlayer player) {
            stopFlying(player);
        }

        double y = Math.min(target.getY(), anchor.pos.y);
        anchor.pos = new Vec3(anchor.pos.x, y, anchor.pos.z);
        if (target.position().subtract(anchor.pos).horizontalDistance() > ANCHOR_SLACK || target.getY() > y + ANCHOR_SLACK) {
            target.teleportTo(anchor.pos.x, y, anchor.pos.z);
        }

        if (!(target instanceof Player)) {
            boolean floats = target.isNoGravity() || target instanceof FlyingMob;
            double fall = target.onGround() ? 0.0D : floats ? -SINK_SPEED : Math.min(0.0D, target.getDeltaMovement().y);
            target.setDeltaMovement(0.0D, fall, 0.0D);
            if (target instanceof Mob mob) {
                mob.getNavigation().stop();
            }
        }
    }

    private static void stopFlying(ServerPlayer player) {
        if (player.getAbilities().flying) {
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }

        if (player.isFallFlying()) {
            player.stopFallFlying();
        }
    }

    // Loses its speed
    private static void setRooted(LivingEntity entity, boolean locked) {
        if (entity instanceof ServerPlayer player) {
            if (!player.hasDisconnected()) {
                PacketDistributor.sendToPlayer(player, new RootedPayload(locked));
            }
            return;
        }

        for (Holder<Attribute> attribute : MOVEMENT) {
            AttributeInstance instance = entity.getAttribute(attribute);
            if (instance == null) {
                continue;
            }

            if (locked) {
                instance.addOrUpdateTransientModifier(new AttributeModifier(ANCHORED, -1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            } else {
                instance.removeModifier(ANCHORED);
            }
        }
    }

    private static void sparks(ServerLevel level, LivingEntity target, int mode, int count) {
        double width = target.getBbWidth() * 0.4D;
        if (mode != ARREST) {
            level.sendParticles(SPARK, target.getX(), target.getY() + 0.1D, target.getZ(), count, width, 0.05D, width, 0.0D);
        }

        if (mode != WELD) {
            level.sendParticles(SPARK, target.getX(), target.getY() + target.getBbHeight() * 0.55D, target.getZ(), count, width, 0.1D, width, 0.0D);
        }

        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + target.getBbHeight() * 0.3D, target.getZ(), Math.max(1, count / 4), width, 0.2D, width, 0.05D);
    }

    // Attach

    public static boolean isAttached(LivingEntity entity) {
        return ATTACHES.stream().anyMatch(attach -> attach.owner() == entity);
    }

    private static void attach(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (isAttached(entity)) {
            fail(entity, Component.translatable("tracadamia.skill.weld.already_attached"));
            return;
        }

        LivingEntity target = MultiArms.getTarget(entity, CONFIG.targetRange);
        if (target == null || MultiArms.isHoldPair(entity, target)) {
            fail(entity, Component.translatable("tensura.targeting.not_targeted"));
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, ATTACH)) {
            return;
        }

        entity.stopRiding();
        Attach attach = new Attach(instance, entity, target, getAttachOffset(target, entity));
        ATTACHES.add(attach);
        MultiArms.holdAt(target, entity, attach.offset(), MultiArms.HOLD_BODY, true, true);
        sparks(level, entity, WELDCRAFT, 10);
        playSound(level, entity.position(), SoundEvents.ANVIL_USE, 0.5F, 1.7F);
        instance.addMasteryPoint(entity);
    }

    private static Vec3 getAttachOffset(LivingEntity target, LivingEntity entity) {
        Vec3 forward = Vec3.directionFromRotation(0.0F, target.yBodyRot);
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        Vec3 away = entity.position().subtract(target.position());
        Vec3 side = new Vec3(away.dot(right), 0.0D, away.dot(forward));
        side = side.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, -1.0D) : side.normalize();
        double distance = (target.getBbWidth() + entity.getBbWidth()) * 0.5D;
        double height = Math.max(0.0D, Math.min(entity.getY() - target.getY(), target.getBbHeight() - entity.getBbHeight() * 0.5D));
        return new Vec3(side.x * distance, height, side.z * distance);
    }

    private static void detach(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        if (!ATTACHES.removeIf(attach -> attach.owner() == entity)) {
            fail(entity, Component.translatable("tracadamia.skill.weld.not_attached"));
            return;
        }

        MultiArms.letGo(entity);

        playSound(level, entity.position(), SoundEvents.CHAIN_BREAK, 0.8F, 1.2F);
        instance.setCoolDown(CONFIG.attachCooldown, ATTACH);
    }

    // false once the user lets go or can't hold on
    private static boolean tickAttach(Attach attach) {
        LivingEntity owner = attach.owner();
        LivingEntity target = attach.target();
        if (!owner.isAlive() || owner.isRemoved() || !target.isAlive() || target.isRemoved() || owner.level() != target.level() || owner.distanceToSqr(target) > ATTACH_BREAK_DISTANCE * ATTACH_BREAK_DISTANCE) {
            attach.instance().setCoolDown(CONFIG.attachCooldown, ATTACH);
            return false;
        }

        MultiArms.holdAt(target, owner, attach.offset(), MultiArms.HOLD_BODY, true, true);
        if (owner.tickCount % BASE_CONFIG.Mastery.masteryHoldTick == 0) {
            attach.instance().addMasteryPoint(owner);
        }

        return true;
    }


    private static int getSlots() {
        return Math.max(1, CONFIG.maxTransported);
    }

    public static List<LivingEntity> getTransported(LivingEntity entity) {
        return TRANSPORTS.getOrDefault(entity, List.of()).stream().map(Carried::target).toList();
    }

    private static void transport(ServerLevel level, ManasSkillInstance instance, LivingEntity entity) {
        LivingEntity target = MultiArms.getNearbyTarget(entity, CONFIG.targetRange);
        if (target == null || target.hasIndirectPassenger(entity)) {
            fail(entity, Component.translatable("tensura.targeting.not_targeted"));
            return;
        }

        List<LivingEntity> carried = getTransported(entity);
        if (carried.contains(target)) {
            fail(entity, Component.translatable("tracadamia.skill.weld.already_transported"));
            return;
        }

        if (!MultiArms.canGrab(entity, target)) {
            fail(entity, Component.translatable(MultiArms.getGrabFail(target)));
            return;
        }

        if (!MultiArms.isNoLarger(target, entity)) {
            fail(entity, Component.translatable("tracadamia.skill.weld.too_big"));
            return;
        }

        if (carried.size() >= getSlots()) {
            fail(entity, Component.translatable("tracadamia.skill.weld.transport_full", getSlots()));
            return;
        }

        if (EnergyHelper.isOutOfEnergy(entity, instance, TRANSPORT)) {
            return;
        }

        target.stopRiding();
        if (!target.startRiding(entity, true)) {
            fail(entity, Component.translatable("tensura.targeting.not_targeted"));
            return;
        }

        TRANSPORTS.computeIfAbsent(entity, key -> new ArrayList<>()).add(new Carried(target, instance, level.getGameTime()));
        syncTransport(entity);
        sparks(level, target, WELDCRAFT, 10);
        playSound(level, target.position(), SoundEvents.ANVIL_USE, 0.5F, 1.6F);
        instance.setCoolDown(CONFIG.transportCooldown, TRANSPORT);
        instance.addMasteryPoint(entity);
    }

    // Puts the targets on the player
    // puts them on the back and then it goes to either left or right

    private static Vec3 getTransportOffset(LivingEntity owner, Entity target, int slot) {
        double height = Math.max(0.0D, owner.getBbHeight() * 0.6D - target.getBbHeight() * 0.5D);
        double gap = (owner.getBbWidth() + target.getBbWidth()) * 0.5D;
        return switch (slot % 3) {
            case 1 -> new Vec3(-gap, height, 0.0D);
            case 2 -> new Vec3(gap, height, 0.0D);
            default -> new Vec3(0.0D, height, -gap);
        };
    }

    public static boolean isTransporting(Entity vehicle) {
        return vehicle.level().isClientSide ? CLIENT_TRANSPORTS.containsKey(vehicle.getId()) : vehicle instanceof LivingEntity living && TRANSPORTS.containsKey(living);
    }

    // used by HeldPassengerMixin
    public static @Nullable Vec3 getTransportSpot(Entity vehicle, Entity passenger) {
        if (!(vehicle instanceof LivingEntity owner)) {
            return null;
        }

        int slot = -1;
        if (vehicle.level().isClientSide) {
            List<Integer> ids = CLIENT_TRANSPORTS.get(vehicle.getId());
            slot = ids == null ? -1 : ids.indexOf(passenger.getId());
        } else {
            List<Carried> carried = TRANSPORTS.get(owner);
            for (int index = 0; carried != null && index < carried.size(); index++) {
                if (carried.get(index).target() == passenger) {
                    slot = index;
                }
            }
        }

        if (slot < 0) {
            return null;
        }

        Vec3 offset = getTransportOffset(owner, passenger, slot);
        Vec3 forward = Vec3.directionFromRotation(0.0F, owner.yBodyRot);
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        return owner.position().add(forward.scale(offset.z)).add(right.scale(offset.x)).add(0.0D, offset.y, 0.0D);
    }

    private static void dropTransported(ServerLevel level, LivingEntity entity) {
        if (getTransported(entity).isEmpty()) {
            fail(entity, Component.translatable("tracadamia.skill.weld.nothing_transported"));
            return;
        }

        releaseTransported(entity);
        playSound(level, entity.position(), SoundEvents.CHAIN_BREAK, 0.8F, 1.2F);
    }

    private static void releaseTransported(LivingEntity entity) {
        List<Carried> carried = TRANSPORTS.remove(entity);
        if (carried == null) {
            return;
        }

        carried.stream().map(Carried::target).filter(target -> target.getVehicle() == entity).forEach(Entity::stopRiding);
        syncTransport(entity);
    }

    private static void syncTransport(LivingEntity entity) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, new TransportPayload(entity.getId(), getTransported(entity).stream().map(Entity::getId).toList()));
        if (entity instanceof ServerPlayer player && !player.hasDisconnected()) {
            player.connection.send(new ClientboundSetPassengersPacket(player));
        }
    }

    // players can get off by crouching
    // hostile mobs have to attack and eventually get loose
    private static boolean tickTransport(LivingEntity owner, List<Carried> carried) {
        if (!owner.isAlive() || owner.isRemoved()) {
            carried.stream().map(Carried::target).filter(target -> target.getVehicle() == owner).forEach(Entity::stopRiding);
            syncTransport(owner);
            return false;
        }

        long time = owner.level().getGameTime();
        boolean changed = carried.removeIf(carry -> {
            LivingEntity target = carry.target();
            if (!target.isAlive() || target.isRemoved() || target.getVehicle() != owner) {
                return true;
            }

            if (MultiArms.isHostile(target, owner) && time - carry.start() > GRAB_CONFIG.hostileHoldSeconds * 20L) {
                target.stopRiding();
                return true;
            }
            return false;
        });

        for (Carried carry : carried) {
            carry.target().setYRot(owner.yBodyRot);
            carry.target().setYBodyRot(owner.yBodyRot);
            carry.target().setYHeadRot(owner.yBodyRot);
        }

        if (changed) {
            syncTransport(owner);
        }

        if (!carried.isEmpty() && owner.tickCount % BASE_CONFIG.Mastery.masteryHoldTick == 0) {
            carried.get(0).instance().addMasteryPoint(owner);
        }

        return !carried.isEmpty();
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof LivingEntity carrier && TRANSPORTS.containsKey(carrier) && event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new TransportPayload(carrier.getId(), getTransported(carrier).stream().map(Entity::getId).toList()));
        }
    }

    @SubscribeEvent
    public static void onTransportedHurt(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || TRANSPORTS.isEmpty()) {
            return;
        }

        if (event.getSource().getEntity() instanceof LivingEntity attacker && getTransported(entity).contains(attacker)
                || event.getSource().is(DamageTypes.IN_WALL) && entity.getVehicle() instanceof LivingEntity carrier && getTransported(carrier).contains(entity)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!ANCHORS.isEmpty() || !rooted.isEmpty()) {
            ANCHORS.removeIf(anchor -> {
                LivingEntity target = anchor.target;
                boolean over = !target.isAlive() || target.isRemoved() || target.level().getGameTime() > anchor.until;
                if (over) {
                    free(anchor);
                }
                return over;
            });

            Set<LivingEntity> locked = new HashSet<>();
            for (Anchor anchor : ANCHORS) {
                if (anchor.mode != ARREST) {
                    pin(anchor);
                    locked.add(anchor.target);
                }

                if (anchor.target.level() instanceof ServerLevel level && anchor.target.tickCount % MARKER_TICKS == 0) {
                    sparks(level, anchor.target, anchor.mode, 2);
                }
            }

            rooted.stream().filter(entity -> !locked.contains(entity)).forEach(entity -> setRooted(entity, false));
            locked.stream().filter(entity -> !rooted.contains(entity)).forEach(entity -> setRooted(entity, true));
            rooted = locked;
        }

        if (!ATTACHES.isEmpty()) {
            ATTACHES.removeIf(attach -> !tickAttach(attach));
        }

        if (!TRANSPORTS.isEmpty()) {
            TRANSPORTS.entrySet().removeIf(entry -> !tickTransport(entry.getKey(), entry.getValue()));
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ANCHORS.clear();
        ATTACHES.clear();
        TRANSPORTS.clear();
        rooted = new HashSet<>();
    }

    private static void playSound(ServerLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static void message(LivingEntity entity, String key) {
        if (entity instanceof Player player) {
            player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY), true);
        }
    }

    private static void fail(LivingEntity entity, Component message) {
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(), TensuraSoundEvents.GENERIC_CAST_FAIL.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        if (entity instanceof Player player) {
            player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
        }
    }

    public static boolean isClientRooted() {
        return clientRooted;
    }

    public static void clearClient() {
        clientRooted = false;
        CLIENT_TRANSPORTS.clear();
    }

    public record RootedPayload(boolean rooted) implements CustomPacketPayload {
        public static final Type<RootedPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "weld_rooted"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RootedPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, RootedPayload::rooted,
                RootedPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record TransportPayload(int carrier, List<Integer> carried) implements CustomPacketPayload {
        public static final Type<TransportPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "weld_transport"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TransportPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, TransportPayload::carrier,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), TransportPayload::carried,
                TransportPayload::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(RootedPayload.TYPE, RootedPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> clientRooted = payload.rooted()));
        event.registrar("1").playToClient(TransportPayload.TYPE, TransportPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (payload.carried().isEmpty()) {
                CLIENT_TRANSPORTS.remove(payload.carrier());
            } else {
                CLIENT_TRANSPORTS.put(payload.carrier(), payload.carried());
            }
        }));
    }

}
