package com.radient.tensuraacadamia.regestry;

import com.radient.tensuraacadamia.TensuraAcadamia;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MHAParticles {
    private static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, "tracadamia");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SMASH_PARTICLE =
            PARTICLES.register("smash_particles_1", () -> new SimpleParticleType(false));

    private MHAParticles() {
    }

    public static void init(IEventBus modEventBus) {
        PARTICLES.register(modEventBus);
    }

    @EventBusSubscriber(modid = TensuraAcadamia.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Client {
        @SubscribeEvent
        public static void registerProviders(RegisterParticleProvidersEvent event) {
            event.registerSpriteSet(SMASH_PARTICLE.get(), SmashParticle.Provider::new);
        }
    }

    private static final class SmashParticle extends TextureSheetParticle {
        private SmashParticle(ClientLevel level, double x, double y, double z,
                              double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
            super(level, x, y, z, xSpeed, ySpeed, zSpeed);
            this.pickSprite(sprites);
            this.lifetime = 12;
            this.quadSize = 0.35F;
            this.gravity = 0.0F;
            this.hasPhysics = false;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        private static final class Provider implements ParticleProvider<SimpleParticleType> {
            private final SpriteSet sprites;

            private Provider(SpriteSet sprites) {
                this.sprites = sprites;
            }

            @Override
            public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                           double x, double y, double z,
                                           double xSpeed, double ySpeed, double zSpeed) {
                return new SmashParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
            }
        }
    }
}
