package com.radient.tensuraacadamia.regestry;

import com.radient.tensuraacadamia.TensuraAcadamia;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.particle.HugeExplosionParticle;
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
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ELECTRIC_ARC =
            PARTICLES.register("electric_arc", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ELECTRIC_FIELD =
            PARTICLES.register("electric_field", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ELECTRIC_TRAIL =
            PARTICLES.register("electric_trail", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SMOKESCREEN =
            PARTICLES.register("smokescreen", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SMOKESCREEN_SELF =
            PARTICLES.register("smokescreen_self", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ORANGE_EXPLOSION =
            PARTICLES.register("orange_explosion", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SMALL_ORANGE_EXPLOSION =
            PARTICLES.register("small_orange_explosion", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VOICE_CANNON =
            PARTICLES.register("voice_cannon", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VOICE_CANNON_WIDE =
            PARTICLES.register("voice_cannon_wide", () -> new SimpleParticleType(false));

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
            event.registerSpriteSet(ELECTRIC_ARC.get(), sprites -> new ElectricParticle.Provider(sprites, false));
            event.registerSpriteSet(ELECTRIC_FIELD.get(), sprites -> new ElectricParticle.Provider(sprites, true));
            event.registerSpriteSet(ELECTRIC_TRAIL.get(), sprites -> new ElectricParticle.Provider(sprites, 2));
            event.registerSpriteSet(SMOKESCREEN.get(), sprites -> new SmokeParticle.Provider(sprites, 1.0F));
            event.registerSpriteSet(SMOKESCREEN_SELF.get(), sprites -> new SmokeParticle.Provider(sprites, 0.2F));
            event.registerSpriteSet(ORANGE_EXPLOSION.get(),
                    sprites -> new OrangeExplosionParticle.Provider(sprites, 0.62F, 0.18F));
            event.registerSpriteSet(SMALL_ORANGE_EXPLOSION.get(),
                    sprites -> new OrangeExplosionParticle.Provider(sprites, 0.38F, 0.12F));
            event.registerSpriteSet(VOICE_CANNON.get(), sprites -> new VoiceCannonParticle.Provider(sprites, 5.0F));
            event.registerSpriteSet(VOICE_CANNON_WIDE.get(), sprites -> new VoiceCannonParticle.Provider(sprites, 15.0F));
        }
    }

    private static final class OrangeExplosionParticle extends TextureSheetParticle {
        private final SpriteSet sprites;

        private OrangeExplosionParticle(ClientLevel level, double x, double y, double z,
                                        double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites,
                                        float baseSize, float sizeVariation) {
            super(level, x, y, z, xSpeed, ySpeed, zSpeed);
            this.sprites = sprites;
            this.lifetime = 16;
            this.quadSize = baseSize + level.random.nextFloat() * sizeVariation;
            this.gravity = 0.0F;
            this.hasPhysics = false;
            this.setColor(1.0F, 0.44F, 0.12F);
            this.setAlpha(1.0F);
            this.setParticleSpeed(0.0D, 0.0D, 0.0D);
            this.setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            this.setSpriteFromAge(sprites);
        }

        @Override
        public int getLightColor(float partialTick) {
            return 0xF000F0;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        private static final class Provider implements ParticleProvider<SimpleParticleType> {
            private final SpriteSet sprites;
            private final float baseSize;
            private final float sizeVariation;

            private Provider(SpriteSet sprites, float baseSize, float sizeVariation) {
                this.sprites = sprites;
                this.baseSize = baseSize;
                this.sizeVariation = sizeVariation;
            }

            @Override
            public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                           double x, double y, double z,
                                           double xSpeed, double ySpeed, double zSpeed) {
                return new OrangeExplosionParticle(level, x, y, z, xSpeed, ySpeed, zSpeed,
                        sprites, baseSize, sizeVariation);
            }
        }
    }

    /**
     * The Warden's actual sonic-boom particle, using its animated wave geometry and
     * sprite timing, recolored for Voice rather than approximated with a flat sprite.
     */
    private static final class VoiceCannonParticle extends HugeExplosionParticle {
        private final SpriteSet sprites;

        private VoiceCannonParticle(ClientLevel level, double x, double y, double z,
                                    double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites, float diameter) {
            super(level, x, y, z, xSpeed, sprites);
            this.sprites = sprites;
            this.lifetime = 16;
            this.quadSize = diameter;
            // The copied Warden frames are already yellow. Keep their native color
            // rather than multiplying a cyan texture with a runtime tint.
            this.setColor(1.0F, 1.0F, 1.0F);
            this.setAlpha(1.0F);
            this.setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            // This is the exact age-to-frame progression used by SonicBoomParticle.
            super.tick();
        }

        private static final class Provider implements ParticleProvider<SimpleParticleType> {
            private final SpriteSet sprites;
            private final float diameter;

            private Provider(SpriteSet sprites, float diameter) {
                this.sprites = sprites;
                this.diameter = diameter;
            }

            @Override
            public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                           double x, double y, double z,
                                           double xSpeed, double ySpeed, double zSpeed) {
                return new VoiceCannonParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites, diameter);
            }
        }
    }

    private static final class ElectricParticle extends TextureSheetParticle {
        private ElectricParticle(ClientLevel level, double x, double y, double z,
                                 double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites, int variant) {
            super(level, x, y, z, xSpeed, ySpeed, zSpeed);
            this.pickSprite(sprites);
            this.lifetime = variant == 2 ? 24 : variant == 1 ? 9 : 7;
            this.quadSize = variant == 2 ? 0.27F : variant == 1 ? 0.55F : 0.18F;
            this.gravity = 0.0F;
            this.hasPhysics = false;
            this.roll = level.random.nextFloat() * 6.2831855F;
            this.oRoll = this.roll;
        }

        @Override
        public void tick() {
            super.tick();
            this.alpha = Math.max(0.0F, 1.0F - (float) this.age / this.lifetime);
        }

        @Override
        public int getLightColor(float partialTick) {
            return 0xF000F0;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        private static final class Provider implements ParticleProvider<SimpleParticleType> {
            private final SpriteSet sprites;
            private final int variant;

            private Provider(SpriteSet sprites, boolean field) {
                this(sprites, field ? 1 : 0);
            }

            private Provider(SpriteSet sprites, int variant) {
                this.sprites = sprites;
                this.variant = variant;
            }

            @Override
            public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                           double x, double y, double z,
                                           double xSpeed, double ySpeed, double zSpeed) {
                return new ElectricParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites, variant);
            }
        }
    }

    private static final class SmokeParticle extends TextureSheetParticle {
        private final SpriteSet sprites;
        private final float opacity;

        private SmokeParticle(ClientLevel level, double x, double y, double z,
                              double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites, float opacity) {
            super(level, x, y, z, xSpeed, ySpeed, zSpeed);
            this.sprites = sprites;
            this.opacity = opacity;
            this.lifetime = 36 + level.random.nextInt(20);
            this.quadSize = 2.4F + level.random.nextFloat() * 1.4F;
            this.gravity = 0.0F;
            this.hasPhysics = false;
            this.xd = xSpeed;
            this.yd = ySpeed;
            this.zd = zSpeed;
            this.alpha = 0.0F;
            this.setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            this.setSpriteFromAge(sprites);
            this.alpha = opacity * Math.min(0.85F, Math.min(this.age / 8.0F,
                    (this.lifetime - this.age) / 10.0F));
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        private static final class Provider implements ParticleProvider<SimpleParticleType> {
            private final SpriteSet sprites;
            private final float opacity;

            private Provider(SpriteSet sprites, float opacity) {
                this.sprites = sprites;
                this.opacity = opacity;
            }

            @Override
            public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                           double x, double y, double z,
                                           double xSpeed, double ySpeed, double zSpeed) {
                return new SmokeParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites, opacity);
            }
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
