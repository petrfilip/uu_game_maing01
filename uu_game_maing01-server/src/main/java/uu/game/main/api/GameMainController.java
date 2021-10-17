package uu.game.main.api;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

import javax.inject.Inject;
import uu.app.server.CommandContext;
import uu.app.server.annotation.Command;
import uu.app.server.annotation.CommandController;
import uu.game.main.api.dto.GameMainInitDtoIn;
import uu.game.main.api.dto.GameMainInitDtoOut;
import uu.game.main.abl.GameMainAbl;

@CommandController
public final class GameMainController {

  @Inject
  private GameMainAbl gameMainAbl;

  @Command(path = "init", method = POST)
  public GameMainInitDtoOut create(CommandContext<GameMainInitDtoIn> ctx) {
    GameMainInitDtoOut dtoOut = gameMainAbl.init(ctx.getUri().getAwid(), ctx.getDtoIn());
    return dtoOut;
  }

}
