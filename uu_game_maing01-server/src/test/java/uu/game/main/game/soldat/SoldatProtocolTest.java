package uu.game.main.game.soldat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.Test;
import uu.game.main.abl.dto.Player;
import uu.game.main.domain.GameState;
import uu.game.main.game.common.Direction;
import uu.game.main.game.common.GameRandom;
import uu.game.main.game.common.GameplayModeEnum;
import uu.game.main.game.common.ammo.Bullet;
import uu.game.main.game.soldat.specialability.BonusItemService;
import uu.game.main.game.soldat.specialability.FreezeOpponentsSpecialAbility;
import uu.game.main.game.soldat.specialability.ImmortalitySpecialAbility;
import uu.game.main.game.soldat.specialability.IncreaseLiveSpecialAbility;
import uu.game.main.game.soldat.specialability.IncreasedSpeedSpecialAbility;
import uu.game.main.game.soldat.specialability.SpecialAbility;

/**
 * What the server actually puts on the wire, checked on a full-size Soldat match rather than on a
 * hand-built board - the level is the whole point here, and it only exists in a real match.
 *
 * <p>The protocol has two payloads: a full state, sent when a client connects and when a match
 * starts, and a per-tick state carrying only what moved. These tests pin the split down from the
 * outside, on the JSON, because that is the only thing a browser ever sees.
 */
