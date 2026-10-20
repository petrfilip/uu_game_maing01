package uu.game.main.engine;

/**
 * Told how every tick of every match went.
 *
 * <p>The seam exists because the tick loop is the one place where a server that is quietly falling
 * behind shows it, and nothing was watching. A tick that takes longer than its period does not fail
 * and does not log: {@code scheduleWithFixedDelay} simply starts the next one late, and the whole
 * match runs slow motion for everyone in it.
 *
 * <p>Implementations are called on the tick thread itself, once per tick per room. They must be
 * cheap and must not throw - the engine treats an observer that does either as a bug in the
 * observer, not a reason to drop a tick.
 */
public interface TickObserver {

  /** Does nothing, so the engine never has to null check. */
  TickObserver NONE = new TickObserver() {
    @Override
    public void onTick(String gameName, long durationNanos, boolean failed) {
      // no measurement wanted
    }
  };

  /**
   * @param gameName which game was ticked
   * @param durationNanos how long the rule took to produce the next state
   * @param failed whether the rule threw, in which case the state did not advance
   */
  void onTick(String gameName, long durationNanos, boolean failed);
}
