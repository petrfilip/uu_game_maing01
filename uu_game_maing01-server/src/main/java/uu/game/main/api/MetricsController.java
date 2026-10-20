package uu.game.main.api;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

import javax.inject.Inject;
import uu.app.server.CommandContext;
import uu.app.server.annotation.Command;
import uu.app.server.annotation.CommandController;
import uu.game.main.api.dto.MetricsGetDtoOut;
import uu.game.main.metrics.MetricsAbl;

/**
 * Reads the server's tick and broadcast health out of a running process.
 *
 * <h2>Why this uuCmd is not {@code Public}</h2>
 * It answers, to whoever asks, how many matches are in progress, how many people are connected and
 * how close the simulation is to falling behind. That is a load map: it tells an attacker when the
 * server is already under strain, how much traffic it takes to get there, and - through
 * {@code droppedFrames} and {@code overruns} - whether an attempt to overload it is working. Room
 * and subscriber counts are also plain occupancy data about the people using the app, which nobody
 * outside the workspace has any business reading.
 *
 * <p>So it carries the same profile list as every other non-public uuCmd in this app -
 * {@code Executives}, {@code Authorities}, {@code AwidLicenseOwner} - rather than a new, looser one.
 * This app's {@code profiles.json} has already been found once to be more open than it should be,
 * and an endpoint added "just for monitoring" is exactly how that happens again. It is allowed in
 * the {@code readOnly} sysState as well as {@code active} because it only reads, and because a
 * workspace being put into {@code readOnly} is precisely when someone wants to know how the server
 * is doing.
 *
 * <p>If a monitoring system needs this without a human identity, that is an authorization decision
 * for the app's owner - a dedicated profile - and not a reason to widen this one.
 */
@CommandController
public final class MetricsController {

  @Inject
  private MetricsAbl metricsAbl;

  /**
   * @return one reading of every counter; takes no dtoIn because the numbers are process wide and
   *     there is nothing for a caller to select
   */
  @Command(path = "metrics/get", method = GET)
  public MetricsGetDtoOut get(CommandContext<Void> ctx) {
    return metricsAbl.get();
  }
}
