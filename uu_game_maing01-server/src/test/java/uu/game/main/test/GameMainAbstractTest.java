package uu.game.main.test;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import uu.app.authorization.AuthorizationResult;
import uu.app.core.core.UuProfiles;
import uu.app.workspace.api.UuAppDataStore;
import uu.app.workspace.api.UuAppProfiles;
import uu.app.workspace.api.UuAppWorkspace;
import uu.app.workspace.api.UuSubAppInstance;
import uu.app.workspace.api.dto.common.AdviceNoteDto;
import uu.app.workspace.api.dto.uusubappinstance.SysUuSubAppInstanceInitDtoIn;
import uu.app.workspace.api.dto.workspace.SysUuAppWorkspaceCreateDtoIn;
import uu.game.main.SubAppRunner;
import uu.game.main.abl.GameMainAbl;

@RunWith(SpringRunner.class)
@ActiveProfiles(UuProfiles.TEST)
@SpringBootTest(classes = {SubAppRunner.class}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class GameMainAbstractTest {

  protected static final String AWID = "22222222222222222222222222222222";
  protected static final String GG_ALL_URI = "urn:uu:GGALL";
  private static final String UU_IDENTITY = "1-1-1";
  private static final String AWID_LICENSE = "S";
  private static final String ADVICE_NOTE_MESSAGE = "message";
  private static final String ADVICE_NOTE_SEVERITY = "info";
  private static final String ASID_LICENSE_OWNER = "AsidLicenseOwner";
  private static final String ASID_AUTHORITIES = "AsidAuthorities";
  private static final String AUTHORITIES = "Authorities";

  @LocalServerPort
  protected int randomServerPort;

  @Inject
  UuAppWorkspace uuAppWorkspace;

  @Inject
  UuSubAppInstance uuSubAppInstance;

  @MockBean
  private AuthorizationResult authorizationResult;

  @MockBean
  protected UuAppProfiles uuAppProfiles;

  @Inject
  protected GameMainAbl gameMainAbl;

  @Inject
  UuAppDataStore dataStore;

  @Value("${asid}")
  protected String asid;

  @Before
  @After
  public void cleanUp() {
    dataStore.drop();
  }

  @Before
  public void prepare() {
    Mockito.when(uuAppProfiles.getPrivilegedProfiles(asid)).thenReturn(Stream.of(ASID_LICENSE_OWNER, ASID_AUTHORITIES).collect(Collectors.toSet()));
    Mockito.when(uuAppProfiles.getNonPrivilegedProfiles()).thenReturn(Stream.of(AUTHORITIES).collect(Collectors.toSet()));
    Mockito.when(authorizationResult.getAuthorizedProfiles()).thenReturn(Stream.of(ASID_LICENSE_OWNER).collect(Collectors.toSet()));
  }

  protected void initUuSubAppInstance() {
    SysUuSubAppInstanceInitDtoIn dtoIn = new SysUuSubAppInstanceInitDtoIn();
    dtoIn.setUuAppProfileAsidAuthorities(GG_ALL_URI);
    AdviceNoteDto adviceNote = new AdviceNoteDto();
    adviceNote.setSeverity(ADVICE_NOTE_SEVERITY);
    adviceNote.setMessage(ADVICE_NOTE_MESSAGE);
    adviceNote.setEstimatedEndTime(ZonedDateTime.now());
    adviceNote.setUuIdentity(UU_IDENTITY);
    dtoIn.setAdviceNote(adviceNote);
    uuSubAppInstance.init(dtoIn);
  }

  protected void createUuAppWorkspace(String awid) {
    SysUuAppWorkspaceCreateDtoIn dtoIn = new SysUuAppWorkspaceCreateDtoIn();
    dtoIn.setAwid(awid);
    dtoIn.setAwidLicense(AWID_LICENSE);
    dtoIn.setAwidLicenseOwnerList(Collections.singletonList(UU_IDENTITY));
    AdviceNoteDto adviceNote = new AdviceNoteDto();
    adviceNote.setSeverity(ADVICE_NOTE_SEVERITY);
    adviceNote.setMessage(ADVICE_NOTE_MESSAGE);
    adviceNote.setEstimatedEndTime(ZonedDateTime.now());
    adviceNote.setUuIdentity(UU_IDENTITY);
    dtoIn.setAdviceNote(adviceNote);
    uuAppWorkspace.create(dtoIn);
  }
}
