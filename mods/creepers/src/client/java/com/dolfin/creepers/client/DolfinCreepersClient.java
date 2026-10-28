package com.dolfin.creepers.client;

import com.dolfin.creepers.DolfinCreepers;
import com.dolfin.creepers.Variante;
import com.dolfin.creepers.client.ModeloDiseno.Capa;
import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EnergySwirlLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.CreeperRenderState;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * Parte del mod que solo corre en el juego del jugador: como se dibuja cada variante. Cada mob usa
 * su modelo propio ({@link ModeloDiseno}), su capa exterior con relieve y su textura.
 */
public class DolfinCreepersClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        for (Variante variante : Variante.values()) {
            String id = variante.id();
            // El usuario pidió los creepers gigantes sin aura de carga.
            boolean aura = variante != Variante.GIGANTE && variante != Variante.REY;
            EntityRendererRegistry.register(
                    DolfinCreepers.tipo(variante),
                    contexto -> new Renderizador(contexto, id, aura, (float) variante.escala));
        }
        EntityRendererRegistry.register(DolfinCreepers.REY_ZOMBIE, RenderizadorReyZombie::new);
    }

    static Identifier textura(String id) {
        return DolfinCreepers.id("textures/entity/" + id + ".png");
    }

    static ModeloDiseno.Datos datos(String id) {
        return ModeloDiseno.cargar(DolfinCreepers.MOD_ID, id);
    }

    /**
     * Un creeper con su modelo propio. Conserva lo del creeper vanilla: se infla y parpadea en
     * blanco antes de explotar y, cargado por un rayo, lleva el aura.
     */
    private static class Renderizador
            extends MobRenderer<Creeper, CreeperRenderState, ModeloDiseno<CreeperRenderState>> {
        private static final Identifier AURA =
                Identifier.withDefaultNamespace("textures/entity/creeper/creeper_armor.png");
        private final Identifier textura;
        // El tamaño ya lo da el atributo SCALE de la variante; solo se corrige si el diseño
        // difiere.
        private final float escala;

        Renderizador(EntityRendererProvider.Context contexto, String id, boolean aura, float base) {
            super(contexto, new ModeloDiseno<>(datos(id), Capa.BASE), .5f);
            this.textura = textura(id);
            this.escala = model.datos().escala() / base;
            addLayer(
                    new CapaExterior<>(
                            this,
                            new ModeloDiseno<>(model.datos(), Capa.EXTERIOR),
                            textura(id + "_exterior")));
            if (aura) {
                var modeloAura = new ModeloDiseno<CreeperRenderState>(model.datos(), Capa.AURA);
                addLayer(
                        new EnergySwirlLayer<>(this) {
                            @Override
                            protected boolean isPowered(CreeperRenderState estado) {
                                return estado.isPowered;
                            }

                            @Override
                            protected float xOffset(float t) {
                                return t * .01f;
                            }

                            @Override
                            protected Identifier getTextureLocation() {
                                return AURA;
                            }

                            @Override
                            protected ModeloDiseno<CreeperRenderState> model() {
                                return modeloAura;
                            }
                        });
            }
        }

        @Override
        public Identifier getTextureLocation(CreeperRenderState estado) {
            return textura;
        }

        @Override
        public CreeperRenderState createRenderState() {
            return new CreeperRenderState();
        }

        @Override
        public void extractRenderState(Creeper creeper, CreeperRenderState estado, float parcial) {
            super.extractRenderState(creeper, estado, parcial);
            estado.swelling = creeper.getSwelling(parcial);
            estado.isPowered = creeper.isPowered();
        }

        /** Igual que el creeper vanilla: crece y tiembla a punto de explotar. */
        @Override
        protected void scale(CreeperRenderState estado, PoseStack pose) {
            pose.scale(escala, escala, escala);
            float mecha = estado.swelling;
            float temblor = 1 + Mth.sin(mecha * 100) * mecha * .01f;
            mecha = Mth.clamp(mecha, 0, 1);
            mecha *= mecha * mecha;
            float ancho = (1 + mecha * .4f) * temblor, alto = (1 + mecha * .1f) / temblor;
            pose.scale(ancho, alto, ancho);
        }

        @Override
        protected float getWhiteOverlayProgress(CreeperRenderState estado) {
            float mecha = estado.swelling;
            return (int) (mecha * 10) % 2 == 0 ? 0 : Mth.clamp(mecha, .5f, 1);
        }
    }

    /** El jefe Rey Zombie con su modelo propio, el doble de alto que un zombie, y su arma. */
    private static class RenderizadorReyZombie
            extends MobRenderer<Zombie, ZombieRenderState, ModeloDiseno<ZombieRenderState>> {
        private final Identifier textura = textura("rey_zombie");
        private final ItemModelResolver objetos;

        RenderizadorReyZombie(EntityRendererProvider.Context contexto) {
            super(contexto, new ModeloDiseno<>(datos("rey_zombie"), Capa.BASE), .9f);
            objetos = contexto.getItemModelResolver();
            addLayer(
                    new CapaExterior<>(
                            this,
                            new ModeloDiseno<>(model.datos(), Capa.EXTERIOR),
                            textura("rey_zombie_exterior")));
            addLayer(new ItemInHandLayer<>(this));
        }

        @Override
        public Identifier getTextureLocation(ZombieRenderState estado) {
            return textura;
        }

        @Override
        public ZombieRenderState createRenderState() {
            return new ZombieRenderState();
        }

        @Override
        public void extractRenderState(Zombie zombie, ZombieRenderState estado, float parcial) {
            super.extractRenderState(zombie, estado, parcial);
            ArmedEntityRenderState.extractArmedEntityRenderState(zombie, estado, objetos, parcial);
        }

        @Override
        protected void scale(ZombieRenderState estado, PoseStack pose) {
            float escala = model.datos().escala();
            pose.scale(escala, escala, escala);
        }
    }
}
