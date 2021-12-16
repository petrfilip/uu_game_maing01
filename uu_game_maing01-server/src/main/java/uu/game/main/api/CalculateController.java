package uu.game.main.api;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

import javax.inject.Inject;
import uu.app.datastore.domain.PagedResult;
import uu.app.server.CommandContext;
import uu.app.server.annotation.Command;
import uu.app.server.annotation.CommandController;
import uu.game.main.abl.CalculateAbl;
import uu.game.main.abl.entity.Calculate;
import uu.game.main.api.dto.CalculateDtoIn;

@CommandController
public class CalculateController {

  @Inject
  private CalculateAbl calculateAbl;

  @Command(path = "/calculate/create", method = POST, spp = "/calculateSpp")
  public Calculate create(CommandContext<CalculateDtoIn> ctx) {
    Calculate dtoOut = calculateAbl.calculate(ctx.getUri().getAwid(), ctx.getDtoIn());
    return dtoOut;
  }


  @Command(path = "/calculate/list", method = GET)
  public PagedResult<Calculate> list(CommandContext<CalculateDtoIn> ctx) {
    PagedResult<Calculate> dtoOut = calculateAbl.list(ctx.getUri().getAwid(), ctx.getDtoIn());
    return dtoOut;
  }


}
