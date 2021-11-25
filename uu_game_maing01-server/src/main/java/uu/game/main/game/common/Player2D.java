package uu.game.main.game.common;

import uu.game.main.game.soldat.SoldatMove;
import uu.game.main.game.soldat.SoldatPlayer;

public interface Player2D<PLAYER,  MOVE> {


  Integer getX();

  void setX(Integer x);

  Integer getY();

  void setY(Integer y);

  Direction getDirection();

  void setDirection(Direction direction);


  PLAYER movePlayer(PLAYER player,  MOVE move);
}
