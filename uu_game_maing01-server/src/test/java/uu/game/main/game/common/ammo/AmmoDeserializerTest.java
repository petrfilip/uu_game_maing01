package uu.game.main.game.common.ammo;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.Test;
import uu.game.main.game.soldat.SoldatMove;

/**
 * The deserializer is only reachable through Jackson, so the tests drive it the way the engine
 * does: by reading a client move off the wire.
 */
public class AmmoDeserializerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  private Ammo read(String firedJson) throws IOException {
    return objectMapper.readValue("{\"fired\":" + firedJson + "}", SoldatMove.class).getFired();
  }

  @Test
  public void resolvesKnownAmmoRegardlessOfCase() throws IOException {
    assertTrue(read("\"BULLET\"") instanceof Bullet);
    assertTrue(read("\"bullet\"") instanceof Bullet);
    assertTrue(read("\"Mine\"") instanceof Mine);
  }

  @Test
  public void unknownAmmoIsDroppedRatherThanFailingTheWholeMoveBatch() throws IOException {
    assertNull(read("\"rocketLauncher\""));
    assertNull(read("\"\""));
  }

  @Test
  public void absentOrNullAmmoMeansTheMoveSimplyDidNotFire() throws IOException {
    assertNull(read("null"));
    assertNull(objectMapper.readValue("{\"sprint\":true}", SoldatMove.class).getFired());
  }

  @Test
  public void eachDeserialisationYieldsItsOwnInstance() throws IOException {
    // Ammo is mutable - it carries its own position and spent flag - so a shared instance would
    // make every bullet on the board the same bullet.
    assertTrue(read("\"bullet\"") != read("\"bullet\""));
  }
}
