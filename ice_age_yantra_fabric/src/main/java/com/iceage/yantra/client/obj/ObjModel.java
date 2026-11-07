package com.iceage.yantra.client.obj;

import com.iceage.yantra.IceAgeYantra;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * A baked OBJ rig part (the very same .obj/.mtl files the Forge version of the mod uses): triangles with per-vertex
 * normals, UVs and baked ambient-occlusion vertex colours, grouped by material. Materials with Ka 1 are emissive.
 * Rendered straight into the vertex consumer, so there are no mixins and no model-loader hooks.
 */
public final class ObjModel {
    /** Material names whose texture has a different file name. */
    private static final Map<String, String> ALIAS = Map.of(
            "chandelier_ice", "portal_icicle", "cone_stand", "gola_chrome", "fan_badge_ring", "gola_chrome", "fan_chrome", "gola_chrome");

    private static final class Group {
        Identifier texture;
        boolean emissive;
        float[] pos, uv, nrm, col;
        int tris;
        Sprite sprite;
    }

    private final List<Group> groups = new ArrayList<>();

    private ObjModel() {
    }

    // ------------------------------------------------------------------------------------------------ loading
    public static ObjModel load(Identifier objPath) {
        ObjModel m = new ObjModel();
        try {
            ResourceManagerHolder.parse(m, objPath);
        } catch (Exception e) {
            IceAgeYantra.LOGGER.error("Could not load OBJ model {}", objPath, e);
        }
        return m;
    }

