package com.dolfin.space.client.mobs;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Custom mob model, identical to the design from the skins-mobs skill (the 3D viewer). It is built
 * from {@code assets/<ns>/dolfin/modelos/<mob>.json}, written by {@code
 * tools/skins/integrate_existing.py}: the pieces in Minecraft coordinates grouped into bones (head,
 * legs, arms, hanging parts). Each bone is an empty part at its pivot; rotating it moves everything
 * hanging from it (a helmet with the head, a shin with its thigh).
 */
public final class DesignModel<S extends LivingEntityRenderState> extends EntityModel<S>
        implements ArmedModel<S> {
    private static final Gson GSON = new Gson();

    private static final String KIND_HEAD = "cabeza";
    private static final String KIND_LEG = "pierna";
    private static final String KIND_ARM = "brazo";
    private static final String KIND_HANGING = "cuelga";

    /** The JSON contents. {@code height} and {@code width} are the design's box in blocks. */
    public record Data(
            @SerializedName("clave") String key,
            @SerializedName("textura") int[] texture,
            @SerializedName("escala") float scale,
            @SerializedName("alto") float height,
            @SerializedName("ancho") float width,
            @SerializedName("huesos") List<Bone> bones,
            @SerializedName("piezas") List<Piece> pieces) {}

    record Bone(
            @SerializedName("nombre") String name,
            @SerializedName("tipo") String kind,
            @SerializedName("piv") float[] pivot,
            @SerializedName("fase") float phase,
            @SerializedName("lado") int side,
            @SerializedName("pieza") int piece) {}

    record Piece(
            @SerializedName("nombre") String name,
            @SerializedName("hueso") String bone,
            @SerializedName("uv") int[] uv,
            @SerializedName("origen") float[] origin,
            @SerializedName("tam") float[] size,
            @SerializedName("piv") float[] pivot,
            @SerializedName("rot") float[] rot,
            @SerializedName("inflado") float inflation) {}

    /** Which boxes the mesh carries: the body, the inflated outer layer or the charge aura. */
    public enum Layer {
        BASE,
        EXTERIOR,
        AURA
    }

    private final Data data;
    private final List<ModelPart> bones = new ArrayList<>();
    // the piece each bone starts with (on an arm, the arm that holds the item)
    private final List<ModelPart> arms = new ArrayList<>();

    public DesignModel(Data data, Layer layer) {
        super(mesh(data, layer).bakeRoot());
        this.data = data;
        for (Bone bone : data.bones()) {
            ModelPart part = root.getChild(bone.name());
            bones.add(part);
            int rootPiece = bone.piece();
            arms.add(part.getChild(data.pieces().get(rootPiece).name() + "_" + rootPiece));
        }
    }

    public Data data() {
        return data;
    }

    /** Reads a mob's model from the mod resources. */
    public static Data load(String namespace, String mob) {
        String path = "/assets/" + namespace + "/dolfin/modelos/" + mob + ".json";
        var input = DesignModel.class.getResourceAsStream(path);
        if (input == null) throw new IllegalStateException("Missing model " + path);
        try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, Data.class);
        } catch (java.io.IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static LayerDefinition mesh(Data data, Layer layer) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        for (Bone bone : data.bones()) {
            float[] p = bone.pivot();
            root.addOrReplaceChild(
                    bone.name(), CubeListBuilder.create(), PartPose.offset(p[0], p[1], p[2]));
        }
        List<Piece> pieces = data.pieces();
        for (int i = 0; i < pieces.size(); i++) {
            Piece piece = pieces.get(i);
            PartDefinition parent = piece.bone() == null ? root : root.getChild(piece.bone());
            float[] o = piece.origin(), t = piece.size(), p = piece.pivot(), r = piece.rot();
            CubeListBuilder cubes = CubeListBuilder.create().texOffs(piece.uv()[0], piece.uv()[1]);
            CubeDeformation inflation =
                    switch (layer) {
                        case BASE -> CubeDeformation.NONE;
                        case EXTERIOR -> new CubeDeformation(piece.inflation());
                        case AURA -> new CubeDeformation(2);
                    };
            // 1 px pieces carry no outer layer, but the bone stays for the animation
            if (layer != Layer.EXTERIOR || piece.inflation() > 0)
                cubes.addBox(o[0], o[1], o[2], t[0], t[1], t[2], inflation);
            parent.addOrReplaceChild(
                    piece.name() + "_" + i,
                    cubes,
                    PartPose.offsetAndRotation(p[0], p[1], p[2], r[0], r[1], r[2]));
        }
        return LayerDefinition.create(mesh, data.texture()[0], data.texture()[1]);
    }

    /**
     * Minimal animation over the design's pose: the head looks around, the legs swing alternately
     * while walking, the arms sway a little and hanging parts (tails, tentacles, capes) sway on
     * their own.
     */
    @Override
    public void setupAnim(S state) {
        super.setupAnim(state);
        float step = state.walkAnimationPos, speed = Math.min(1, state.walkAnimationSpeed);
        float t = state.ageInTicks;
        for (int i = 0; i < bones.size(); i++) {
            Bone bone = data.bones().get(i);
            ModelPart part = bones.get(i);
            switch (bone.kind()) {
                case KIND_HEAD -> {
                    part.yRot += state.yRot * Mth.DEG_TO_RAD;
                    part.xRot += state.xRot * Mth.DEG_TO_RAD;
                }
                case KIND_LEG -> part.xRot += Mth.cos(step * .6662f + bone.phase()) * 1.2f * speed;
                case KIND_ARM -> {
                    part.xRot +=
                            Mth.cos(step * .6662f + bone.phase() + Mth.PI) * .4f * speed
                                    + Mth.sin(t * .067f) * .05f;
                    part.zRot += bone.side() * (Mth.cos(t * .09f) * .05f + .05f);
                }
                case KIND_HANGING -> {
                    part.xRot += Mth.sin(t * .08f + i) * .12f + speed * .3f;
                    part.zRot += Mth.cos(t * .06f + i) * .08f;
                }
                default -> {}
            }
        }
    }

    /** Moves the pose to the end of the arm on that side, like a humanoid's arm. */
    @Override
    public void translateToHand(S state, HumanoidArm arm, PoseStack pose) {
        int side = arm == HumanoidArm.RIGHT ? -1 : 1;
        for (int i = 0; i < bones.size(); i++) {
            Bone bone = data.bones().get(i);
            if (!bone.kind().equals(KIND_ARM) || bone.side() != side) continue;
            root.translateAndRotate(pose);
            bones.get(i).translateAndRotate(pose);
            // the bone's first piece is the arm itself: its rest rotation points the hand
            arms.get(i).translateAndRotate(pose);
            return;
        }
    }
}
