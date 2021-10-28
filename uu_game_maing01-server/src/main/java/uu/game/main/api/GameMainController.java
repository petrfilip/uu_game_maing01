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

  public static final String WORKSPACE_INIT_CMD = "sys/uuAppWorkspace/init";

  @Inject
  private GameMainAbl gameMainAbl;

  @Command(path = WORKSPACE_INIT_CMD, method = POST)
  public GameMainInitDtoOut create(CommandContext<GameMainInitDtoIn> ctx) {
    GameMainInitDtoOut dtoOut = gameMainAbl.init(ctx.getUri().getAwid(), ctx.getDtoIn());
    return dtoOut;
  }

}
