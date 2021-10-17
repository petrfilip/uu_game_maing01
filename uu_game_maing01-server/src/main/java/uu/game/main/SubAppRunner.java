package uu.game.main;

import static org.springframework.core.env.AbstractEnvironment.DEFAULT_PROFILES_PROPERTY_NAME;

import org.springframework.boot.SpringApplication;
import uu.app.core.core.UuProfiles;
import uu.app.subapp.AbstractSubAppRunner;
import uu.app.subapp.annotation.UuSubApp;

/**
 * Class for running the application.
 */
@UuSubApp
public class SubAppRunner extends AbstractSubAppRunner {

  /**
   * The main method starts the application and is intended only for a non-production use.
   *
   * @param args application arguments
   * @throws Exception any uncaught exception
   */
  public static void main(String[] args) throws Exception {
    // The main method is not expected to be used in production, but only for development -> set the default profile to development
    setupDevelopmentMode();
    SpringApplication.run(SubAppRunner.class, args);
  }

}
