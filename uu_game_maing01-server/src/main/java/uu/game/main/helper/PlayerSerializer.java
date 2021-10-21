package uu.game.main.helper;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.io.StringWriter;
import uu.game.main.abl.dto.Player;


public class PlayerSerializer extends JsonSerializer<Player> {

  private final ObjectMapper mapper = new ObjectMapper();

  @Override
  public void serialize(Player value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
    StringWriter writer = new StringWriter();
    mapper.writeValue(writer, value.getPlayerId());
    gen.writeFieldName(writer.toString());
  }
}
