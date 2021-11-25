package uu.game.main.game.common;

public interface Player2D<PLAYER extends GameRectangle, MOVE extends PlayerMove, GAMEBOARD extends GameBoard> {

  double getX();

  void setX(double x);

  double getY();

  void setY(double y);

  Integer getWidth();

  void setWidth(Integer width);

  Integer getHeight();

  void setHeight(Integer height);

  Direction getDirection();

  void setDirection(Direction direction);

  PLAYER movePlayer(MOVE move, GAMEBOARD gameBoard);
}
