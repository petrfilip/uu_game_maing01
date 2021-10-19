package uu.game.main.api;


import javax.inject.Inject;
import org.springframework.web.bind.annotation.RequestMethod;
import uu.app.server.CommandContext;
import uu.app.server.annotation.Command;
import uu.app.server.annotation.CommandController;
import uu.game.main.abl.BroadcastAbl;
import uu.game.main.api.dto.PollDtoIn;
import uu.game.main.api.dto.PollDtoOut;

@CommandController
public class BroadcastController {

  @Inject
  private BroadcastAbl broadcastAbl;

  @Command(path = "polling/poll", method = RequestMethod.POST)
  public PollDtoOut poll(CommandContext<PollDtoIn> ctx) {
    return broadcastAbl.poll();
  }

}
