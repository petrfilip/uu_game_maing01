package uu.game.main.helper;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import uu.game.main.abl.dto.event.GameServerEvent;

@Component
public class EventPublisherHelper {

  private final ApplicationEventPublisher applicationEventPublisher;

  public EventPublisherHelper(ApplicationEventPublisher applicationEventPublisher) {
    this.applicationEventPublisher = applicationEventPublisher;
  }

  public void publish(GameServerEvent event) {
    applicationEventPublisher.publishEvent(event);
  }
}
