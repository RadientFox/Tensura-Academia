package com.radient.tensuraacadamia.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.radient.tensuraacadamia.TensuraAcadamia;
import com.radient.tensuraacadamia.ability.unique.quirks.ImpactRecoilQuirk;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

// Impact Recoil shockwave
@EventBusSubscriber(modid = TensuraAcadamia.MODID, value = Dist.CLIENT)
public class ImpactShockwaveParticle extends TextureSheetParticle {

    private static final float SIZE = 1.5F;
    private static final float START_ALPHA = 0.75F;

    private final SpriteSet sprites;
    private final Quaternionf front;
    private final Quaternionf back;

    private ImpactShockwaveParticle(ClientLevel level, double x, double y, double z, double normalX, double normalY, double normalZ, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.lifetime = 10;
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.xd = 0.0D;
        this.yd = 0.0D;
        this.zd = 0.0D;
        this.rCol = 1.0F;
        this.gCol = 1.0F;
        this.bCol = 1.0F;
        this.front = new Quaternionf().rotationTo(0.0F, 0.0F, 1.0F, (float) normalX, (float) normalY, (float) normalZ);
        this.back = new Quaternionf(this.front).rotateY((float) Math.PI);
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        float progress = Mth.clamp((this.age + partialTicks) / this.lifetime, 0.0F, 1.0F);
        float eased = 1.0F - (1.0F - progress) * (1.0F - progress);
        float size = Mth.lerp(eased, SIZE * 0.75F, SIZE);
        this.alpha = START_ALPHA * (1.0F - progress);

        renderFace(buffer, camera, partialTicks, this.front, size);
        renderFace(buffer, camera, partialTicks, this.back, size);
    }

    private void renderFace(VertexConsumer buffer, Camera camera, float partialTicks, Quaternionf rotation, float size) {
        Vec3 cameraPos = camera.getPosition();
        float x = (float) (Mth.lerp(partialTicks, this.xo, this.x) - cameraPos.x());
        float y = (float) (Mth.lerp(partialTicks, this.yo, this.y) - cameraPos.y());
        float z = (float) (Mth.lerp(partialTicks, this.zo, this.z) - cameraPos.z());

        Vector3f[] corners = new Vector3f[]{
                new Vector3f(-1.0F, -1.0F, 0.0F), new Vector3f(-1.0F, 1.0F, 0.0F), new Vector3f(1.0F, 1.0F, 0.0F), new Vector3f(1.0F, -1.0F, 0.0F)
        };

        for (Vector3f corner : corners) {
            corner.rotate(rotation);
            corner.mul(size);
            corner.add(x, y, z);
        }

        int light = 15728880;
        vertex(buffer, corners[0], this.getU1(), this.getV1(), light);
        vertex(buffer, corners[1], this.getU1(), this.getV0(), light);
        vertex(buffer, corners[2], this.getU0(), this.getV0(), light);
        vertex(buffer, corners[3], this.getU0(), this.getV1(), light);
    }

    private void vertex(VertexConsumer buffer, Vector3f pos, float u, float v, int light) {
        buffer.addVertex(pos.x(), pos.y(), pos.z()).setUv(u, v).setColor(this.rCol, this.gCol, this.bCol, this.alpha).setLight(light);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double normalX, double normalY, double normalZ) {
            return new ImpactShockwaveParticle(level, x, y, z, normalX, normalY, normalZ, this.sprites);
        }
    }

    @SubscribeEvent
    public static void registerProvider(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ImpactRecoilQuirk.IMPACT_SHOCKWAVE, Provider::new);
    }

}
