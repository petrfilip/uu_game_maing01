package uu.game.main;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import uu.app.auditlog.AuditLogLibraryConfiguration;
import uu.app.subapp.AbstractSubAppConfiguration;
import uu.app.subapp.OidcAuthenticationContextConfiguration;
import uu.app.subapp.WorkspaceContextConfiguration;
import uu.app.telemetry.TelemetryLibraryContextConfiguration;

/**
 * Spring configuration of the application.
 */
@Configuration
@Import({WorkspaceContextConfiguration.class,
  OidcAuthenticationContextConfiguration.class,
  TelemetryLibraryContextConfiguration.class,
  AuditLogLibraryConfiguration.class})
public class SubAppConfiguration extends AbstractSubAppConfiguration {



}
