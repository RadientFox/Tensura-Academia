package com.radient.tensuraacadamia.quirks.barrier;

import com.radient.tensuraacadamia.TensuraAcadamia;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import io.github.manasmods.tensura.ability.skill.Skill;
import io.github.manasmods.tensura.storage.TensuraStorages;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public final class BarrierQuirk extends Skill {
    public static final int[] SIZES = {3, 5, 10, 20, 30};
    public static final double AURA_PER_DAMAGE = 5;
    public static final String SIZE = "BarrierSize", GENERATION = "BarrierGeneration";
    public BarrierQuirk() { super(SkillType.UNIQUE); }
    @Override public int getMaxMastery() { return 2500; }
    @Override public double getDefaultAcquiringMagiculeCost() { return 0; }
    @Override public boolean checkAcquiringRequirement(Player player, double cost) { return false; }
    @Override public MutableComponent getSkillDescription() { return Component.translatable("tracadamia.skill.barrier.description"); }
    @Override public ResourceLocation getSkillIcon() { return ResourceLocation.fromNamespaceAndPath("tracadamia", "textures/skill/unique/barriericon.png"); }
    @Override public int getModes(ManasSkillInstance instance) { return 2; }
    @Override public int nextMode(LivingEntity owner, ManasSkillInstance instance, int mode, boolean reverse) { return Math.floorMod(mode + (reverse ? -1 : 1), 2); }
    @Override public String getModeId(ManasSkillInstance instance, int mode) { return mode == 1 ? "barrier.remove" : "barrier.summon"; }
    public static int size(ManasSkillInstance instance) { return SIZES[Math.clamp(instance.getOrCreateTag().getInt(SIZE), 0, instance.getMastery() >= 2500 ? 4 : 2)]; }
    @Override public void onPressed(ManasSkillInstance instance, LivingEntity owner, int key, int mode) {
        if (!(owner.level() instanceof ServerLevel level) || !owner.isAlive() || mode < 0 || mode > 1) return;
        if (mode == 1) {
            instance.getOrCreateTag().putInt(GENERATION, instance.getOrCreateTag().getInt(GENERATION) + 1);
            instance.markDirty();
            for (var world : level.getServer().getAllLevels())
                java.util.stream.StreamSupport.stream(world.getAllEntities().spliterator(), false)
                        .filter(entity -> entity instanceof DomeBarrier dome && owner.getUUID().equals(dome.creatorId()))
                        .toList().forEach(net.minecraft.world.entity.Entity::discard);
            return;
        }
        if (owner.isShiftKeyDown()) {
            int choices = instance.getMastery() >= getMaxMastery() ? 5 : 3;
            instance.getOrCreateTag().putInt(SIZE, Math.floorMod(instance.getOrCreateTag().getInt(SIZE) + 1, choices));
            instance.markDirty();
            if (owner instanceof Player player) player.displayClientMessage(Component.literal("Barrier: " + size(instance) + " × " + size(instance)), true);
            return;
        }
        if (owner.getHealth() < owner.getMaxHealth() * .2F) {
            if (owner instanceof Player player) player.displayClientMessage(Component.literal("Barrier requires at least 20% health."), true);
            return;
        }
        if (TensuraStorages.getExistenceFrom(owner).getAura() <= 0) return;
        var dome = new DomeBarrier(level, owner, size(instance), instance.getMastery() >= getMaxMastery(), instance.getOrCreateTag().getInt(GENERATION));
        if (level.addFreshEntity(dome)) instance.addMasteryPoint(owner);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void incoming(LivingIncomingDamageEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level) || event.getAmount() <= 0
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        var source = event.getSource();
        var direct = source.getDirectEntity();
        var from = direct == null ? source.getSourcePosition() : direct.getBoundingBox().getCenter();
        if (from != null && DomeBarrier.blockDamage(level, from, event.getEntity().getBoundingBox().getCenter(), source, event.getAmount())) event.setCanceled(true);
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void projectile(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof Projectile projectile && projectile.level() instanceof ServerLevel level
                && DomeBarrier.blockProjectile(level, projectile)) event.setCanceled(true);
    }
    @SubscribeEvent public static void dimension(EntityTravelToDimensionEvent event) {
        var entity = event.getEntity();
        if (!entity.isSpectator() && entity.level() instanceof ServerLevel level
                && !level.getEntitiesOfClass(DomeBarrier.class, entity.getBoundingBox(), dome -> dome.contains(entity.getBoundingBox().getCenter())).isEmpty()) event.setCanceled(true);
    }
}