public class SoldatProtocolTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** A real workspace id looks like this, and it must never leave the server. */
  private static final String AWID = "fedcba9876543210fedcba9876543210";

  private static final long SEED = 424242L;

  /** Long enough for crates to drop, bullets to fly and players to die and respawn. */
  private static final int TICKS = 300;

  /** Everything a client is allowed to be told about an event. */
  private static final Set<String> EVENT_FIELDS =
    new HashSet<>(Arrays.asList("action", "objectName", "id", "x", "y"));

  @Test
  public void aPerTickPayloadCarriesNoLevelGeometry() throws Exception {
    Match match = play(60);

    JsonNode tick = json(match.state);
    // Absent rather than false: a flag that is false on every tick but two is worth leaving out,
    // and a client reads its absence as the delta it is.
    JsonNode full = tick.get("full");
    assertTrue("the tick payload must not announce itself as a complete picture",
      full == null || !full.asBoolean());
    assertFalse("obstacles are static and must not be in a tick payload", contains(tick, "obstacles"));
    assertFalse("nor the walls they are made of", contains(tick, "walls"));
    assertFalse("nor the arena they stand in", contains(tick, "area"));

    // The measurement this whole change exists for. The board really is the full map: twenty
    // obstacle groups, seventy odd walls.
    int tickBytes = bytes(match.state);
    int fullBytes = bytes(match.state.asFullState(match.rule.getSetup()));
    System.out.println("per-tick payload = " + tickBytes + " B, full state = " + fullBytes
      + " B, obstacles = " + match.state.getGame().getObstacles().size() + " groups");

    assertTrue("a tick payload that is anywhere near the size of a full state is still carrying the map"
      + " (tick=" + tickBytes + "B, full=" + fullBytes + "B)", tickBytes * 2 < fullBytes);
  }

  @Test
  public void theFullStateCarriesTheLevelGeometry() throws Exception {
    Match match = play(60);

    JsonNode full = json(match.state.asFullState(match.rule.getSetup()));
    assertTrue("a client that has just connected has nothing to merge into", full.get("full").asBoolean());

    JsonNode setup = full.get("setup");
    assertTrue("the level must be in the state a client starts from", setup.get("obstacles").size() > 0);
    assertTrue("obstacles are only useful with their geometry",
      setup.get("obstacles").get(0).get("walls").size() > 0);
    assertEquals(1400, setup.get("area").get("width").asInt());
    assertEquals(600, setup.get("area").get("height").asInt());
    // Match settings are static too, and the client draws the timer and the mode from them.
    assertEquals(GameplayModeEnum.TIME_LIMIT.name(), setup.get("params").get("gameMode").asText());
    assertEquals(SEED, setup.get("params").get("seed").asLong());

    // Still a live snapshot, not just a level: a mid-match joiner has to see where everybody is.
    assertTrue(full.get("game").get("players").size() > 0);
  }

  @Test
  public void coordinatesArePublishedWithScreenPrecisionAndNoMore() throws Exception {
    Match match = play(240);

    String board = MAPPER.writeValueAsString(match.state.getGame());
    Matcher noisy = Pattern.compile("-?\\d+\\.\\d{2,}").matcher(board);

    // Positions come out of repeated floating point arithmetic, so a player standing at 614.6 used
    // to be published as 614.5999999999999 - seventeen characters to place something on a pixel
    // grid, for every entity, twenty times a second.
    assertFalse("a coordinate was published with more precision than a screen can show: "
      + (noisy.find() ? noisy.group() : ""), noisy.find(0));
    assertTrue("this run published no coordinates at all, so it asserts nothing",
      board.contains("\"x\":"));

    // And a whole number is written as one: things come to rest on a pixel grid, so ".0" is two
    // characters of nothing on most coordinates in most payloads.
    Matcher trailing = Pattern.compile("\\.0(?![0-9])").matcher(board);
    assertFalse("a whole coordinate was published as a decimal", trailing.find());
  }

  @Test
  public void aTickWhereNothingHappenedCarriesNothingItDoesNotNeed() throws Exception {
    Match match = play(60);

    int quiet = 60;
    while (!match.state.getGameEvents().isEmpty() && quiet < 300) {
      match.tick(quiet++);
    }
    assertTrue("this run never had a quiet tick, so it asserts nothing",
      match.state.getGameEvents().isEmpty());

    JsonNode tick = json(match.state);
    // Most ticks in a match are quiet, and most of a match is not a full state; both used to cost
    // their field name and value on every one of them.
    assertFalse("a quiet tick published an empty event list", tick.has("gameEvents"));
    assertFalse("a per-tick payload published the full-state flag", tick.has("full"));
  }

  @Test
  public void theWorkspaceIdIsInNoPayload() throws Exception {
    Match match = play(5);

    String tick = MAPPER.writeValueAsString(match.state);
    String full = MAPPER.writeValueAsString(match.state.asFullState(match.rule.getSetup()));

    // It is an internal identifier of the uuApp workspace. It used to sit in `params` and therefore
    // went to every browser twenty times a second.
    assertFalse("awid leaked into a tick payload", tick.contains(AWID));
    assertFalse("awid leaked into the full state", full.contains(AWID));
    assertFalse(tick.contains("\"awid\""));
    assertFalse(full.contains("\"awid\""));

    // ...while the rule still has it, which is what the leaderboard write needs.
    assertEquals(AWID, awidHeldByRule(match.rule));
  }

  @Test
  public void aFullStateFollowedByTicksLeavesTheSamePictureAsConnectingLate() throws Exception {
    Match match = play(0);

    // What a client that was there from the start receives: one full state, then deltas.
    ObjectNode picture = (ObjectNode) json(match.state.asFullState(match.rule.getSetup()));
    for (int tick = 0; tick < 120; tick++) {
      match.tick(tick);
      apply(picture, json(match.state));
    }

    // What a client connecting at that same moment receives instead.
    ObjectNode lateJoiner = (ObjectNode) json(match.state.asFullState(match.rule.getSetup()));

    // Two things describe the payload rather than the picture drawn from it: the events of the tick
    // that produced it, which are deliberately not replayed to a late joiner, and the flag saying
    // whether the payload was self-contained.
    picture.remove("gameEvents");
    picture.remove("full");
    lateJoiner.remove("gameEvents");
    lateJoiner.remove("full");

    assertEquals("a field that changes during a match is being published as if it were static",
      lateJoiner, picture);
  }

  @Test
  public void eventsCarryIdentifiersAndPositionsRatherThanEntities() throws Exception {
    Match match = play(0);

    Set<String> actions = new LinkedHashSet<>();
    Set<String> playerIds = new HashSet<>();
    for (Player player : match.state.getGame().getPlayers().keySet()) {
      playerIds.add(player.getPlayerId());
    }

    for (int tick = 0; tick < TICKS; tick++) {
      match.tick(tick);
      // path, not get: a tick where nothing happened carries no event list at all.
      for (JsonNode event : json(match.state).path("gameEvents")) {
        actions.add(event.get("action").asText());

        Iterator<String> fields = event.fieldNames();
        while (fields.hasNext()) {
          String field = fields.next();
          assertTrue("an event published " + field + ": " + event, EVENT_FIELDS.contains(field));
          assertTrue("an event embedded a whole object under " + field + ": " + event,
            event.get(field).isValueNode());
        }

        if ("death".equals(event.get("action").asText())) {
          // Who died has to be identifiable without shipping the player: the board's player map is
          // keyed by exactly this id.
          assertTrue("a death event must name the player that died: " + event, event.has("id"));
          assertTrue("unknown player id in " + event, playerIds.contains(event.get("id").asText()));
          assertTrue("an effect has to be drawn somewhere: " + event, event.has("x") && event.has("y"));
        }
      }
    }

    // Without this the loop above would pass just as happily on a match in which nothing happened.
    assertTrue("this match no longer produces deaths, so the assertions above prove nothing",
      actions.contains("death"));
    assertTrue(actions.contains("fired"));
    assertTrue(actions.contains("used"));
  }

  // --- harness -------------------------------------------------------------------------------

  /** A running match, driven exactly the way the engine drives one. */
  private static final class Match {

    private SoldatRule rule;
    private GameState<SoldatBoard> state;
    private final Random input = new Random(20240101L);

    void tick(int tick) {
      state.setTick(tick);
      rule.getNextGameState(state, movesFor(state.getGame(), input));
    }
  }

  /**
   * Starts a match with the bot and three humans and runs it for a while.
   *
   * @param ticks how many ticks to simulate before handing the match back
   */
  private Match play(int ticks) throws Exception {
    Match match = new Match();
    match.rule = newRule();

    Map<String, Object> params = new HashMap<>();
    params.put(SoldatRule.PARAM_BOT, true);
    params.put(SoldatRule.PARAM_AWID, AWID);
    params.put(SoldatRule.PARAM_GAME_MODE, GameplayModeEnum.TIME_LIMIT.name());
    // Comfortably longer than any run here, so the match is still live at the last tick compared.
    params.put(SoldatRule.PARAM_SECOND_LIMIT, 600);
    params.put(SoldatRule.PARAM_INITIAL_LIVES, 1);
    params.put(SoldatRule.PARAM_BONUS_DROP_SECONDS, 1);
    params.put(GameRandom.PARAM_SEED, SEED);

    List<Player> humans = Arrays.asList(player("p1"), player("p2"), player("p3"));
    match.state = new GameState<>();
    match.state.setParams(params);
    match.rule.init(match.state, humans);
    // The engine attaches the roster to every state it publishes; without it these payloads would
    // be missing a part the client renders.
    match.state.setPlayers(new ArrayList<>(humans));

    for (int tick = 0; tick < ticks; tick++) {
      match.tick(tick);
    }
    return match;
  }

  private static SoldatRule newRule() throws Exception {
    SoldatRule rule = new SoldatRule();
    Field field = SoldatRule.class.getDeclaredField("bonusItemService");
    field.setAccessible(true);
    field.set(rule, new BonusItemService(Arrays.<SpecialAbility>asList(
      new IncreaseLiveSpecialAbility(), new ImmortalitySpecialAbility(),
      new IncreasedSpeedSpecialAbility(), new FreezeOpponentsSpecialAbility())));
    return rule;
  }

  private static String awidHeldByRule(SoldatRule rule) throws Exception {
    Field field = SoldatRule.class.getDeclaredField("awid");
    field.setAccessible(true);
    return (String) field.get(rule);
  }

  /** Input that moves everybody around the level and into each other's fire. */
  private static Map<Player, List<SoldatMove>> movesFor(SoldatBoard board, Random input) {
    Map<Player, List<SoldatMove>> moves = new HashMap<>();
    for (Player player : board.getPlayers().keySet()) {
      SoldatMove move = new SoldatMove();
      int direction = input.nextInt(10);
      if (direction < 4) {
        move.setMove(Direction.RIGHT);
      } else if (direction < 7) {
        move.setMove(Direction.LEFT);
      } else if (direction < 9) {
        move.setMove(Direction.UP);
      }
      if (input.nextInt(3) == 0) {
        move.setFired(new Bullet());
        move.setFiredAngle(input.nextInt(360) - 180);
      }
      moves.put(player, Collections.singletonList(move));
    }
    return moves;
  }

  /**
   * What the client does with an incoming payload: everything it carries replaces what was held,
   * and whatever it leaves out - the setup - keeps its previous value.
   */
  private static void apply(ObjectNode picture, JsonNode incoming) {
    Iterator<String> fields = incoming.fieldNames();
    while (fields.hasNext()) {
      String field = fields.next();
      picture.set(field, incoming.get(field));
    }
  }

  private static boolean contains(JsonNode node, String fieldName) {
    if (node.has(fieldName)) {
      return true;
    }
    for (JsonNode child : node) {
      if (contains(child, fieldName)) {
        return true;
      }
    }
    return false;
  }

  private static JsonNode json(Object value) throws Exception {
    // Through the string, not valueToTree: this has to be the bytes a client receives.
    return MAPPER.readTree(MAPPER.writeValueAsString(value));
  }

  private static int bytes(Object value) throws Exception {
    return MAPPER.writeValueAsString(value).getBytes(StandardCharsets.UTF_8).length;
  }

  private static Player player(String id) {
    Player player = new Player(id);
    player.setPlayerName("name of " + id);
    return player;
  }
}
