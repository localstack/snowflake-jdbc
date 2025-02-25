/*
 * Copyright (c) 2024-2025 Snowflake Computing Inc. All rights reserved.
 */

package net.snowflake.client.core;

import java.util.HashMap;
import net.snowflake.client.category.TestTags;
import net.snowflake.client.core.auth.AuthenticatorType;
import net.snowflake.client.jdbc.BaseWiremockTest;
import net.snowflake.client.jdbc.SnowflakeSQLException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(TestTags.CORE)
public class ProgrammaticAccessTokenAuthFlowLatestIT extends BaseWiremockTest {

  private static final String SCENARIOS_BASE_DIR = MAPPINGS_BASE_DIR + "/pat";
  private static final String SUCCESSFUL_FLOW_SCENARIO_MAPPINGS =
      SCENARIOS_BASE_DIR + "/successful_flow.json";
  private static final String INVALID_TOKEN_SCENARIO_MAPPINGS =
      SCENARIOS_BASE_DIR + "/invalid_pat_token.json";

  @Test
  public void successfulFlowScenarioPatAsToken() throws SFException, SnowflakeSQLException {
    importMappingFromResources(SUCCESSFUL_FLOW_SCENARIO_MAPPINGS);
    SFLoginInput loginInputWithPatAsToken = createLoginInputStub("MOCK_TOKEN", null);
    SFLoginOutput loginOutput =
        SessionUtil.newSession(loginInputWithPatAsToken, new HashMap<>(), "INFO");
    assertSuccessfulLoginOutput(loginOutput);
  }

  @Test
  public void successfulFlowScenarioPatAsPassword() throws SFException, SnowflakeSQLException {
    importMappingFromResources(SUCCESSFUL_FLOW_SCENARIO_MAPPINGS);
    SFLoginInput loginInputWithPatAsPassword = createLoginInputStub(null, "MOCK_TOKEN");
    SFLoginOutput loginOutput =
        SessionUtil.newSession(loginInputWithPatAsPassword, new HashMap<>(), "INFO");
    assertSuccessfulLoginOutput(loginOutput);
  }

  @Test
  public void invalidTokenScenario() {
    importMappingFromResources(INVALID_TOKEN_SCENARIO_MAPPINGS);
    SnowflakeSQLException e =
        Assertions.assertThrows(
            SnowflakeSQLException.class,
            () ->
                SessionUtil.newSession(
                    createLoginInputStub("MOCK_TOKEN", null), new HashMap<>(), "INFO"));
    Assertions.assertEquals("Programmatic access token is invalid.", e.getMessage());
  }

  private void assertSuccessfulLoginOutput(SFLoginOutput loginOutput) {
    Assertions.assertNotNull(loginOutput);
    Assertions.assertEquals("session token", loginOutput.getSessionToken());
    Assertions.assertEquals("master token", loginOutput.getMasterToken());
    Assertions.assertEquals(14400, loginOutput.getMasterTokenValidityInSeconds());
    Assertions.assertEquals("8.48.0", loginOutput.getDatabaseVersion());
    Assertions.assertEquals("TEST_DHEYMAN", loginOutput.getSessionDatabase());
    Assertions.assertEquals("TEST_JDBC", loginOutput.getSessionSchema());
    Assertions.assertEquals("ANALYST", loginOutput.getSessionRole());
    Assertions.assertEquals("TEST_XSMALL", loginOutput.getSessionWarehouse());
    Assertions.assertEquals("1172562260498", loginOutput.getSessionId());
    Assertions.assertEquals(1, loginOutput.getCommonParams().size());
    Assertions.assertEquals(4, loginOutput.getCommonParams().get("CLIENT_PREFETCH_THREADS"));
  }

  private SFLoginInput createLoginInputStub(String token, String password) {
    SFLoginInput input = new SFLoginInput();
    input.setAuthenticator(AuthenticatorType.PROGRAMMATIC_ACCESS_TOKEN.name());
    input.setServerUrl(String.format("http://%s:%d/", WIREMOCK_HOST, wiremockHttpPort));
    input.setUserName("MOCK_USERNAME");
    input.setAccountName("MOCK_ACCOUNT_NAME");
    input.setAppId("MOCK_APP_ID");
    input.setAppVersion("MOCK_APP_VERSION");
    input.setToken(token);
    input.setPassword(password);
    input.setOCSPMode(OCSPMode.FAIL_OPEN);
    input.setHttpClientSettingsKey(new HttpClientSettingsKey(OCSPMode.FAIL_OPEN));
    input.setLoginTimeout(1000);
    input.setSessionParameters(new HashMap<>());
    return input;
  }
}
