package com.radient.tensuraacadamia.entity;

import com.radient.tensuraacadamia.TensuraAcadamia;
import io.github.manasmods.tensura.entity.projectile.magic.SolarGrenadeProjectile;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.Optional;

@EventBusSubscriber(modid = TensuraAcadamia.MODID)
public class LightBallProjectile extends SolarGrenadeProjectile {

    public static final DeferredHolder<EntityType<?>, EntityType<LightBallProjectile>> TYPE = DeferredHolder.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(TensuraAcadamia.MODID, "light_ball"));

    public LightBallProjectile(EntityType<? extends LightBallProjectile> type, Level level) {
        super(type, level);
    }

    public LightBallProjectile(Level level, LivingEntity owner) {
        this(TYPE.get(), level);
        setOwner(owner);
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.ENTITY_TYPE, TYPE.getId(), () -> EntityType.Builder.<LightBallProjectile>of(LightBallProjectile::new, MobCategory.MISC)
                .sized(0.4F, 0.4F)
                .clientTrackingRange(4)
                .updateInterval(10)
                .noSummon()
                .build(TYPE.getId().toString()));
    }

    @Override
    public void onExplosion(double x, double y, double z) {
    }

    @Override
    public Optional<SoundEvent> hitSound() {
        return Optional.of(SoundEvents.FIREWORK_ROCKET_BLAST);
    }

}
