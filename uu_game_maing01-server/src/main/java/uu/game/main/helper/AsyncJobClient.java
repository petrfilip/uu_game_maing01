package uu.game.main.helper;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import javax.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uu.app.authentication.CallTokenOptions;
import uu.app.authentication.Session;
import uu.app.client.AppClient;
import uu.app.client.AppClientFactory;
import uu.app.server.CommandContext;
import uu.app.uri.DefaultUriBuilder;
import uu.app.uri.Uri;
import uu.game.main.api.dto.CalculateDtoIn;
import uu.game.main.api.dto.job.ActiveJob;
import uu.game.main.api.dto.job.AuthenticationCredentials;
import uu.game.main.api.dto.job.CallParams;
import uu.game.main.api.dto.job.CreateJobDtoIn;
import uu.game.main.api.dto.job.CreateJobDtoOut;

@Component
public class AsyncJobClient {

  private static final Logger logger = LogManager.getLogger(AsyncJobClient.class);
  @Inject
  private AppClientFactory factory;

  @Value("${asyncJobUri:#{null}}")
  private String asyncJobUri;

  public ActiveJob startAsyncJob(CommandContext<CalculateDtoIn> ctx) {

    Uri thisUri = ctx.getUri();
    String primaryEventUri = thisUri.getBuilder()
      .setGateway("https://uuapp-dev-async.plus4u.net")
      .setSpp("async")
      .setUseCase("calculateAsync/create")
      .toUri().toString();

    // primary call
    CallParams callParams = new CallParams();
    callParams.setUri(primaryEventUri);

    // callback
    CallParams callBackParams = new CallParams();
    callBackParams.setHttpMethod("POST");
    callBackParams.setUri(getCallbackUri(thisUri));

    ZonedDateTime notBefore = ZonedDateTime.now().plus(2, ChronoUnit.MINUTES);
    ZonedDateTime notAfter = ZonedDateTime.now().plus(5, ChronoUnit.MINUTES);
    CreateJobDtoOut job = this.createAsyncJob(ctx.getAuthenticationSession(), callParams, callBackParams, notBefore, notAfter);

    return job.getJob();
  }

  private String getCallbackUri(Uri thisUri) {
    return thisUri.getBuilder()
      .setGateway("https://uuapp.plus4u.net")
      .setUseCase("calculate/createAsyncJob")
      .toUri().toString();
  }


  public CreateJobDtoOut createAsyncJob(Session authenticationSession, CallParams callParams, CallParams callbackParams,
    ZonedDateTime notBeforeTime, ZonedDateTime notAfterTime) {

    CreateJobDtoIn createJobDtoIn = new CreateJobDtoIn();
    createJobDtoIn.setNotBeforeTime(notBeforeTime);
    createJobDtoIn.setNotAfterTime(notAfterTime);
    createJobDtoIn.setCall(callParams);
    createJobDtoIn.setCallback(callbackParams);

    String scope = String.join(" ", "openid", callParams.getUri(), callbackParams.getUri());
    AuthenticationCredentials authenticationCredentials = new AuthenticationCredentials();
    CallTokenOptions callTokenOptions = new CallTokenOptions();
    callTokenOptions.setExcludeAuthenticationType(true);
    String callToken = authenticationSession.getCallToken(scope, callTokenOptions);
    logger.debug("Call token is {}", callToken);
    authenticationCredentials.setCallToken(callToken);

    createJobDtoIn.setAuthentication(authenticationCredentials);

    AppClient appClient = factory.newAppClient(authenticationSession);

    Uri createJobUri = DefaultUriBuilder
      .parse(asyncJobUri)
      .setUseCase("createJob")
      .toUri();
    return appClient.post(createJobUri, createJobDtoIn, CreateJobDtoOut.class);
  }
}
