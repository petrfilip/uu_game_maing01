package uu.game.main.game.soldat;

import java.util.ArrayList;
import java.util.List;
import uu.game.main.game.common.GameArea;
import uu.game.main.game.common.GameRandom;
import uu.game.main.game.common.GameRectangle;
import uu.game.main.game.common.Obstacle;
import uu.game.main.game.common.ObstacleTypeEnum;

/**
 * The Soldat arena. Extracted from the rule so level design and simulation are separate concerns -
 * the rule no longer carries eighty lines of coordinates, and a second map is a second method here.
 */
final class SoldatMap {

  private static final int WALL_SIZE = 25;
  private static final int BOX_SIZE = 50;

  private SoldatMap() {
  }

  /**
   * @param random the match's randomness - the troll face is placed at random, and a match cannot be
   *     replayed if its level differs from one run to the next
   */
  static List<Obstacle> build(GameArea area, GameRandom random) {
    List<Obstacle> obstacles = new ArrayList<>();

    // Left tower. A terrace step is one jump, but this side rises 150 px at a time, so it keeps the
    // crates that break those leaps into reachable pieces.
    obstacles.add(box(ObstacleTypeEnum.METAL_BOX, 220, 500, 100));
    obstacles.add(box(ObstacleTypeEnum.WOODEN_BOX, 200, 425, BOX_SIZE));
    addRow(obstacles, ObstacleTypeEnum.WALL, 50, 350, 8, WALL_SIZE);
    obstacles.add(box(ObstacleTypeEnum.WOODEN_BOX, 100, 275, BOX_SIZE));
    // Offset from the crate below rather than squarely on top of it: a crate the same width as its
    // support has nowhere alongside to jump from, so it can be seen but never stood on.
    obstacles.add(box(ObstacleTypeEnum.WOODEN_BOX, 170, 200, BOX_SIZE));

    // Centre. The crates at 350 sit 150 px over the terrace, so a landing breaks that into two.
    addRow(obstacles, ObstacleTypeEnum.WALL, 400, 500, 24, WALL_SIZE);
    obstacles.add(box(ObstacleTypeEnum.WOODEN_BOX, 450, 430, BOX_SIZE));
    obstacles.add(box(ObstacleTypeEnum.WOODEN_BOX, 400, 350, BOX_SIZE));
    obstacles.add(box(ObstacleTypeEnum.METAL_BOX, 300, 300, 100));
    // Carries the stack at 500, which otherwise sits 200 px over the terrace.
    obstacles.add(box(ObstacleTypeEnum.WOODEN_BOX, 500, 350, BOX_SIZE));
    obstacles.add(box(ObstacleTypeEnum.WOODEN_BOX, 500, 300, BOX_SIZE));
    obstacles.add(box(ObstacleTypeEnum.WOODEN_BOX, 550, 300, BOX_SIZE));
    obstacles.add(box(ObstacleTypeEnum.WOODEN_BOX, 550, 250, BOX_SIZE));
    obstacles.add(box(ObstacleTypeEnum.WOODEN_BOX, 550, 200, BOX_SIZE));

    // Right staircase. One terrace per jump, as it was drawn.
    obstacles.add(box(ObstacleTypeEnum.WOODEN_BOX, 750, 450, BOX_SIZE));
    addRow(obstacles, ObstacleTypeEnum.WALL, 700, 400, 12, WALL_SIZE);
    addRow(obstacles, ObstacleTypeEnum.WALL, 850, 300, 12, WALL_SIZE);
    addRow(obstacles, ObstacleTypeEnum.WALL, 1000, 200, 12, WALL_SIZE);

    // Right-hand approach from the floor.
    addRow(obstacles, ObstacleTypeEnum.WOODEN_BOX, 1050, 550, 5, BOX_SIZE);
    obstacles.add(box(ObstacleTypeEnum.WOODEN_BOX, 1100, 500, BOX_SIZE));
    obstacles.add(box(ObstacleTypeEnum.WOODEN_BOX, 1150, 500, BOX_SIZE));
    obstacles.add(box(ObstacleTypeEnum.WOODEN_BOX, 1200, 500, BOX_SIZE));
    obstacles.add(box(ObstacleTypeEnum.WOODEN_BOX, 1150, 450, BOX_SIZE));

    obstacles.add(new Obstacle(ObstacleTypeEnum.TROLL_FACE,
      new GameRectangle(random.getRandomNumber(50, Math.max(51, area.getWidth() - 200)), 130, 50, 50)));

    return obstacles;
  }

  private static Obstacle box(ObstacleTypeEnum type, double x, double y, int size) {
    return new Obstacle(type, new GameRectangle(x, y, size, size));
  }

  private static void addRow(List<Obstacle> obstacles, ObstacleTypeEnum type, int x, int y, int count, int size) {
    List<GameRectangle> walls = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      walls.add(new GameRectangle(x + (i * (double) size), y, size, size));
    }
    // One obstacle holding the whole row rather than N obstacles: fewer objects to serialise every
    // tick, and the client draws the same sprites either way.
    obstacles.add(new Obstacle(type, walls));
  }
}
