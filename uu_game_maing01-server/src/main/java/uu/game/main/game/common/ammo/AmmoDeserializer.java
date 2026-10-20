package uu.game.main.game.common.ammo;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Turns the ammo name a client sends ({@code "BULLET"}) into an instance.
 *
 * <p>The previous implementation walked the <em>entire</em> classpath through Guava's
 * {@code ClassPath.from(...).getAllClasses()} on every single deserialisation - that is, on every
 * shot fired by every player - and reflectively instantiated the match. This is a fixed registry:
 * adding ammo means adding a line, and the cost is a hash lookup.
 */
public class AmmoDeserializer extends StdDeserializer<Ammo> {

  private static final Map<String, Supplier<Ammo>> FACTORIES = new HashMap<>();

  static {
    FACTORIES.put("bullet", Bullet::new);
    FACTORIES.put("mine", Mine::new);
  }

  public AmmoDeserializer() {
    super(Ammo.class);
  }

  @Override
  public Ammo deserialize(JsonParser parser, DeserializationContext context) throws IOException {
    String name = parser.getCodec().readValue(parser, String.class);
    if (name == null) {
      return null;
    }

    Supplier<Ammo> factory = FACTORIES.get(name.toLowerCase(Locale.ROOT));
    if (factory == null) {
      // Unknown ammo is dropped rather than failing the whole move batch.
      return null;
    }
    return factory.get();
  }
}
