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
import uu.game.main.api.dto.job.ActiveJob;
import uu.game.main.helper.AsyncJobClient;

@CommandController
public class CalculateController {

  @Inject
  private CalculateAbl calculateAbl;

  @Inject
  private AsyncJobClient asyncJobClient;

  @Command(path = "/calculateAsync/create", method = POST, spp = "async")
  public Calculate createAsync(CommandContext<CalculateDtoIn> ctx) {
    Calculate dtoOut = calculateAbl.calculate(ctx.getUri().getAwid(), ctx.getDtoIn());
    return dtoOut;
  }

  @Command(path = "/calculate/createAsyncJob", method = POST)
  public ActiveJob createAsyncJob(CommandContext<CalculateDtoIn> ctx) {
    ActiveJob dtoOut = asyncJobClient.startAsyncJob(ctx);
    return dtoOut;
  }

  @Command(path = "/calculate/list", method = GET)
  public PagedResult<Calculate> list(CommandContext<CalculateDtoIn> ctx) {
    PagedResult<Calculate> dtoOut = calculateAbl.list(ctx.getUri().getAwid(), ctx.getDtoIn());
    return dtoOut;
  }
}
