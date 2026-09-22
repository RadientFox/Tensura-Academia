package com.radient.tensuraacadamia.mixin.client;

import com.radient.tensuraacadamia.network.ElasticityBouncePayload;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class ElasticityJumpKeyMixin {
    @Inject(method = "keyPress", at = @At("TAIL"))
    private void tracadamia$sendElasticityBounce(long window, int key, int scanCode, int action, int modifiers,
                                                 CallbackInfo callback) {
        if (key == GLFW.GLFW_KEY_SPACE && action == GLFW.GLFW_PRESS && Minecraft.getInstance().screen == null) {
            PacketDistributor.sendToServer(new ElasticityBouncePayload());
        }
    }
}
