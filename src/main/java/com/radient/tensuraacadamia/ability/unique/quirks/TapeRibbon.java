package com.radient.tensuraacadamia.ability.unique.quirks;

import com.mojang.math.Transformation;
import com.radient.tensuraacadamia.mixin.TapeBlockDisplayAccessor;
import com.radient.tensuraacadamia.mixin.TapeDisplayAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** A thin, textured vanilla block display with its long axis between two points. */
final class TapeRibbon {
    static final String TAG = "TracadamiaTapeRibbon";
    private static final List<Display.BlockDisplay> ACTIVE = new ArrayList<>();
    private final Display.BlockDisplay display;

    private TapeRibbon(Display.BlockDisplay display) { this.display = display; }

    static TapeRibbon create(ServerLevel level, Vec3 from) {
        Display.BlockDisplay display = EntityType.BLOCK_DISPLAY.create(level);
        if (display == null) return null;
        ((TapeBlockDisplayAccessor) display).tracadamia$setBlockState(Blocks.WHITE_CARPET.defaultBlockState());
        ((TapeDisplayAccessor) display).tracadamia$setTransformationInterpolationDuration(2);
        ((TapeDisplayAccessor) display).tracadamia$setPosRotInterpolationDuration(2);
        display.getPersistentData().putBoolean(TAG, true);
        display.setPos(from.x, from.y, from.z);
        ACTIVE.add(display);
        if (!level.addFreshEntity(display)) { ACTIVE.remove(display); return null; }
        TapeRibbon ribbon = new TapeRibbon(display);
        ribbon.set(from, from.add(0, 0, 0.01), 0.16F);
        return ribbon;
    }

    void set(Vec3 from, Vec3 to, float width) {
        if (display.isRemoved()) return;
        Vec3 delta = to.subtract(from);
        float length = (float) Math.max(0.01, delta.length());
        Vector3f direction = new Vector3f((float) (delta.x / length), (float) (delta.y / length),
                (float) (delta.z / length));
        Quaternionf rotation = new Quaternionf().rotationTo(new Vector3f(0, 0, 1), direction);
        display.setPos(from.x, from.y, from.z);
        ((TapeDisplayAccessor) display).tracadamia$setTransformation(new Transformation(
                new Vector3f(-width / 2, -0.01F, 0), rotation,
                new Vector3f(width, 0.35F, length), new Quaternionf()));
    }

    void discard() { ACTIVE.remove(display); display.discard(); }
    static boolean active(Display.BlockDisplay display) { return ACTIVE.contains(display); }
    static void clear() { ACTIVE.clear(); }
}
