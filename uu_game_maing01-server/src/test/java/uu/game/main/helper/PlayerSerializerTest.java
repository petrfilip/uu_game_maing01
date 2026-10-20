package uu.game.main.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import uu.game.main.abl.dto.Player;

public class PlayerSerializerTest {

  /** Mirrors how {@code SoldatBoard} publishes its player map. */
  public static class Board {

    @JsonSerialize(keyUsing = PlayerSerializer.class)
    private final Map<Player, String> players = new LinkedHashMap<>();

    public Map<Player, String> getPlayers() {
      return players;
    }
  }

  @Test
  public void writesTheRawPlayerIdAsTheMapKey() throws IOException {
    Board board = new Board();
    board.getPlayers().put(new Player("1234-5678"), "soldier");

    JsonNode json = new ObjectMapper().valueToTree(board).get("players");

    // The old output was the quoted JSON literal, so keys came out as "\"1234-5678\"" and the
    // client had to look players up with JSON.stringify(uuIdentity).
    assertTrue("key was " + json.fieldNames().next(), json.has("1234-5678"));
    assertEquals("soldier", json.get("1234-5678").asText());
  }

  @Test
  public void keepsOneEntryPerPlayer() throws IOException {
    Board board = new Board();
    board.getPlayers().put(new Player("a"), "one");
    board.getPlayers().put(new Player("b"), "two");

    JsonNode json = new ObjectMapper().valueToTree(board).get("players");

    assertEquals(2, json.size());
    assertEquals("one", json.get("a").asText());
    assertEquals("two", json.get("b").asText());
  }
}
