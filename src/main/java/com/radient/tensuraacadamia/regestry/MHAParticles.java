package com.radient.tensuraacadamia.regestry;

import com.radient.tensuraacadamia.TensuraAcadamia;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class MHAParticles {

    private static final DeferredRegister<ParticleType<?>> PARTICLES;
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SMASH_PARTICLE;

    public MHAParticles() {
    }

    public static void init(IEventBus modEventBus) {
        PARTICLES.register(modEventBus);
    }

    static {
        PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, "tracadamia");
        SMASH_PARTICLE = PARTICLES.register("smash_particles_1", () -> {
            return new SimpleParticleType(false);
        });
    }

    }
