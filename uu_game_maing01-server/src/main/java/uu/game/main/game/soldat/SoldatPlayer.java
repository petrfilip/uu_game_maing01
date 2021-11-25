package uu.game.main.game.soldat;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import uu.game.main.game.common.Direction;
import uu.game.main.game.common.GameRectangle;
import uu.game.main.game.common.GameRuleEvent;
import uu.game.main.game.common.ammo.AmmoDamagable;

public class SoldatPlayer extends GameRectangle implements AmmoDamagable {

  private Integer lives = 1;
  private Direction direction;
  private Integer speed = 3;
  private Direction jump;
  private Integer jumpUpStarted;
  private boolean sprint;


  public SoldatPlayer(Direction direction, Integer x, Integer y, Integer width, Integer height) {
    super(x, y, width, height);
    this.direction = direction;
  }

  @Override
  public List<GameRuleEvent> applyAmmoDamage(Integer damage) {
    this.lives = this.lives - damage;
    if ( lives > 0) {
      return Collections.singletonList(new GameRuleEvent("liveDecreased", this.getClass().getSimpleName(), this));
    } else {
      return Collections.singletonList(new GameRuleEvent("death", this.getClass().getSimpleName(), this));
    }
  }

  @Override
  public boolean intersects(Collection<? extends GameRectangle> gameRectangles) {
    return super.intersects(gameRectangles);
  }
}
