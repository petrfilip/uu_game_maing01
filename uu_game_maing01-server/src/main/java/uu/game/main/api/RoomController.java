package uu.game.main.api;

import static org.springframework.web.bind.annotation.RequestMethod.GET;
import static org.springframework.web.bind.annotation.RequestMethod.POST;

import javax.inject.Inject;
import uu.app.server.CommandContext;
import uu.app.server.annotation.Command;
import uu.app.server.annotation.CommandController;
import uu.game.main.abl.RoomAbl;
import uu.game.main.abl.dto.Room;
import uu.game.main.api.dto.RoomCreateDtoIn;
import uu.game.main.api.dto.RoomJoinDtoIn;
import uu.game.main.api.dto.RoomListDtoOut;

@CommandController
public final class RoomController {

  @Inject
  private RoomAbl roomAbl;

  @Command(path = "room/create", method = POST)
  public Room roomCreate(CommandContext<RoomCreateDtoIn> ctx) {
    ctx.getDtoIn().setRoomOwner(ctx.getAuthenticationSession().getIdentity().getUUIdentity());
    return roomAbl.roomCreate(ctx.getUri().getAwid(), ctx.getDtoIn());
  }

  @Command(path = "room/list", method = GET)
  public RoomListDtoOut roomList(CommandContext<Void> ctx) {
    return roomAbl.roomList(ctx.getUri().getAwid());
  }

  @Command(path = "room/join", method = POST)
  public Room roomJoin(CommandContext<RoomJoinDtoIn> ctx) {
    ctx.getDtoIn().setPlayerId(ctx.getAuthenticationSession().getIdentity().getUUIdentity());
    return roomAbl.roomJoinPlayer(ctx.getUri().getAwid(), ctx.getDtoIn());
  }


}
