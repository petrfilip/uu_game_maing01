package uu.game.main.game.bulanci.helper;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import uu.game.main.game.common.ammo.Ammo;

public class PlayerAmmoSerialization extends JsonSerializer<List<Ammo>> {

  @Override
  public void serialize(List<Ammo> value, JsonGenerator jgen, SerializerProvider serializers) throws IOException {

    Map<String, List<Ammo>> groupAmmoByType = value.stream()
      .collect(Collectors.groupingBy(ammo -> ammo.getClass().getSimpleName()));

    jgen.writeStartArray();
    for (Entry<String, List<Ammo>> aggregatedAmmo : groupAmmoByType.entrySet()) {
      jgen.writeStartObject();
      jgen.writeObjectField("type", aggregatedAmmo.getKey().toLowerCase());
      jgen.writeObjectField("amount", aggregatedAmmo.getValue().size());
      jgen.writeEndObject();
    }

    jgen.writeEndArray();
  }
}
