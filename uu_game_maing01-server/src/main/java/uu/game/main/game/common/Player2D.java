package uu.game.main.game.common;

public interface Player2D<PLAYER extends GameRectangle, MOVE extends PlayerMove, GAMEBOARD extends GameBoard> {

  Integer getX();

  void setX(Integer x);

  Integer getY();

  void setY(Integer y);

  Direction getDirection();

  void setDirection(Direction direction);

  PLAYER movePlayer(MOVE move, GAMEBOARD gameBoard);
}