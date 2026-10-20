package uu.game.main.game.common;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;

/**
 * Writes a coordinate with the precision a screen can show, and no more.
 *
 * <p>Positions are the product of repeated floating point arithmetic, so a player standing at
 * 614.6 is published as {@code 614.5999999999999} - seventeen characters to place something on a
 * pixel grid, for every entity, twenty times a second. This rounds to a tenth of a pixel and drops
 * a trailing {@code .0} entirely.
 *
 * <p>Only the wire is affected. The simulation keeps full precision, because rounding the state
 * itself would let the error feed back into the next tick; this is the last thing that happens to a
 * number on its way out.
 */
public class CompactDoubleSerializer extends JsonSerializer<Double> {

  @Override
  public void serialize(Double value, JsonGenerator generator, SerializerProvider provider)
    throws IOException {
    if (value == null) {
      generator.writeNull();
      return;
    }

    double rounded = Math.round(value * 10.0) / 10.0;
    // An integral value as an integer: "1302" rather than "1302.0", which is most of them, because
    // the arena is a pixel grid and things come to rest on it.
    if (rounded == Math.rint(rounded) && Math.abs(rounded) < Long.MAX_VALUE) {
      generator.writeNumber((long) rounded);
    } else {
      generator.writeNumber(rounded);
    }
  }
}