    private static final class ResourceManagerHolder {
        static void parse(ObjModel m, Identifier objPath) throws IOException {
            var rm = MinecraftClient.getInstance().getResourceManager();
            Optional<Resource> res = rm.getResource(objPath);
            if (res.isEmpty()) throw new IOException("missing " + objPath);
            // materials: emissive flag from the .mtl next to the .obj
            Map<String, Boolean> emissive = new HashMap<>();
            Identifier mtlPath = new Identifier(objPath.getNamespace(), objPath.getPath().replaceAll("\\.obj$", ".mtl"));
            Optional<Resource> mtl = rm.getResource(mtlPath);
            if (mtl.isPresent()) {
                try (BufferedReader r = mtl.get().getReader()) {
                    String cur = null, line;
                    while ((line = r.readLine()) != null) {
                        line = line.trim();
                        if (line.startsWith("newmtl ")) cur = line.substring(7).trim();
                        else if (line.startsWith("Ka ") && cur != null) {
                            String[] p = line.split("\\s+");
                            emissive.put(cur, Float.parseFloat(p[1]) > 0.5f);
                        }
                    }
                }
            }
            List<float[]> v = new ArrayList<>(), vt = new ArrayList<>(), vn = new ArrayList<>(), vc = new ArrayList<>();
            Map<String, List<float[]>> perMat = new HashMap<>();   // 3 corners * (x y z u v nx ny nz r g b a)
            List<String> order = new ArrayList<>();
            String mat = "default";
            try (BufferedReader r = res.get().getReader()) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.isEmpty() || line.charAt(0) == '#') continue;
                    String[] t = line.split("\\s+");
                    switch (t[0]) {
                        case "v" -> v.add(new float[]{f(t[1]), f(t[2]), f(t[3])});
                        case "vt" -> vt.add(new float[]{f(t[1]), f(t[2])});
                        case "vn" -> vn.add(new float[]{f(t[1]), f(t[2]), f(t[3])});
                        case "vc" -> vc.add(new float[]{f(t[1]), f(t[2]), f(t[3]), t.length > 4 ? f(t[4]) : 1f});
                        case "usemtl" -> mat = t[1];
                        case "o" -> {
                            if (!perMat.containsKey(t[1]) && !emissive.containsKey(t[1])) {
                                // object names equal the material names in these files; usemtl follows anyway
                            }
                        }
                        case "f" -> {
                            List<float[]> list = perMat.computeIfAbsent(mat, k -> {
                                order.add(k);
                                return new ArrayList<>();
                            });
                            int n = t.length - 1;
                            float[][] corner = new float[n][];
                            for (int i = 0; i < n; i++) {
                                String[] ix = t[i + 1].split("/");
                                float[] p = v.get(Integer.parseInt(ix[0]) - 1);
                                float[] uv = ix.length > 1 && !ix[1].isEmpty() ? vt.get(Integer.parseInt(ix[1]) - 1) : new float[]{0, 0};
                                float[] nn = ix.length > 2 && !ix[2].isEmpty() ? vn.get(Integer.parseInt(ix[2]) - 1) : new float[]{0, 1, 0};
                                float[] cc = ix.length > 3 && !ix[3].isEmpty() ? vc.get(Integer.parseInt(ix[3]) - 1) : new float[]{1, 1, 1, 1};
                                corner[i] = new float[]{p[0], p[1], p[2], uv[0], uv[1], nn[0], nn[1], nn[2], cc[0], cc[1], cc[2], cc[3]};
                            }
                            for (int i = 1; i < n - 1; i++) {   // triangle fan
                                list.add(corner[0]);
                                list.add(corner[i]);
                                list.add(corner[i + 1]);
                            }
                        }
                        default -> {
                        }
                    }
                }
            }
            for (String name : order) {
                List<float[]> c = perMat.get(name);
                Group g = new Group();
                String tex = ALIAS.getOrDefault(name, name);
                g.texture = new Identifier(IceAgeYantra.MODID, "block/" + tex);
                g.emissive = emissive.getOrDefault(name, false);
                g.tris = c.size() / 3;
                g.pos = new float[c.size() * 3];
                g.uv = new float[c.size() * 2];
                g.nrm = new float[c.size() * 3];
                g.col = new float[c.size() * 4];
                for (int i = 0; i < c.size(); i++) {
                    float[] k = c.get(i);
                    g.pos[i * 3] = k[0];
                    g.pos[i * 3 + 1] = k[1];
                    g.pos[i * 3 + 2] = k[2];
                    g.uv[i * 2] = k[3];
                    g.uv[i * 2 + 1] = k[4];
                    g.nrm[i * 3] = k[5];
                    g.nrm[i * 3 + 1] = k[6];
                    g.nrm[i * 3 + 2] = k[7];
                    g.col[i * 4] = k[8];
                    g.col[i * 4 + 1] = k[9];
                    g.col[i * 4 + 2] = k[10];
                    g.col[i * 4 + 3] = k[11];
                }
                m.groups.add(g);
            }
        }

        private static float f(String s) {
            return Float.parseFloat(s);
        }
    }

    // ------------------------------------------------------------------------------------------------ drawing
    public void render(MatrixStack ms, VertexConsumerProvider vcp, int light, int overlay) {
        render(ms, vcp, light, overlay, 1f, 1f, 1f, 1f);
    }

    public void render(MatrixStack ms, VertexConsumerProvider vcp, int light, int overlay, float r, float g, float b, float a) {
        if (groups.isEmpty()) return;
        Function<Identifier, Sprite> atlas = MinecraftClient.getInstance().getSpriteAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
        RenderLayer layer = a < 0.999f ? RenderLayer.getEntityTranslucent(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)
                : RenderLayer.getEntityCutoutNoCull(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
        VertexConsumer vc = vcp.getBuffer(layer);
        MatrixStack.Entry e = ms.peek();
        Matrix4f pm = e.getPositionMatrix();
        Matrix3f nm = e.getNormalMatrix();
        for (Group gr : groups) {
            Sprite s = atlas.apply(gr.texture);
            float u0 = s.getMinU(), du = s.getMaxU() - u0, v0 = s.getMinV(), dv = s.getMaxV() - v0;
            int lt = gr.emissive ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light;
            for (int t = 0; t < gr.tris; t++) {
                for (int k = 0; k < 4; k++) {          // triangles are drawn as degenerate quads
                    int i = t * 3 + Math.min(k, 2);
                    vc.vertex(pm, gr.pos[i * 3], gr.pos[i * 3 + 1], gr.pos[i * 3 + 2])
                            .color(gr.col[i * 4] * r, gr.col[i * 4 + 1] * g, gr.col[i * 4 + 2] * b, gr.col[i * 4 + 3] * a)
                            .texture(u0 + du * gr.uv[i * 2], v0 + dv * gr.uv[i * 2 + 1])
                            .overlay(overlay)
                            .light(lt)
                            .normal(nm, gr.nrm[i * 3], gr.nrm[i * 3 + 1], gr.nrm[i * 3 + 2])
                            .next();
                }
            }
        }
    }
}
