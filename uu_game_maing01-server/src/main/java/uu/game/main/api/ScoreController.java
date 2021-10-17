package uu.game.main.api;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

import javax.inject.Inject;
import uu.app.server.CommandContext;
import uu.app.server.annotation.Command;
import uu.app.server.annotation.CommandController;
import uu.game.main.abl.ScoreAbl;
import uu.game.main.api.dto.ScoreGetByUuIdentityDtoIn;
import uu.game.main.api.dto.ScoreGetDtoOut;
import uu.game.main.api.dto.ScoreListDtoIn;
import uu.game.main.api.dto.ScoreListDtoOut;

@CommandController
public final class ScoreController {

  @Inject
  private ScoreAbl scoreAbl;

  @Command(path = "score/list", method = GET)
  public ScoreListDtoOut list(CommandContext<ScoreListDtoIn> ctx) {
    return scoreAbl.list(ctx.getUri().getAwid(), ctx.getDtoIn());
  }

  @Command(path = "score/getByUuIdentity", method = GET)
  public ScoreGetDtoOut getByUuIdentity(CommandContext<ScoreGetByUuIdentityDtoIn> ctx) {
    return scoreAbl.getByUuIdentity(ctx.getUri().getAwid(), ctx.getAuthenticationSession().getIdentity().getUUIdentity());
  }

}
