package uu.game.main.helper;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import uu.game.main.abl.dto.Player;

/**
 * Serialises a {@link Player} used as a map key into the player id.
 *
 * <p>It used to run the id through an ObjectMapper into a StringWriter and use the <em>quoted</em>
 * JSON literal as the field name, so keys came out as {@code "\"1234-5678\""} and the client had to
 * look players up with {@code players[JSON.stringify(uuIdentity)]}. Now the key is just the id.
 */
public class PlayerSerializer extends JsonSerializer<Player> {

  @Override
  public void serialize(Player value, JsonGenerator generator, SerializerProvider serializers) throws IOException {
    generator.writeFieldName(value.getPlayerId());
  }
}
