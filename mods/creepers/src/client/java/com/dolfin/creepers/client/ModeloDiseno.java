package com.dolfin.creepers.client;

import com.google.gson.Gson;
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
 * Modelo propio de un mob, igual al diseño del skill skins-mobs (el visor 3D). Se arma con {@code
 * assets/<ns>/dolfin/modelos/<mob>.json}, que escribe {@code tools/skins/integrate_existing.py}:
 * las piezas en coordenadas de Minecraft agrupadas en huesos (cabeza, patas, brazos, partes que
 * cuelgan). Cada hueso es una pieza vacía en su pivote; al girarlo se mueve todo lo que cuelga de
 * él (un casco con la cabeza, una canilla con su muslo).
 */
public final class ModeloDiseno<S extends LivingEntityRenderState> extends EntityModel<S>
        implements ArmedModel<S> {
    private static final Gson GSON = new Gson();

    /** El contenido del JSON. {@code alto} y {@code ancho} son la caja del diseño en bloques. */
    public record Datos(
            String clave,
            int[] textura,
            float escala,
            float alto,
            float ancho,
            List<Hueso> huesos,
            List<Pieza> piezas) {}

    record Hueso(String nombre, String tipo, float[] piv, float fase, int lado, int pieza) {}

    record Pieza(
            String nombre,
            String hueso,
            int[] uv,
            float[] origen,
            float[] tam,
            float[] piv,
            float[] rot,
            float inflado) {}

    /** Qué cajas lleva la malla: el cuerpo, la capa exterior inflada o el aura de carga. */
    public enum Capa {
        BASE,
        EXTERIOR,
        AURA
    }

    private final Datos datos;
    private final List<ModelPart> huesos = new ArrayList<>();
    // la pieza con que empieza cada hueso (en un brazo, el brazo que sostiene el objeto)
    private final List<ModelPart> brazos = new ArrayList<>();

    public ModeloDiseno(Datos datos, Capa capa) {
        super(malla(datos, capa).bakeRoot());
        this.datos = datos;
        for (Hueso hueso : datos.huesos()) {
            ModelPart parte = root.getChild(hueso.nombre());
            huesos.add(parte);
            int raiz = hueso.pieza();
            brazos.add(parte.getChild(datos.piezas().get(raiz).nombre() + "_" + raiz));
        }
    }

    public Datos datos() {
        return datos;
    }

    /** Lee el modelo de un mob de los recursos del mod. */
    public static Datos cargar(String namespace, String mob) {
        String ruta = "/assets/" + namespace + "/dolfin/modelos/" + mob + ".json";
        var entrada = ModeloDiseno.class.getResourceAsStream(ruta);
        if (entrada == null) throw new IllegalStateException("Falta el modelo " + ruta);
        try (Reader lector = new InputStreamReader(entrada, StandardCharsets.UTF_8)) {
            return GSON.fromJson(lector, Datos.class);
        } catch (java.io.IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static LayerDefinition malla(Datos datos, Capa capa) {
        MeshDefinition malla = new MeshDefinition();
        PartDefinition raiz = malla.getRoot();
        for (Hueso hueso : datos.huesos()) {
            float[] p = hueso.piv();
            raiz.addOrReplaceChild(
                    hueso.nombre(), CubeListBuilder.create(), PartPose.offset(p[0], p[1], p[2]));
        }
        List<Pieza> piezas = datos.piezas();
        for (int i = 0; i < piezas.size(); i++) {
            Pieza pieza = piezas.get(i);
            PartDefinition padre = pieza.hueso() == null ? raiz : raiz.getChild(pieza.hueso());
            float[] o = pieza.origen(), t = pieza.tam(), p = pieza.piv(), r = pieza.rot();
            CubeListBuilder cubos = CubeListBuilder.create().texOffs(pieza.uv()[0], pieza.uv()[1]);
            CubeDeformation inflado =
                    switch (capa) {
                        case BASE -> CubeDeformation.NONE;
                        case EXTERIOR -> new CubeDeformation(pieza.inflado());
                        case AURA -> new CubeDeformation(2);
                    };
            // las piezas de 1 px no llevan capa exterior, pero el hueso queda para la animación
            if (capa != Capa.EXTERIOR || pieza.inflado() > 0)
                cubos.addBox(o[0], o[1], o[2], t[0], t[1], t[2], inflado);
            padre.addOrReplaceChild(
                    pieza.nombre() + "_" + i,
                    cubos,
                    PartPose.offsetAndRotation(p[0], p[1], p[2], r[0], r[1], r[2]));
        }
        return LayerDefinition.create(malla, datos.textura()[0], datos.textura()[1]);
    }

    /**
     * Animación mínima sobre la pose del diseño: la cabeza mira, las patas se balancean al caminar
     * alternándose, los brazos se mecen un poco y lo que cuelga (colas, tentáculos, capas) se mece
     * solo.
     */
    @Override
    public void setupAnim(S estado) {
        super.setupAnim(estado);
        float paso = estado.walkAnimationPos, velocidad = Math.min(1, estado.walkAnimationSpeed);
        float t = estado.ageInTicks;
        for (int i = 0; i < huesos.size(); i++) {
            Hueso hueso = datos.huesos().get(i);
            ModelPart parte = huesos.get(i);
            switch (hueso.tipo()) {
                case "cabeza" -> {
                    parte.yRot += estado.yRot * Mth.DEG_TO_RAD;
                    parte.xRot += estado.xRot * Mth.DEG_TO_RAD;
                }
                case "pierna" ->
                        parte.xRot += Mth.cos(paso * .6662f + hueso.fase()) * 1.2f * velocidad;
                case "brazo" -> {
                    parte.xRot +=
                            Mth.cos(paso * .6662f + hueso.fase() + Mth.PI) * .4f * velocidad
                                    + Mth.sin(t * .067f) * .05f;
                    parte.zRot += hueso.lado() * (Mth.cos(t * .09f) * .05f + .05f);
                }
                case "cuelga" -> {
                    parte.xRot += Mth.sin(t * .08f + i) * .12f + velocidad * .3f;
                    parte.zRot += Mth.cos(t * .06f + i) * .08f;
                }
                default -> {}
            }
        }
    }

    /** Lleva la pose al extremo del brazo de ese lado, como el brazo de un humanoide. */
    @Override
    public void translateToHand(S estado, HumanoidArm brazo, PoseStack pose) {
        int lado = brazo == HumanoidArm.RIGHT ? -1 : 1;
        for (int i = 0; i < huesos.size(); i++) {
            Hueso hueso = datos.huesos().get(i);
            if (!hueso.tipo().equals("brazo") || hueso.lado() != lado) continue;
            root.translateAndRotate(pose);
            huesos.get(i).translateAndRotate(pose);
            // la primera pieza del hueso es el brazo mismo: su giro de reposo apunta la mano
            brazos.get(i).translateAndRotate(pose);
            return;
        }
    }
}
