package com.radient.tensuraacadamia.ability.unique.quirks;

import com.radient.tensuraacadamia.regestry.MHAEffects;
import com.radient.tensuraacadamia.regestry.skills.QuirkSkills;
import com.radient.tensuraacadamia.TensuraAcadamia;
import io.github.manasmods.manascore.network.api.util.Changeable;
import io.github.manasmods.manascore.skill.api.EntityEvents;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.damage.TensuraDamageHelper;
import io.github.manasmods.tensura.enchantment.TensuraEnchantmentHelper;
import io.github.manasmods.tensura.enchantment.TensuraEnchantments;
import io.github.manasmods.tensura.storage.TensuraStorages;
import com.radient.tensuraacadamia.regestry.QuirkVisualItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class WoodenSwordsQuirk extends Skill {
    private static final int SPLINTER = 0;
    private static final int DREIDEL = 1;
    private static final int SPLINTER_SECONDS = 10;
    private static final int DEBUFF_SECONDS = 60;
    private static final int DREIDEL_TICKS = 10;
    private static final int DREIDEL_MAX_HITS = 10;
    private static final double DREIDEL_BLOCKS_PER_TICK = 1.0D;
    private static final double MELEE_DAMAGE_MULTIPLIER = 1.10D;
    private static final double MOVEMENT_DAMAGE_DISTANCE = 0.5D;
    private static final float BASE_SPLINTER_DAMAGE = 0.01F;
    private static final float DAMAGE_PER_STACK = 0.0015F;
    private static final int MAX_STACKS = 3;
    private static final String SPLINTER_UNTIL = "tracadamia_wooden_swords_splinter_until";
    private static final String STACKS_TAG = "tracadamia_splinter_stacks";
    private static final String LAST_X_TAG = "tracadamia_splinter_last_x";
    private static final String LAST_Y_TAG = "tracadamia_splinter_last_y";
    private static final String LAST_Z_TAG = "tracadamia_splinter_last_z";
    private static final String MOVED_TAG = "tracadamia_splinter_moved";
    private static final String DISTANCE_TAG = "tracadamia_splinter_distance";
    private static final Map<UUID, DreidelDash> DREIDEL_DASHES = new HashMap<>();
    private static boolean applyingDreidelDamage;

    private static final class DreidelDash {
        private final ServerPlayer owner;
        private final ManasSkillInstance instance;
        private final Vec3 direction;
        private final float startYaw;
        private int ticks;
        private int hits;

        private DreidelDash(ServerPlayer owner, ManasSkillInstance instance, Vec3 direction) {
            this.owner = owner;
            this.instance = instance;
            this.direction = direction;
            this.startYaw = owner.getYRot();
        }
    }

    public WoodenSwordsQuirk() {
        super(SkillType.UNIQUE);
    }

    @Override
    public @Nullable ResourceLocation getSkillIcon() {
        return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/woodenswordsfromhandsicon.png");
    }

    public static void registerSkillEvents() {
        EntityEvents.LIVING_POST_TICK.register(WoodenSwordsQuirk::onLivingTick);
    }

    @Override
    public double getDefaultAcquiringMagiculeCost() {
        return 0.0D;
    }

    @Override
    public boolean checkAcquiringRequirement(Player player, double cost) {
        return false;
    }

    @Override
    public int getModes(ManasSkillInstance instance) {
        return 2;
    }

    @Override
    public int nextMode(LivingEntity entity, ManasSkillInstance instance, int mode, boolean reverse) {
        return Math.floorMod(mode + (reverse ? -1 : 1), getModes(instance));
    }

    @Override
    public String getModeId(ManasSkillInstance instance, int mode) {
        return switch (mode) {
            case SPLINTER -> "wooden_swords.splinter";
            case DREIDEL -> "wooden_swords.dreidel";
            default -> super.getModeId(instance, mode);
        };
    }

    @Override
    public void onPressed(ManasSkillInstance instance, LivingEntity entity, int keyNumber, int mode) {
        if (!(entity instanceof ServerPlayer player) || mode < 0 || mode >= getModes(instance)) {
            return;
        }

        if (mode == SPLINTER) {
            long until = player.serverLevel().getGameTime() + SPLINTER_SECONDS * 20L;
            instance.getOrCreateTag().putLong(SPLINTER_UNTIL, until);
            instance.markDirty();
            player.displayClientMessage(Component.translatable("tracadamia.skill.wooden_swords.splinter_active"), true);
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.WOOD_BREAK, SoundSource.PLAYERS, 0.9F, 0.8F);
            player.serverLevel().sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + player.getBbHeight() * 0.65D,
                    player.getZ(), 8, 0.35D, 0.4D, 0.35D, 0.06D);
            instance.addMasteryPoint(player);
            return;
        }

        startDreidel(player, instance);
    }

    @Override
    public boolean onDamageEntity(ManasSkillInstance instance, LivingEntity owner, LivingEntity target,
                                  DamageSource source, Changeable<Float> amount) {
        if (applyingDreidelDamage || owner.level().isClientSide || source.getDirectEntity() != owner
                || !TensuraDamageHelper.isPhysicalAttack(source) || !isSlotted(owner)) {
            return true;
        }

        amount.set((amount.get() + 4.0F) * (float) MELEE_DAMAGE_MULTIPLIER);
        if (isSplinterActive(instance, owner)) {
            applySplinter(owner, target, false);
        }
        return true;
    }

    private static void startDreidel(ServerPlayer player, ManasSkillInstance instance) {
        if (DREIDEL_DASHES.containsKey(player.getUUID())) {
            return;
        }

        Vec3 direction = player.getLookAngle();
        if (direction.lengthSqr() < 1.0E-6D) {
            direction = Vec3.directionFromRotation(0.0F, player.getYRot());
        }

        DREIDEL_DASHES.put(player.getUUID(), new DreidelDash(player, instance, direction.normalize()));
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.7F);
        player.serverLevel().sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX(), player.getY() + 1.0D, player.getZ(), 2, 0.2D, 0.2D, 0.2D, 0.0D);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        Iterator<Map.Entry<UUID, DreidelDash>> iterator = DREIDEL_DASHES.entrySet().iterator();
        while (iterator.hasNext()) {
            DreidelDash dash = iterator.next().getValue();
            ServerPlayer owner = dash.owner;
            if (!owner.isAlive() || owner.isRemoved() || !(owner.level() instanceof ServerLevel level)) {
                iterator.remove();
                continue;
            }
            if (!isSlotted(owner)) {
                iterator.remove();
                continue;
            }

            if (dash.ticks++ >= DREIDEL_TICKS || dash.hits >= DREIDEL_MAX_HITS) {
                iterator.remove();
                continue;
            }

            AABB before = owner.getBoundingBox();
            Vec3 oldMotion = owner.getDeltaMovement();
            owner.move(MoverType.SELF, dash.direction.scale(DREIDEL_BLOCKS_PER_TICK));
            owner.setDeltaMovement(0.0D, oldMotion.y, 0.0D);
            owner.setYRot(dash.startYaw + 36.0F * dash.ticks);
            owner.hurtMarked = true;
            AABB swept = before.minmax(owner.getBoundingBox()).inflate(0.4D);
            double damage = (owner.getAttributeValue(Attributes.ATTACK_DAMAGE) + 4.0D) * MELEE_DAMAGE_MULTIPLIER;
            boolean splinterActive = isSplinterActive(dash.instance, owner);

            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, swept,
                    candidate -> candidate != owner && candidate.isAlive() && !candidate.isRemoved())) {
                if (dash.hits >= DREIDEL_MAX_HITS) {
                    break;
                }

                int invulnerableTime = target.invulnerableTime;
                applyingDreidelDamage = true;
                boolean hit;
                try {
                    target.invulnerableTime = 0;
                    hit = target.hurt(owner.damageSources().playerAttack(owner), Math.max(1.0F, (float) damage));
                } finally {
                    applyingDreidelDamage = false;
                    target.invulnerableTime = invulnerableTime;
                }

                if (hit) {
                    dash.hits++;
                    boolean maxStacks = splinterActive || target.hasEffect(MHAEffects.SPLINTER);
                    if (maxStacks) {
                        applySplinter(owner, target, maxStacks);
                    }
                    owner.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
                    level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.75F, 0.8F + owner.getRandom().nextFloat() * 0.2F);
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + target.getBbHeight() * 0.5D,
                            target.getZ(), 1, 0.0D, 0.1D, 0.0D, 0.0D);
                }
            }

        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        DREIDEL_DASHES.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        DREIDEL_DASHES.clear();
    }

    private static void onLivingTick(LivingEntity entity) {
        if (entity.level().isClientSide || !entity.hasEffect(MHAEffects.SPLINTER)) {
            if (!entity.level().isClientSide && entity.getPersistentData().contains(STACKS_TAG)) {
                clearSplinterTracking(entity);
            }
            return;
        }

        CompoundTag data = entity.getPersistentData();
        Vec3 position = entity.position();
        if (!data.getBoolean(MOVED_TAG)) {
            data.putBoolean(MOVED_TAG, true);
            savePosition(data, position);
            return;
        }

        Vec3 previous = new Vec3(data.getDouble(LAST_X_TAG), data.getDouble(LAST_Y_TAG), data.getDouble(LAST_Z_TAG));
        savePosition(data, position);
        double distance = position.distanceTo(previous);
        double moved = data.getDouble(DISTANCE_TAG) + distance;
        if (moved >= MOVEMENT_DAMAGE_DISTANCE) {
            applySplinterDamage(entity);
            moved %= MOVEMENT_DAMAGE_DISTANCE;
        }
        data.putDouble(DISTANCE_TAG, moved);
    }

    @SubscribeEvent
    public static void onLivingDamaged(LivingDamageEvent.Post event) {
        if (event.getEntity().level().isClientSide || event.getNewDamage() <= 0.0F) {
            return;
        }

        if (event.getSource().getEntity() instanceof LivingEntity attacker
                && attacker != event.getEntity() && attacker.hasEffect(MHAEffects.SPLINTER)) {
            applySplinterDamage(attacker);
        }
    }

    private static void applySplinter(LivingEntity owner, LivingEntity target, boolean maxStacks) {
        if (!QuirkCastCosts.hasAura(owner, 1000)) return;
        int oldStacks = target.hasEffect(MHAEffects.SPLINTER)
                ? Math.max(1, target.getPersistentData().getInt(STACKS_TAG)) : 0;
        int stacks = maxStacks ? MAX_STACKS : Math.min(MAX_STACKS, oldStacks + 1);
        if (!target.addEffect(new net.minecraft.world.effect.MobEffectInstance(MHAEffects.SPLINTER,
                DEBUFF_SECONDS * 20, 0, false, true, true)) && !target.hasEffect(MHAEffects.SPLINTER)) return;
        target.getPersistentData().putInt(STACKS_TAG, stacks);
        QuirkCastCosts.spendAura(owner, 1000);
        if (stacks > oldStacks && target instanceof Player player) {
            player.displayClientMessage(Component.translatable("tracadamia.skill.wooden_swords.splinter_stacks", stacks, MAX_STACKS), true);
        }
        if (target.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.5D,
                    target.getZ(), 5 + stacks, 0.35D, 0.35D, 0.35D, 0.04D);
        }
    }

    private static void applySplinterDamage(LivingEntity victim) {
        if (!victim.isAlive() || victim.isRemoved() || !victim.hasEffect(MHAEffects.SPLINTER)) {
            return;
        }

        int stacks = Math.clamp(victim.getPersistentData().getInt(STACKS_TAG), 1, MAX_STACKS);
        float percent = BASE_SPLINTER_DAMAGE + (stacks - 1) * DAMAGE_PER_STACK;
        float damage = Math.max(0.01F, victim.getMaxHealth() * percent);
        victim.hurt(victim.damageSources().magic(), damage);
    }

    private static void savePosition(CompoundTag data, Vec3 position) {
        data.putDouble(LAST_X_TAG, position.x);
        data.putDouble(LAST_Y_TAG, position.y);
        data.putDouble(LAST_Z_TAG, position.z);
    }

    private static void clearSplinterTracking(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        data.remove(STACKS_TAG);
        data.remove(LAST_X_TAG);
        data.remove(LAST_Y_TAG);
        data.remove(LAST_Z_TAG);
        data.remove(MOVED_TAG);
        data.remove(DISTANCE_TAG);
    }

    private static boolean isSlotted(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.WOODEN_SWORDS.get()).isPresent()
                && TensuraStorages.getAbilityFrom(entity).isAbilityInActivePreset(QuirkSkills.WOODEN_SWORDS.get());
    }

    public static Optional<ManasSkillInstance> getOwnedInstance(LivingEntity entity) {
        return SkillAPI.getSkillsFrom(entity).getSkill(QuirkSkills.WOODEN_SWORDS.get());
    }

    public static boolean isSlottedForRender(LivingEntity entity) {
        return isSlotted(entity);
    }

    public static boolean hasMasteredIntangibility(LivingEntity entity) {
        return getOwnedInstance(entity).filter(instance -> instance.isMastered(entity)).isPresent()
                && TensuraStorages.getAbilityFrom(entity).isAbilityInActivePreset(QuirkSkills.WOODEN_SWORDS.get());
    }

    public static ItemStack getSwordStackForRender(LivingEntity entity) {
        Optional<ManasSkillInstance> skill = getOwnedInstance(entity);
        ItemStack sword = skill.isPresent() && isSplinterActive(skill.get(), entity)
                ? new ItemStack(QuirkVisualItems.SPLINTER_SWORD_VISUAL.get())
                : new ItemStack(Items.WOODEN_SWORD);
        applyMasteryEngraving(sword, entity, skill);
        return sword;
    }

    public static ItemStack getIntangibilityWeaponStack(LivingEntity entity) {
        ItemStack sword = new ItemStack(Items.WOODEN_SWORD);
        applyMasteryEngraving(sword, entity, getOwnedInstance(entity));
        return sword;
    }

    private static void applyMasteryEngraving(ItemStack sword, LivingEntity entity,
                                               Optional<ManasSkillInstance> skill) {
        if (skill.isEmpty() || !skill.get().isMastered(entity)
                || !TensuraStorages.getAbilityFrom(entity).isAbilityInActivePreset(QuirkSkills.WOODEN_SWORDS.get())) {
            return;
        }

        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(
                sword.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
        enchantments.set(TensuraEnchantmentHelper.getEnchantment(entity.level(), TensuraEnchantments.INTANGIBILITY), 1);
        EnchantmentHelper.setEnchantments(sword, enchantments.toImmutable());
    }

    public static boolean isSplinterActive(ManasSkillInstance instance, LivingEntity owner) {
        CompoundTag tag = instance.getTag();
        return tag != null && tag.getLong(SPLINTER_UNTIL) > owner.level().getGameTime();
    }

}
