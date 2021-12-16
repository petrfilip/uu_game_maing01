package uu.game.main;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import uu.app.auditlog.AuditLogLibraryConfiguration;
import uu.app.datastore.annotations.DataStoreConfiguration;
import uu.app.datastore.mongodb.AbstractPersistenceMongoDbContextConfiguration;
import uu.app.datastore.mongodb.DatastoreMongoDbContextConfiguration;
import uu.app.objectstore.annotations.ObjectStore;
import uu.app.telemetry.TelemetryLibraryConfiguration;
import uu.app.workspace.dao.WorkspaceStorageConfiguration;

/**
 * Spring configuration of the application persistence.
 */
@DataStoreConfiguration
@Import({DatastoreMongoDbContextConfiguration.class})
public class SubAppPersistenceConfiguration extends AbstractPersistenceMongoDbContextConfiguration {

  @Value("${uuSubAppDataStore.primary}")
  private String objectStorePrimaryUri;

  @Bean({"primaryObjectStoreFactory"})
  public MongoDbFactory primaryOsMongoFactory() {
    return getMongoDbFactory(objectStorePrimaryUri);
  }

  @ObjectStore(name = {"primary", WorkspaceStorageConfiguration.WORKSPACE_OBJECT_STORE,
    AuditLogLibraryConfiguration.AUDIT_OBJECT_STORE,
    TelemetryLibraryConfiguration.SYS_TELEMETRY_OBJECT_STORE}, primary = true)
  public MongoTemplate primaryMongo(@Qualifier("primaryObjectStoreFactory") MongoDbFactory mongoDbFactory) {
    return getMongoTemplate(mongoDbFactory);
  }

}
