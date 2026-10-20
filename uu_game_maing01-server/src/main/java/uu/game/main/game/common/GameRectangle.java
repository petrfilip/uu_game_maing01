package uu.game.main.game.common;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.annotation.JsonIgnore;
import uu.game.main.game.common.ammo.Intersectable;

/**
 * Axis aligned bounding box with sub-pixel precision.
 *
 * <p>Overlap is computed directly on {@code double} coordinates. The previous implementation
 * converted to {@link java.awt.Rectangle}, which truncated positions to {@code int} and made
 * collisions jitter by up to one pixel depending on which side of the grid an entity sat on.
 */
public class GameRectangle implements Intersectable {

  private double x;
  private double y;
  private Integer width;
  private Integer height;

  public GameRectangle(double x, double y, Integer width, Integer height) {
    this.x = x;
    this.y = y;
    this.width = width;
    this.height = height;
  }

  @JsonIgnore
  public double getRight() {
    return x + (width == null ? 0 : width);
  }

  @JsonIgnore
  public double getBottom() {
    return y + (height == null ? 0 : height);
  }

  @JsonIgnore
  public double getCenterX() {
    return x + (width == null ? 0 : width) / 2.0;
  }

  @JsonIgnore
  public double getCenterY() {
    return y + (height == null ? 0 : height) / 2.0;
  }

  /**
   * @return a detached copy translated by the given offset, useful for "would I collide if I moved
   * there" probes without mutating the entity.
   */
  public GameRectangle translated(double dx, double dy) {
    return new GameRectangle(x + dx, y + dy, width, height);
  }

  @JsonSerialize(using = CompactDoubleSerializer.class)
  public double getX() {
    return x;
  }

  public void setX(double x) {
    this.x = x;
  }

  @JsonSerialize(using = CompactDoubleSerializer.class)
  public double getY() {
    return y;
  }

  public void setY(double y) {
    this.y = y;
  }

  public Integer getWidth() {
    return width;
  }

  public void setWidth(Integer width) {
    this.width = width;
  }

  public Integer getHeight() {
    return height;
  }

  public void setHeight(Integer height) {
    this.height = height;
  }

  @Override
  public boolean intersects(GameRectangle hitArea) {
    if (hitArea == null || width == null || height == null
      || hitArea.getWidth() == null || hitArea.getHeight() == null) {
      return false;
    }
    return x < hitArea.getRight()
      && getRight() > hitArea.getX()
      && y < hitArea.getBottom()
      && getBottom() > hitArea.getY();
  }
}
