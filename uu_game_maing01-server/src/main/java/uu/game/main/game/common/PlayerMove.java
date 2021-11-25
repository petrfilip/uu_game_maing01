package uu.game.main.game.common;

import uu.game.main.game.common.ammo.Ammo;

public interface PlayerMove {
  Direction getMove();

  void setMove(Direction move);

  Ammo getFired() ;

  void setFired(Ammo fired);

  boolean isSprint();

  void setSprint(boolean sprint);

  int getFireAngle();

  void setFireAngle(int fireAngle);
}
