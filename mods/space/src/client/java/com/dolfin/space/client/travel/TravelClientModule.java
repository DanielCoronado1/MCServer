package com.dolfin.space.client.travel;

import com.dolfin.space.*;
import com.dolfin.space.ships.*;
import com.dolfin.space.travel.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.sounds.*;
import net.minecraft.util.RandomSource;

public final class TravelClientModule {
  public static final KeyMapping COCKPIT =
      new KeyMapping("key.dolfinspace.cabina", InputConstants.KEY_R, KeyMapping.Category.INVENTORY);
  public static volatile float intensity;
  public static volatile long shakeStart;
  public static volatile long lastPacketNanos;
  private static CockpitHum hum;

  public static void init() {
    MenuScreens.register(TravelModule.MENU, CockpitScreen::new);
    KeyMappingHelper.registerKeyMapping(COCKPIT);
    ClientPlayNetworking.registerGlobalReceiver(
        CameraShakePayload.TYPE,
        (p, c) ->
            c.client()
                .execute(
                    () -> {
                      intensity = p.intensidad();
                      shakeStart = p.inicio();
                      lastPacketNanos = System.nanoTime();
                    }));
    ClientPlayNetworking.registerGlobalReceiver(
        FlightPhasePayload.TYPE,
        (p, c) ->
            c.client()
                .execute(
                    () -> {
                      if (p.phase() == FlightJournal.Phase.TRANSITION.ordinal())
                        c.client().gui.setScreen(new StarTransitionScreen());
                      if (p.phase() < 0) intensity = 0;
                    }));
    ClientPlayConnectionEvents.DISCONNECT.register(
        (h, c) -> {
          intensity = 0;
          hum = null;
        });
    ClientTickEvents.END_CLIENT_TICK.register(
        c -> {
          boolean riding = c.player != null && c.player.getVehicle() instanceof SpaceshipEntity;
          while (COCKPIT.consumeClick())
            if (riding) {
              if (c.gui.screen() instanceof CockpitScreen) c.player.closeContainer();
              else if (c.gui.screen() == null)
                ClientPlayNetworking.send(new CockpitActionPayload(0));
            }
          if (riding && (hum == null || hum.isStopped())) {
            hum = new CockpitHum();
            c.getSoundManager().play(hum);
          }
          if (!riding) intensity = 0;
        });
  }

  public static double scale() {
    return DolfinSpace.config().current().cliente().intensidadVibracionCamara();
  }

  public static void scale(double value) {
    var cfg = DolfinSpace.config();
    try {
      var json =
          com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(cfg.file()))
              .getAsJsonObject();
      json.getAsJsonObject("cliente")
          .addProperty("intensidadVibracionCamara", Math.clamp(value, 0, 1));
      java.nio.file.Files.writeString(
          cfg.file(), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json));
      cfg.reload();
    } catch (Exception e) {
      DolfinSpace.LOGGER.warn("event=client_shake_config_failed reason={}", e.toString());
    }
  }

  public static void beep(boolean alarm) {
    var c = Minecraft.getInstance();
    if (c.player != null)
      c.getSoundManager()
          .play(
              new SimpleSoundInstance(
                  alarm
                      ? SoundEvents.NOTE_BLOCK_BASS.value()
                      : SoundEvents.NOTE_BLOCK_PLING.value(),
                  SoundSource.NEUTRAL,
                  alarm ? .25f : .15f,
                  alarm ? .6f : 1.6f,
                  RandomSource.create(),
                  c.player.blockPosition()));
  }

  private static final class CockpitHum extends AbstractTickableSoundInstance {
    CockpitHum() {
      super(SoundEvents.BEACON_AMBIENT, SoundSource.NEUTRAL, RandomSource.create());
      looping = true;
      volume = .12f;
      pitch = .7f;
      relative = true;
      attenuation = SoundInstance.Attenuation.NONE;
    }

    public void tick() {
      var c = Minecraft.getInstance();
      if (c.player == null || !(c.player.getVehicle() instanceof SpaceshipEntity)) stop();
    }
  }

  private TravelClientModule() {}
}
