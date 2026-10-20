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
    // The uuCmd has always answered for the caller's own identity and a uuIdentity sent by the client
    // is deliberately ignored, so the dtoIn handed to the ABL is the one built from the session - that
    // is also the value worth validating, a session without a usable uuIdentity must not reach the DAO.
    ScoreGetByUuIdentityDtoIn dtoIn = new ScoreGetByUuIdentityDtoIn();
    dtoIn.setUuIdentity(ctx.getAuthenticationSession().getIdentity().getUUIdentity());
    return scoreAbl.getByUuIdentity(ctx.getUri().getAwid(), dtoIn);
  }

}
