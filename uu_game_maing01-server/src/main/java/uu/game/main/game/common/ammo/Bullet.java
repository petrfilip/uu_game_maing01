package uu.game.main.game.common.ammo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import uu.game.main.abl.dto.Player;
import uu.game.main.game.common.GameRuleEvent;
import uu.game.main.game.common.Player2D;
import uu.game.main.game.common.PlayerMove;
import uu.game.main.helper.MathUtils;

public class Bullet extends Projectile {

  /**
   * bullet speed
   */
  private static final int SPEED = 3;

  public Bullet() {
    super(0, 0, 10, 10, 0);
  }

  @Override
  public List<GameRuleEvent> applyEffect(Collection<? extends AmmoDamagable> players, Integer tick) {

    for (int i = 0; i < getSpeed(); i++) {
      computeNextPosition();

      if(checkOutOfBound()){
        setUsed(true);
        break;
      }

      for (AmmoDamagable damagable : players) {
        if (damagable instanceof Intersectable && ((Intersectable) damagable).intersects(this)) {
          List<GameRuleEvent> gameRuleEvents = damagable.applyAmmoDamage(1);

          HashMap<Object, Object> sourceTargetMap = new HashMap<>();
          sourceTargetMap.put("source", this);
          sourceTargetMap.put("target", damagable);
          gameRuleEvents.add(new GameRuleEvent("used", this.getClass().getSimpleName(), sourceTargetMap));
          setUsed(true);
          getOwner().setScore(getOwner().getScore()+1);
          return gameRuleEvents;
        }
      }
    }

    //todo used when colision with obstacle

    //todo used when out of the board

    return new ArrayList<>();

  }

  @Override
  public List<GameRuleEvent> init(Player userPlayer, Player2D player2D, PlayerMove playerMove) {
    this.setOwner(userPlayer);
    this.setAngle(playerMove.getFiredAngle());

    double r = Math.sqrt(Math.pow(player2D.getWidth(), 2) + Math.pow(player2D.getWidth(), 2));

    this.setX((player2D.getX() + (getWidth().doubleValue()/2)) + r * MathUtils.cos(getAngle()));
    this.setY((player2D.getY() + (getHeight().doubleValue()/2)) + r * MathUtils.sin(getAngle()));

    return new GameRuleEvent("fired", this.getClass().getSimpleName(), this).asList();
  }

  @Override
  public int getSpeed() {
    return SPEED;
  }

  @Override
  public void computeNextPosition() {
    double posX = getX() + 5 * MathUtils.cos(getAngle());
    double posY = getY() + 5 * MathUtils.sin(getAngle());

    setX(posX);
    setY(posY);
  }
}
