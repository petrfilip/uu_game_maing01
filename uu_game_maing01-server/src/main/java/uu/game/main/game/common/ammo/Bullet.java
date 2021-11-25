package uu.game.main.game.common.ammo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import uu.game.main.game.common.GameRuleEvent;
import uu.game.main.game.common.Player2D;
import uu.game.main.game.common.PlayerMove;
import uu.game.main.helper.MathUtils;

public class Bullet extends Projectile {

  /**
   * bullet speed
   */
  private final static int SPEED = 20;

  public Bullet() {
    super(0, 0, 10, 10, 0);
  }

  @Override
  public List<GameRuleEvent> applyEffect(Collection<? extends AmmoDamagable> players, Integer tick) {

    for (int i = 0; i < getSpeed(); i++) {
      computeNextPosition();

      for (AmmoDamagable damagable : players) {
        if (damagable instanceof Intersectable && ((Intersectable) damagable).intersects(this)) {
          List<GameRuleEvent> gameRuleEvents = damagable.applyAmmoDamage(-1);
          gameRuleEvents.add(new GameRuleEvent("used", this.getClass().getSimpleName(), this));
          setUsed(true);
          return gameRuleEvents;
        }
      }
    }

    //todo used when colision with obstacle

    //todo used when out of the board

    return new ArrayList<>();

  }

  @Override
  public List<GameRuleEvent> init(Player2D player2D, PlayerMove playerMove) {
    this.setAngle(playerMove.getFireAngle());
    this.setX(player2D.getX());
    this.setY(player2D.getY());

    return new GameRuleEvent("fired", this.getClass().getSimpleName(), this).asList();
  }

  @Override
  public int getSpeed() {
    return SPEED;
  }

  @Override
  public void computeNextPosition() {
    double posX = getX() + 1 * MathUtils.cos(getAngle());
    double posY = getY() + 1 * MathUtils.sin(getAngle());

    setX(MathUtils.roundUp(posX));
    setY(MathUtils.roundUp(posY));
  }
}