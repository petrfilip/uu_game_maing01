package uu.game.main.api;


import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
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

  private final ExecutorService executor = Executors.newWorkStealingPool(50);

  @PostConstruct
  public void init() {
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      executor.shutdown();
      try {
        executor.awaitTermination(1, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        LOGGER.error(e.toString());
      }
    }));
  }


  @Inject
  private BroadcastAbl broadcastAbl;

  @Command(path = "polling/poll", method = RequestMethod.POST)
  public PollDtoOut poll(CommandContext<PollDtoIn> ctx) {
    return broadcastAbl.poll(ctx.getUri().getAwid(), ctx.getDtoIn());
  }

  @Command(path = "sse", method = RequestMethod.GET)
  public SseEmitter sse(CommandContext<PollDtoIn> ctx) {
    SseEmitter sseEmitter = new SseEmitter(Long.MAX_VALUE);

    LOGGER.info("Event emitted");
    broadcastAbl.subscribe(sseEmitter);
    sseEmitter.onCompletion(() -> {
      broadcastAbl.unsubscribe(sseEmitter);
      LOGGER.info("SSE - completed");
    });
    sseEmitter.onTimeout(() -> {
      broadcastAbl.unsubscribe(sseEmitter);
      LOGGER.info("SSE - timeout ");
    });

    return sseEmitter;
  }

}
