package com.radient.tensuraacadamia.ability.unique.quirks.neworder;

import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.config.skills.QuirkSkillsConfig;
import com.radient.tensuraacadamia.util.HitRules;
import io.github.manasmods.manascore.config.ConfigRegistry;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.entity.magic.spike.MudSpikeEntity;
import io.github.manasmods.tensura.registry.sound.TensuraSoundEvents;
import io.github.manasmods.tensura.util.EnergyHelper;
import io.github.manasmods.tensura.util.ObjectSelectionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

// The ground rules that cover the area around the user
@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class NewOrderGround {

    private static final QuirkSkillsConfig.NewOrder CONFIG = ConfigRegistry.getConfig(QuirkSkillsConfig.class).NewOrder;

    public static final int SLIPPERY = 1;
    public static final int ELASTIC = 2;
    public static final int NO_FALL = 4;
    private static final float ICE = 0.98F;
    private static final int JUMP_CHECK = 5;
    private static final double SPIKE_SPREAD = 1.2D;
    private static final float SPIKE_LEAN = -75.0F;
    private static final ResourceLocation ELASTIC_JUMP = ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "new_order_elastic_jump");

    // A New Order user and the ground rules
    public record Field(LivingEntity holder, int flags) {}

    private static final Map<LivingEntity, Field> FIELDS = new HashMap<>();
    private static final List<Field> CLIENT_FIELDS = new ArrayList<>();
    private static final Set<LivingEntity> JUMPING = Collections.newSetFromMap(new WeakHashMap<>());

    private NewOrderGround() {
    }

    static int getFlag(NewOrderRule rule) {
        return switch (rule) {
            case SLIPPERY -> SLIPPERY;
            case ELASTIC -> ELASTIC;
            case NO_FALL -> NO_FALL;
            default -> 0;
        };
    }

    private static double getHalf(int flag) {
        return switch (flag) {
            case SLIPPERY -> CONFIG.slipperySize;
            case ELASTIC -> CONFIG.elasticSize;
            default -> CONFIG.noFallSize;
        } * 0.5D;
    }

    static void setField(LivingEntity holder, int flags) {
        if (flags == 0) {
            FIELDS.remove(holder);
        } else {
            Field field = FIELDS.get(holder);
            if (field == null || field.flags() != flags) {
                FIELDS.put(holder, new Field(holder, flags));
            }
        }
    }

    // uhh rules synced with the skills
    public static void updateClientFields(List<? extends Player> players) {
        CLIENT_FIELDS.clear();
        for (Player player : players) {
            int flags = 0;
            for (NewOrderRule rule : new NewOrderRule[]{NewOrderRule.SLIPPERY, NewOrderRule.ELASTIC}) {
                if (NewOrderRules.hasRule(player, rule)) {
                    flags |= getFlag(rule);
                }
            }
            if (flags != 0) {
                CLIENT_FIELDS.add(new Field(player, flags));
            }
        }
    }

    public static void clearClient() {
        CLIENT_FIELDS.clear();
    }

    private static Collection<Field> getFields(Level level) {
        return level.isClientSide ? CLIENT_FIELDS : FIELDS.values();
    }

    private static boolean isInside(Field field, double x, double y, double z, int flag) {
        LivingEntity holder = field.holder();
        double half = getHalf(flag);
        return Math.abs(x - holder.getX()) <= half && Math.abs(y - holder.getY()) <= half && Math.abs(z - holder.getZ()) <= half;
    }

    // stuff for slippery ground and elasticity
    private static boolean isAffected(Entity entity, int flag) {
        Collection<Field> fields = getFields(entity.level());
        if (fields.isEmpty()) {
            return false;
        }

        for (Field field : fields) {
            if ((field.flags() & flag) == 0 || field.holder().level() != entity.level() || !isInside(field, entity.getX(), entity.getY(), entity.getZ(), flag)) {
                continue;
            }

            if (flag == SLIPPERY || !HitRules.canHit(field.holder(), entity)) {
                return true;
            }
        }
        return false;
    }

    public static float getFriction(Entity entity, float friction) {
        return friction < ICE && isAffected(entity, SLIPPERY) ? ICE : friction;
    }

    public static boolean bounce(Entity entity) {
        if (entity.isSuppressingBounce() || !isAffected(entity, ELASTIC)) {
            return false;
        }

        Vec3 motion = entity.getDeltaMovement();
        if (motion.y < 0.0D) {
            entity.setDeltaMovement(motion.x, -motion.y * (entity instanceof LivingEntity ? 1.0D : 0.8D), motion.z);
        }
        return true;
    }

    public static boolean holdsBlock(Level level, BlockPos pos) {
        if (FIELDS.isEmpty()) {
            return false;
        }

        for (Field field : FIELDS.values()) {
            if ((field.flags() & NO_FALL) != 0 && field.holder().level() == level && isInside(field, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, NO_FALL)) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!event.getEntity().level().isClientSide && isAffected(event.getEntity(), ELASTIC)) {
            event.setDamageMultiplier(0.0F);
        }
    }

    private static void setJump(LivingEntity entity, boolean on) {
        AttributeInstance jump = entity.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump == null) {
            return;
        }

        if (on) {
            AttributeModifier current = jump.getModifier(ELASTIC_JUMP);
            if (current == null || current.amount() != CONFIG.elasticJumpStrength) {
                jump.addOrUpdateTransientModifier(new AttributeModifier(ELASTIC_JUMP, CONFIG.elasticJumpStrength, AttributeModifier.Operation.ADD_VALUE));
            }
            JUMPING.add(entity);
        } else {
            jump.removeModifier(ELASTIC_JUMP);
            JUMPING.remove(entity);
        }
    }

    private static void tickJumps() {
        for (LivingEntity entity : List.copyOf(JUMPING)) {
            if (entity.isRemoved() || !isAffected(entity, ELASTIC)) {
                setJump(entity, false);
            }
        }
        for (Field field : FIELDS.values()) {
            if ((field.flags() & ELASTIC) != 0) {
                LivingEntity holder = field.holder();
                for (LivingEntity entity : holder.level().getEntitiesOfClass(LivingEntity.class, holder.getBoundingBox().inflate(getHalf(ELASTIC)), entity -> !HitRules.canHit(holder, entity))) {
                    setJump(entity, true);
                }
            }
        }
    }

    static boolean spikes(ServerLevel level, LivingEntity player, ManasSkillInstance instance, int mode) {
        if (EnergyHelper.isOutOfEnergy(player, instance, mode)) {
            return false;
        }

        NewOrderUtil.Aim aim = NewOrderUtil.aim(player, CONFIG.spikyRange);
        Vec3 center = aim.entity() != null ? aim.entity().position() : aim.point();
        boolean mastered = instance.isMastered(player);
        int count = Math.max(1, mastered ? CONFIG.spikyCountMastered : CONFIG.spikyCount);
        float damage = NewOrderUtil.ruleDamage(player, mastered ? CONFIG.spikyDamageMastered : CONFIG.spikyDamage);
        Vec3 away = center.subtract(player.position()).multiply(1.0D, 0.0D, 1.0D);
        away = away.lengthSqr() < 1.0E-4D ? player.getViewVector(1.0F).multiply(1.0D, 0.0D, 1.0D) : away;
        for (int i = 0; i < count; i++) {
            double angle = Mth.TWO_PI * i / count;
            Vec3 spot = count == 1 ? center : center.add(Math.cos(angle) * SPIKE_SPREAD, 0.0D, Math.sin(angle) * SPIKE_SPREAD);
            Vec3 lean = count == 1 ? away : center.subtract(spot);
            MudSpikeEntity spike = new MudSpikeEntity(level, player);
            spike.setPos(ObjectSelectionHelper.getNearestGround(spot, level, 5.0D, spike));
            spike.setSkill(instance);
            spike.setMode(mode);
            spike.setDamage(damage);
            spike.setSecondaryDamage(0.0F);
            spike.setYaw(SPIKE_LEAN);
            spike.setPitch(ObjectSelectionHelper.getYRotFromVector(lean.normalize()));
            level.addFreshEntity(spike);
            spike.triggerAnim("controller", "start");
        }

        player.swing(InteractionHand.MAIN_HAND, true);
        NewOrderUtil.playSound(level, center, TensuraSoundEvents.CAST_EARTH.get(), 1.2F, 0.9F);
        NewOrderUtil.callout(level, new Vec3(player.getX(), player.getY() + player.getBbHeight() + 0.8D, player.getZ()), player, NewOrderQuirk.getCalloutName(instance, mode), NewOrderRules.RULE_COLORS[NewOrderRule.GROUND]);
        if (level.random.nextInt(3) == 0) {
            instance.addMasteryPoint(player);
        }
        return true;
    }

    static void forget(LivingEntity entity) {
        FIELDS.remove(entity);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!FIELDS.isEmpty()) {
            FIELDS.keySet().removeIf(holder -> holder.isRemoved() || !holder.isAlive());
        }
        if ((!FIELDS.isEmpty() || !JUMPING.isEmpty()) && event.getServer().getTickCount() % JUMP_CHECK == 0) {
            tickJumps();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        FIELDS.clear();
        JUMPING.clear();
    }

}
