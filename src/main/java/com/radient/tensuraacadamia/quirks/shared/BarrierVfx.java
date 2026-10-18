package com.radient.tensuraacadamia.quirks.shared;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class BarrierVfx {
    public static void render(PoseStack poses, MultiBufferSource buffers, Vec3 cam, float span, float thickness,
                       float health, float age, float hitAge, Vec3 hitPoint, boolean gold, boolean repairing, long seed) {
        int detail = SkillVfx.detail(cam, Vec3.ZERO); if (detail == 0) return;
        double half = span * .5, z = Math.copySign(thickness * .5 + .04, cam.z);
        int color = gold ? 0xffc548 : 0xa3deff;
        float pulse = .18F + Mth.sin(age * .05F) * .035F;
        Vec3[] corners = {new Vec3(-half,-half,z),new Vec3(half,-half,z),new Vec3(half,half,z),new Vec3(-half,half,z)};
        for (int i=0;i<4;i++) SkillVfx.ribbon(poses,buffers,cam,corners[i],corners[(i+1)%4],.065,color,pulse,true);
        double scan = -half + (age * .055 % span);
        SkillVfx.ribbon(poses,buffers,cam,new Vec3(-half,scan,z),new Vec3(half,scan,z),.08,color,.04F,true);
        if (gold && detail > 8) {
            Vec3 center = new Vec3(0,0,z);
            SkillVfx.ring(poses,buffers,cam,center,new Vec3(0,0,1),span*.24,.035,color,.16F,age*.009);
            SkillVfx.ring(poses,buffers,cam,center,new Vec3(0,0,1),span*.18,.025,0xffe6a4,.12F,-age*.015);
            for (int i=0;i<6;i++) {
                double a=age*.008+i*Math.PI/3,b=a+Math.PI*2/3;
                SkillVfx.ribbon(poses,buffers,cam,new Vec3(Math.cos(a)*span*.21,Math.sin(a)*span*.21,z),
                        new Vec3(Math.cos(b)*span*.21,Math.sin(b)*span*.21,z),.025,color,.12F,true);
            }
        }
        if (hitAge>=0 && hitAge<10) {
            float fade=1-hitAge/10;
            Vec3 impact = new Vec3(Math.clamp(hitPoint.x, -half, half), Math.clamp(hitPoint.y, -half, half), z);
            SkillVfx.ring(poses, buffers, cam, impact, new Vec3(0,0,1), .06 + hitAge * .025, .035,
                    repairing ? 0xFFECAA : 0xF5FCFF, fade * .8F, 0);
            for (int wave = 0; wave < 2; wave++) {
                double radius = Math.min(span * .45, .08 + hitAge * .12 + wave * .12);
                for (int segment = 0; segment < 24; segment++) {
                    double a = segment * Math.PI / 12, b = (segment + 1) * Math.PI / 12;
                    Vec3 start = impact.add(Math.cos(a) * radius, Math.sin(a) * radius, 0);
                    Vec3 end = impact.add(Math.cos(b) * radius, Math.sin(b) * radius, 0);
                    if (Math.abs(start.x) <= half && Math.abs(start.y) <= half && Math.abs(end.x) <= half && Math.abs(end.y) <= half)
                        SkillVfx.ribbon(poses, buffers, cam, start, end, wave == 0 ? .05 : .02,
                                wave == 0 ? (repairing ? 0xFFECAA : 0xF5FCFF) : color, fade * (wave == 0 ? .85F : .35F), true);
                }
            }
            if (repairing) {
                double restore = -half + span * (1 - fade);
                SkillVfx.ribbon(poses,buffers,cam,new Vec3(-half,restore,z),new Vec3(half,restore,z),.06,0xFFECAA,fade*.65F,true);
            }
        }
        float damage=Mth.clamp((.7F-health)/.7F,0,1);
        if (damage<=0) return;
        int cracks = Math.min(detail, 3 + (int)(damage*7));
        for (int i=0;i<cracks;i++) {
            double a=i*2.399963+seed%101*.17;
            Vec3 from=new Vec3(Math.sin(a*2)*half*.35,Math.cos(a*3)*half*.35,z);
            for (int segment=0;segment<3;segment++) {
                Vec3 to=from.add(Math.cos(a+(segment%2==0?.4:-.4))*span*.1*damage,
                        Math.sin(a+(segment%2==0?.4:-.4))*span*.1*damage,0);
                SkillVfx.ribbon(poses,buffers,cam,from,to,.025,gold?0x5f2710:0xdffaff,damage*.8F,!gold);
                if(segment==1) SkillVfx.ribbon(poses,buffers,cam,from,from.add(Math.sin(a)*span*.065,Math.cos(a)*span*.065,0),
                        .015,gold?0x5f2710:0xdffaff,damage*.6F,!gold);
                from=to;
            }
        }
    }
    private BarrierVfx() { }
}
