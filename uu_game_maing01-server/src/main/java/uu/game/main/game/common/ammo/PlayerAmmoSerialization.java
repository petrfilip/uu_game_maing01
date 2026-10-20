package uu.game.main.game.common.ammo;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Publishes a player's inventory as {@code [{type, amount}]} rather than as every individual round.
 */
public class PlayerAmmoSerialization extends JsonSerializer<List<Ammo>> {

  @Override
  public void serialize(List<Ammo> value, JsonGenerator generator, SerializerProvider serializers) throws IOException {
    Map<String, Integer> countByType = new LinkedHashMap<>();
    for (Ammo ammo : value) {
      countByType.merge(ammo.getType().toLowerCase(java.util.Locale.ROOT), 1, Integer::sum);
    }

    generator.writeStartArray();
    for (Map.Entry<String, Integer> entry : countByType.entrySet()) {
      generator.writeStartObject();
      generator.writeStringField("type", entry.getKey());
      generator.writeNumberField("amount", entry.getValue());
      generator.writeEndObject();
    }
    generator.writeEndArray();
  }
}
