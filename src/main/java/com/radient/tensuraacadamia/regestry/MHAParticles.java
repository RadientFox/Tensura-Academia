package com.radient.tensuraacadamia.regestry;

import com.radient.tensuraacadamia.TensuraAcadamia;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.particle.CherryParticle;
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
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> OFA_COWLING_1 =
            PARTICLES.register("ofa_cowling_1", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> OFA_COWLING_2 =
            PARTICLES.register("ofa_cowling_2", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> QUEEN_HEART =
            PARTICLES.register("queen_heart", () -> new SimpleParticleType(false));
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
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ENGINE_SPARK =
            PARTICLES.register("engine_spark", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VOICE_CANNON =
            PARTICLES.register("voice_cannon", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> VOICE_CANNON_WIDE =
            PARTICLES.register("voice_cannon_wide", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> OFA_1_COWL =
            PARTICLES.register("ofa_1_fullcowl", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLAZE_ROD_BEAM =
            PARTICLES.register("blaze_rod_beam", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLAZE_ROD_BEAM_WIDE =
            PARTICLES.register("blaze_rod_beam_wide", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> IMPURE_BEAM =
            PARTICLES.register("impure_beam", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLAZE_ROD_BEAM_OVERPOWER =
            PARTICLES.register("blaze_rod_beam_overpower", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> IMPURE_BEAM_OVERPOWER =
            PARTICLES.register("impure_beam_overpower", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GREEN_LEAF =
            PARTICLES.register("green_leaf", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> PORTAL_FOG =
            PARTICLES.register("portal_fog", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BEAM_CLASH =
            PARTICLES.register("beam_clash", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLAST_FLASH =
            PARTICLES.register("blast_flash", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLAST_CHARGE =
            PARTICLES.register("blast_charge", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> EXPLOSION_IMPACT =
            PARTICLES.register("explosion_impact", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ELECTRIC_LINK =
            PARTICLES.register("electric_link", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ELECTRIC_SHELL =
            PARTICLES.register("electric_shell", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> WIND_VORTEX =
            PARTICLES.register("wind_vortex", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> WIND_RELEASE =
            PARTICLES.register("wind_release", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BARRIER_SHARDS =
            PARTICLES.register("barrier_shards", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ACID_SPLASH =
            PARTICLES.register("acid_splash", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ACID_CHARGE =
            PARTICLES.register("acid_charge", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ACID_SWEAT_BLAST =
            PARTICLES.register("acid_sweat_blast", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> STOCKPILE_RELEASE =
            PARTICLES.register("stockpile_release", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> TELEPORT_FLASH =
            PARTICLES.register("teleport_flash", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> PHASE_RIPPLE =
            PARTICLES.register("phase_ripple", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLOOD_PARALYSIS =
            PARTICLES.register("blood_paralysis", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> WOOD_SLASH =
            PARTICLES.register("wood_slash", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FIRE_STREAM =
            PARTICLES.register("fire_stream", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FIRE_SHELL =
            PARTICLES.register("fire_shell", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FROST_BURST =
            PARTICLES.register("frost_burst", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> PALE_BLADE =
            PARTICLES.register("pale_blade", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FROST_FRONT =
            PARTICLES.register("frost_front", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DUST_WAVE =
            PARTICLES.register("dust_wave", () -> new SimpleParticleType(false));


    private MHAParticles() {
    }

    public static void init(IEventBus modEventBus) {
        PARTICLES.register(modEventBus);
    }

    @EventBusSubscriber(modid = TensuraAcadamia.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Client {
        @SubscribeEvent
        public static void registerProviders(RegisterParticleProvidersEvent event) {
            event.registerSpecial(FIRE_STREAM.get(), new com.radient.tensuraacadamia.quirks.half_cold_half_hot.ThermalVfxParticle.Provider(com.radient.tensuraacadamia.quirks.half_cold_half_hot.ThermalVfxParticle.Kind.FIRE_STREAM));
            event.registerSpecial(FIRE_SHELL.get(), new com.radient.tensuraacadamia.quirks.half_cold_half_hot.ThermalVfxParticle.Provider(com.radient.tensuraacadamia.quirks.half_cold_half_hot.ThermalVfxParticle.Kind.FIRE_SHELL));
            event.registerSpecial(FROST_BURST.get(), new com.radient.tensuraacadamia.quirks.half_cold_half_hot.ThermalVfxParticle.Provider(com.radient.tensuraacadamia.quirks.half_cold_half_hot.ThermalVfxParticle.Kind.FROST_BURST));
            event.registerSpecial(PALE_BLADE.get(), new com.radient.tensuraacadamia.quirks.half_cold_half_hot.ThermalVfxParticle.Provider(com.radient.tensuraacadamia.quirks.half_cold_half_hot.ThermalVfxParticle.Kind.PALE_BLADE));
            event.registerSpecial(FROST_FRONT.get(), new com.radient.tensuraacadamia.quirks.half_cold_half_hot.ThermalVfxParticle.Provider(com.radient.tensuraacadamia.quirks.half_cold_half_hot.ThermalVfxParticle.Kind.FROST_FRONT));
            event.registerSpecial(DUST_WAVE.get(), new com.radient.tensuraacadamia.quirks.assault_dust.DustWaveParticle.Provider());
            event.registerSpecial(STOCKPILE_RELEASE.get(), new com.radient.tensuraacadamia.quirks.shared.QuirkFeedbackParticle.Provider(com.radient.tensuraacadamia.quirks.shared.QuirkFeedbackParticle.Kind.STOCKPILE));
            event.registerSpecial(TELEPORT_FLASH.get(), new com.radient.tensuraacadamia.quirks.shared.QuirkFeedbackParticle.Provider(com.radient.tensuraacadamia.quirks.shared.QuirkFeedbackParticle.Kind.TELEPORT));
            event.registerSpecial(PHASE_RIPPLE.get(), new com.radient.tensuraacadamia.quirks.shared.QuirkFeedbackParticle.Provider(com.radient.tensuraacadamia.quirks.shared.QuirkFeedbackParticle.Kind.PHASE));
            event.registerSpecial(BLOOD_PARALYSIS.get(), new com.radient.tensuraacadamia.quirks.shared.QuirkFeedbackParticle.Provider(com.radient.tensuraacadamia.quirks.shared.QuirkFeedbackParticle.Kind.BLOOD));
            event.registerSpecial(WOOD_SLASH.get(), new com.radient.tensuraacadamia.quirks.shared.QuirkFeedbackParticle.Provider(com.radient.tensuraacadamia.quirks.shared.QuirkFeedbackParticle.Kind.WOOD_SLASH));
            event.registerSpecial(ACID_SWEAT_BLAST.get(), new com.radient.tensuraacadamia.quirks.acid_sweat.AcidSweatClient.BlastProvider());
            event.registerSpecial(ACID_SPLASH.get(), new com.radient.tensuraacadamia.quirks.acid.AcidVfxParticle.Provider(false));
            event.registerSpecial(ACID_CHARGE.get(), new com.radient.tensuraacadamia.quirks.acid.AcidVfxParticle.Provider(true));
            event.registerSpecial(QUEEN_HEART.get(), new com.radient.tensuraacadamia.quirks.queen_beam.QueenHeartParticle.Provider());
            event.registerSpriteSet(SMASH_PARTICLE.get(), SmashParticle.Provider::new);
            event.registerSpriteSet(OFA_COWLING_1.get(),
                    io.github.manasmods.tensura.particle.type.LightningSparkParticle.Provider::new);
            event.registerSpriteSet(OFA_COWLING_2.get(),
                    io.github.manasmods.tensura.particle.type.LightningSparkParticle.Provider::new);
            event.registerSpecial(ELECTRIC_ARC.get(), new com.radient.tensuraacadamia.quirks.shared.QuirkVfxParticle.Provider(2, .28));
            event.registerSpecial(ELECTRIC_FIELD.get(), new com.radient.tensuraacadamia.quirks.shared.QuirkVfxParticle.Provider(3, .55));
            event.registerSpecial(ELECTRIC_TRAIL.get(), new com.radient.tensuraacadamia.quirks.shared.QuirkVfxParticle.Provider(4, .24));
            event.registerSpecial(ELECTRIC_LINK.get(), new com.radient.tensuraacadamia.quirks.shared.QuirkVfxParticle.Provider(10, 1));
            event.registerSpecial(ELECTRIC_SHELL.get(), new com.radient.tensuraacadamia.quirks.shared.QuirkVfxParticle.Provider(11, 1));
            event.registerSpriteSet(SMOKESCREEN.get(), sprites -> new SmokeParticle.Provider(sprites, 1.0F));
            event.registerSpriteSet(SMOKESCREEN_SELF.get(), sprites -> new SmokeParticle.Provider(sprites, 0.2F));
            event.registerSpecial(PORTAL_FOG.get(), new com.radient.tensuraacadamia.quirks.shared.SkillEffectParticle.Provider(1, 1));
            event.registerSpecial(BEAM_CLASH.get(), new com.radient.tensuraacadamia.quirks.shared.SkillEffectParticle.Provider(2, 1));
            event.registerSpecial(BLAST_FLASH.get(), new com.radient.tensuraacadamia.quirks.shared.SkillEffectParticle.Provider(3, 1));
            event.registerSpecial(BLAST_CHARGE.get(), new com.radient.tensuraacadamia.quirks.shared.SkillEffectParticle.Provider(4, 1));
            event.registerSpecial(ORANGE_EXPLOSION.get(), new com.radient.tensuraacadamia.quirks.shared.QuirkVfxParticle.Provider(0, .8));
            event.registerSpecial(SMALL_ORANGE_EXPLOSION.get(), new com.radient.tensuraacadamia.quirks.shared.QuirkVfxParticle.Provider(0, .35));
            event.registerSpecial(EXPLOSION_IMPACT.get(), new com.radient.tensuraacadamia.quirks.shared.QuirkVfxParticle.Provider(1, 1));
            event.registerSpecial(WIND_VORTEX.get(), new com.radient.tensuraacadamia.quirks.shared.QuirkVfxParticle.Provider(7, 1));
            event.registerSpecial(WIND_RELEASE.get(), new com.radient.tensuraacadamia.quirks.shared.QuirkVfxParticle.Provider(8, 1));
            event.registerSpecial(BARRIER_SHARDS.get(), new com.radient.tensuraacadamia.quirks.shared.QuirkVfxParticle.Provider(9, 1));
            event.registerSpriteSet(ENGINE_SPARK.get(), EngineSparkParticle.Provider::new);
            event.registerSpecial(VOICE_CANNON.get(), new com.radient.tensuraacadamia.quirks.shared.QuirkVfxParticle.Provider(5, 2.5));
            event.registerSpecial(VOICE_CANNON_WIDE.get(), new com.radient.tensuraacadamia.quirks.shared.QuirkVfxParticle.Provider(5, 7.5));

            event.registerSpriteSet(OFA_1_COWL.get(), SmashParticle.Provider::new);
            event.registerSpriteSet(BLAZE_ROD_BEAM.get(),
                    sprites -> new com.radient.tensuraacadamia.quirks.shared.EyeBeamParticle.Provider(
                            sprites, 100.0D, 0.3D, 0xFFFF0000));
            event.registerSpriteSet(BLAZE_ROD_BEAM_WIDE.get(),
                    sprites -> new com.radient.tensuraacadamia.quirks.shared.EyeBeamParticle.Provider(
                            sprites, 240.0D, 15.0D, 0xFFFF0000));
            event.registerSpriteSet(IMPURE_BEAM.get(),
                    sprites -> new com.radient.tensuraacadamia.quirks.shared.EyeBeamParticle.Provider(
                            sprites, 240.0D, 0.36D, 0xFFFFD52A));
            event.registerSpriteSet(BLAZE_ROD_BEAM_OVERPOWER.get(),
                    sprites -> new com.radient.tensuraacadamia.quirks.shared.EyeBeamParticle.Provider(
                            sprites, 240.0D, 30.0D, 0xFFFF0000));
            event.registerSpriteSet(IMPURE_BEAM_OVERPOWER.get(),
                    sprites -> new com.radient.tensuraacadamia.quirks.shared.EyeBeamParticle.Provider(
                            sprites, 240.0D, 1.08D, 0xFFFFD52A));
            event.registerSpriteSet(GREEN_LEAF.get(), GreenLeafParticle.Provider::new);
        }
    }

    private static final class GreenLeafParticle extends CherryParticle {
        private GreenLeafParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level, x, y, z, sprites);
            setColor(0.16F, 1.0F, 0.14F);
        }

        private static final class Provider implements ParticleProvider<SimpleParticleType> {
            private final SpriteSet sprites;

            private Provider(SpriteSet sprites) { this.sprites = sprites; }

            @Override public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                                      double x, double y, double z,
                                                      double xSpeed, double ySpeed, double zSpeed) {
                return new GreenLeafParticle(level, x, y, z, sprites);
            }
        }
    }

    private static final class EngineSparkParticle extends TextureSheetParticle {
        private EngineSparkParticle(ClientLevel level, double x, double y, double z,
                                    double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
            super(level, x, y, z, xSpeed, ySpeed, zSpeed);
            this.pickSprite(sprites);
            this.lifetime = 6 + level.random.nextInt(4);
            this.quadSize = 0.055F + level.random.nextFloat() * 0.020F;
            this.gravity = 0.0F;
            this.friction = 0.90F;
            this.hasPhysics = false;
            this.xd = xSpeed;
            this.yd = ySpeed;
            this.zd = zSpeed;
            this.setColor(1.0F, 0.40F, 0.08F);
            this.setAlpha(0.88F);
        }

        @Override
        public void tick() {
            super.tick();
            this.alpha = 0.88F * (1.0F - (float) this.age / this.lifetime);
            this.quadSize *= 0.94F;
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

            private Provider(SpriteSet sprites) {
                this.sprites = sprites;
            }

            @Override
            public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                           double x, double y, double z,
                                           double xSpeed, double ySpeed, double zSpeed) {
                return new EngineSparkParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
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

    private static final class OFA1CowlParticle extends TextureSheetParticle {
        private OFA1CowlParticle(ClientLevel level, double x, double y, double z,
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
