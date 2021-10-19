package uu.game.main.abl;

import java.util.ArrayList;
import java.util.List;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import uu.game.main.abl.dto.ObjectHolder;
import uu.game.main.abl.dto.event.GameServerEvent;
import uu.game.main.api.dto.PollDtoOut;

@Component
public class BroadcastAbl implements ApplicationListener<GameServerEvent> {

  private final List<ObjectHolder<PollDtoOut>> holders = new ArrayList<>();

  private final Object lock = new Object();


  public PollDtoOut poll() {
    ObjectHolder<PollDtoOut> holder = new ObjectHolder<>();
    synchronized (lock) {
      holders.add(holder);
    }
    return holder.get();
  }


  @Override
  public void onApplicationEvent(GameServerEvent event) {
    synchronized (lock) {
      for (ObjectHolder<PollDtoOut> holder : holders) {
        holder.set(new PollDtoOut(event.getOutput())); //todo room event / game event
      }
      holders.clear(); //todo clear by group
    }
  }
}
