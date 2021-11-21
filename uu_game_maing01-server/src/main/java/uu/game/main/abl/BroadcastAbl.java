package uu.game.main.abl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import uu.game.main.abl.dto.ObjectHolder;
import uu.game.main.abl.dto.event.GameServerEvent;
import uu.game.main.api.dto.PollDtoIn;
import uu.game.main.api.dto.PollDtoOut;

@Component
public class BroadcastAbl implements ApplicationListener<GameServerEvent> {

  // awid - roomId - List<Requests>
  private final Map<String, Map<String, List<ObjectHolder<PollDtoOut>>>> holders = new HashMap<>();

  private final Collection<SseEmitter> emitters = Collections.synchronizedCollection(new HashSet<>());

  private final Object lock = new Object();


  public PollDtoOut poll(String awid, PollDtoIn dtoIn) {

    ObjectHolder<PollDtoOut> holder = new ObjectHolder<>();
    synchronized (lock) {
      holders.putIfAbsent(awid, new HashMap<>());
      Map<String, List<ObjectHolder<PollDtoOut>>> rooms = holders.getOrDefault(awid, new HashMap<>());
      List<ObjectHolder<PollDtoOut>> room = rooms.getOrDefault(dtoIn.getRoomId(), new ArrayList<>());
      room.add(holder);
      rooms.put(dtoIn.getRoomId(), room);
      holders.put(awid, rooms);
    }
    return holder.get();
  }


  @Override
  public void onApplicationEvent(GameServerEvent event) {
    synchronized (lock) {
      Map<String, List<ObjectHolder<PollDtoOut>>> rooms = holders.getOrDefault(event.getAwid(), new HashMap<>());
      List<ObjectHolder<PollDtoOut>> room = rooms.getOrDefault(event.getRoomId(), new ArrayList<>());
      PollDtoOut dtoOut = new PollDtoOut(event.getOutput(), event.getClass().getSimpleName());
      sendToHolders(room, dtoOut);
      sendToEmitters(dtoOut);
      room.clear();
    }
  }

  private void sendToHolders(List<ObjectHolder<PollDtoOut>> room, PollDtoOut dtoOut) {
    for (ObjectHolder<PollDtoOut> holder : room) {
      holder.set(dtoOut);
    }
  }

  private void sendToEmitters(PollDtoOut dtoOut) {

    emitters.forEach(sseEmitter -> {
      try {
        sseEmitter.send(dtoOut);
      } catch (IOException e) {
        unsubscribe(sseEmitter);
        sseEmitter.completeWithError(e);
      }
    });
  }

  public void subscribe(SseEmitter sseEmitter) {
    emitters.add(sseEmitter);
  }

  public void unsubscribe(SseEmitter sseEmitter) {
    emitters.remove(sseEmitter);
  }
}
