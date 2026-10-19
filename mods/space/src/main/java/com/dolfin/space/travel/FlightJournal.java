package com.dolfin.space.travel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

public record FlightJournal(
    Phase phase, int ticks, double startY, long target, String destination, List<String> riders) {
  public enum Phase {
    COUNTDOWN,
    ASCENT,
    TRANSITION,
    DESCENT
  }

  public static final Codec<FlightJournal> CODEC =
      RecordCodecBuilder.create(
          i ->
              i.group(
                      Codec.STRING
                          .xmap(Phase::valueOf, Phase::name)
                          .fieldOf("phase")
                          .forGetter(FlightJournal::phase),
                      Codec.INT.fieldOf("ticks").forGetter(FlightJournal::ticks),
                      Codec.DOUBLE.fieldOf("startY").forGetter(FlightJournal::startY),
                      Codec.LONG.fieldOf("target").forGetter(FlightJournal::target),
                      Codec.STRING.fieldOf("destination").forGetter(FlightJournal::destination),
                      Codec.STRING.listOf().fieldOf("riders").forGetter(FlightJournal::riders))
                  .apply(i, FlightJournal::new));

  public enum Action {
    CANCEL,
    WAIT,
    LIFTOFF,
    ASCEND,
    STARS,
    TRANSFER,
    DESCEND,
    LAND
  }

  public Action action(boolean guardsPass) {
    return switch (phase) {
      case COUNTDOWN -> !guardsPass ? Action.CANCEL : ticks <= 1 ? Action.LIFTOFF : Action.WAIT;
      case ASCENT -> ticks <= 1 ? Action.STARS : Action.ASCEND;
      case TRANSITION -> ticks <= 1 ? Action.TRANSFER : Action.STARS;
      case DESCENT -> ticks <= 1 ? Action.LAND : Action.DESCEND;
    };
  }

  public FlightJournal step() {
    return new FlightJournal(phase, Math.max(0, ticks - 1), startY, target, destination, riders);
  }

  public FlightJournal phase(Phase next, int ticks) {
    return new FlightJournal(next, ticks, startY, target, destination, riders);
  }

  public static boolean cancelOnDismount(Phase phase) {
    return phase == Phase.COUNTDOWN;
  }

  public static boolean completeOnReconnect(Phase phase) {
    return phase != Phase.COUNTDOWN;
  }

  public static boolean autoReturn(
      boolean diedOnKepler, boolean respawnedOutside, boolean enabled) {
    return diedOnKepler && respawnedOutside && enabled;
  }
}
