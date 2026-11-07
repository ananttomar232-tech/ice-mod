package com.iceage.yantra.client.obj;

import com.iceage.yantra.IceAgeYantra;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** The baked rig parts of the Forge version (gola, fan, fridge, AC, van, portal, pedestal, hairdryer). Lazily parsed. */
public final class ObjParts {
    private static final List<Part> ALL = new ArrayList<>();

    public static final class Part {
        private final Identifier path;
        private ObjModel model;

        private Part(String name) {
            this.path = new Identifier(IceAgeYantra.MODID, "models/obj/" + name + ".obj");
        }

        public void render(MatrixStack ms, VertexConsumerProvider vcp, int light, int overlay) {
            render(ms, vcp, light, overlay, 1f, 1f, 1f, 1f);
        }

        public void render(MatrixStack ms, VertexConsumerProvider vcp, int light, int overlay, float r, float g, float b, float a) {
            if (model == null) model = ObjModel.load(path);
            model.render(ms, vcp, light, overlay, r, g, b, a);
        }

        void clear() {
            model = null;
        }
    }

    private static Part part(String name) {
        Part p = new Part(name);
        ALL.add(p);
        return p;
    }

    public static final Part GOLA_BODY = part("gola/body"), GOLA_WHEEL = part("gola/wheel"), GOLA_HANDLE = part("gola/handle");
    public static final Part FAN_BODY = part("fan/body"), FAN_HEAD = part("fan/head"), FAN_ROTOR = part("fan/rotor");
    public static final Part FRIDGE_BODY = part("fridge/body"), FRIDGE_DOOR_TOP = part("fridge/door_top"), FRIDGE_DOOR_BOTTOM = part("fridge/door_bottom");
    public static final Part AC_BODY = part("ac/body"), AC_ROTOR = part("ac/rotor");
    public static final Part TRUCK_BODY = part("truck/body"), TRUCK_CONE = part("truck/cone"), TRUCK_DECALS = part("truck/decals"),
            TRUCK_GLASS = part("truck/glass"), TRUCK_WHEEL_FL = part("truck/wheel_fl"), TRUCK_WHEEL_FR = part("truck/wheel_fr"),
            TRUCK_WHEEL_RL = part("truck/wheel_rl"), TRUCK_WHEEL_RR = part("truck/wheel_rr");
    public static final Part PORTAL_FRAME = part("portal/frame"), PEDESTAL = part("pedestal/base"), HAIRDRYER = part("hairdryer/body");

    private ObjParts() {
    }

    /** Called on every resource reload so edited/new models and re-stitched atlases are picked up. */
    public static void clearAll() {
        for (Part p : ALL) p.clear();
    }
}
