package uu.game.main.api;

import javax.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import uu.app.server.CommandContext;
import uu.app.server.annotation.Command;
import uu.app.server.annotation.CommandController;
import uu.game.main.abl.BroadcastAbl;
import uu.game.main.api.dto.PollDtoIn;
import uu.game.main.api.dto.PollDtoOut;

@CommandController
public class BroadcastController {

  private static final Logger LOGGER = LogManager.getLogger(BroadcastController.class);

  /**
   * Long enough that a healthy client is not reconnecting constantly, short enough that a dead
   * connection is eventually reclaimed. {@code Long.MAX_VALUE} meant a half-open connection held a
   * subscription for the lifetime of the server.
   */
  private static final long SSE_TIMEOUT_MILLIS = 10 * 60 * 1000L;

  @Inject
  private BroadcastAbl broadcastAbl;

  @Command(path = "polling/poll", method = RequestMethod.POST)
  public PollDtoOut poll(CommandContext<PollDtoIn> ctx) {
    return broadcastAbl.poll(ctx.getUri().getAwid(), identityOf(ctx), ctx.getDtoIn());
  }

  @Command(path = "sse", method = RequestMethod.GET)
  public SseEmitter sse(CommandContext<PollDtoIn> ctx) {
    String roomId = ctx.getDtoIn() == null ? null : ctx.getDtoIn().getRoomId();
    if (roomId == null) {
      throw new IllegalArgumentException("roomId is required to subscribe");
    }

    SseEmitter sseEmitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
    // Subscription is scoped to the room and to the players in it, so a client no longer receives
    // every game on the server - nor any game it has not joined.
    broadcastAbl.subscribe(ctx.getUri().getAwid(), roomId, identityOf(ctx), sseEmitter);
    LOGGER.debug("SSE opened for room {}", roomId);
    return sseEmitter;
  }

  /**
   * The caller's identity, taken from the session rather than from the request body: it decides
   * which rooms the caller may watch, so a client must not be able to name itself.
   */
  private String identityOf(CommandContext<?> ctx) {
    return ctx.getAuthenticationSession().getIdentity().getUUIdentity();
  }
}
