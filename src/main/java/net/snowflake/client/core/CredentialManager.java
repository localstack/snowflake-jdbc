/*
 * Copyright (c) 2012-2019 Snowflake Computing Inc. All rights reserved.
 */

package net.snowflake.client.core;

import com.amazonaws.util.StringUtils;
import com.google.common.base.Strings;
import java.net.URI;
import net.snowflake.client.jdbc.ErrorCode;
import net.snowflake.client.log.SFLogger;
import net.snowflake.client.log.SFLoggerFactory;

public class CredentialManager {
  private static final SFLogger logger = SFLoggerFactory.getLogger(CredentialManager.class);

  private SecureStorageManager secureStorageManager;

  private CredentialManager() {
    initSecureStorageManager();
  }

  private void initSecureStorageManager() {
    try {
      if (Constants.getOS() == Constants.OS.MAC) {
        secureStorageManager = SecureStorageAppleManager.builder();
      } else if (Constants.getOS() == Constants.OS.WINDOWS) {
        secureStorageManager = SecureStorageWindowsManager.builder();
      } else if (Constants.getOS() == Constants.OS.LINUX) {
        secureStorageManager = SecureStorageLinuxManager.getInstance();
      } else {
        logger.error("Unsupported Operating System. Expected: OSX, Windows, Linux", false);
      }
    } catch (NoClassDefFoundError error) {
      logMissingJnaJarForSecureLocalStorage();
    }
  }

  /** Helper function for tests to go back to normal settings. */
  static void resetSecureStorageManager() {
    logger.debug("Resetting the secure storage manager");
    getInstance().initSecureStorageManager();
  }

  /**
   * Testing purpose. Inject a mock manager.
   *
   * @param manager SecureStorageManager
   */
  static void injectSecureStorageManager(SecureStorageManager manager) {
    logger.debug("Injecting secure storage manager");
    getInstance().secureStorageManager = manager;
  }

  private static class CredentialManagerHolder {
    private static final CredentialManager INSTANCE = new CredentialManager();
  }

  public static CredentialManager getInstance() {
    return CredentialManagerHolder.INSTANCE;
  }

  /**
   * Reuse the cached id token stored locally
   *
   * @param loginInput login input to attach id token
   */
  static void fillCachedIdToken(SFLoginInput loginInput) throws SFException {
    logger.debug(
        "Looking for cached id token for user: {}, host: {}",
        loginInput.getUserName(),
        loginInput.getHostFromServerUrl());
    getInstance()
        .fillCachedCredential(
            loginInput,
            loginInput.getHostFromServerUrl(),
            loginInput.getUserName(),
            CachedCredentialType.ID_TOKEN);
  }

  /**
   * Reuse the cached mfa token stored locally
   *
   * @param loginInput login input to attach mfa token
   */
  static void fillCachedMfaToken(SFLoginInput loginInput) throws SFException {
    logger.debug(
        "Looking for cached mfa token for user: {}, host: {}",
        loginInput.getUserName(),
        loginInput.getHostFromServerUrl());
    getInstance()
        .fillCachedCredential(
            loginInput,
            loginInput.getHostFromServerUrl(),
            loginInput.getUserName(),
            CachedCredentialType.MFA_TOKEN);
  }

  /**
   * Reuse the cached OAuth access token stored locally
   *
   * @param loginInput login input to attach access token
   */
  static void fillCachedOAuthAccessToken(SFLoginInput loginInput) throws SFException {
    String host = getHostForOAuthCacheKey(loginInput);
    logger.debug(
        "Looking for cached OAuth access token for user: {}, host: {}",
        loginInput.getUserName(),
        host);
    getInstance()
        .fillCachedCredential(
            loginInput, host, loginInput.getUserName(), CachedCredentialType.OAUTH_ACCESS_TOKEN);
  }

  /**
   * Reuse the cached OAuth refresh token stored locally
   *
   * @param loginInput login input to attach refresh token
   */
  static void fillCachedOAuthRefreshToken(SFLoginInput loginInput) throws SFException {
    String host = getHostForOAuthCacheKey(loginInput);
    logger.debug(
        "Looking for cached OAuth refresh token for user: {}, host: {}",
        loginInput.getUserName(),
        host);
    getInstance()
        .fillCachedCredential(
            loginInput, host, loginInput.getUserName(), CachedCredentialType.OAUTH_REFRESH_TOKEN);
  }

  /** Reuse the cached token stored locally */
  synchronized void fillCachedCredential(
      SFLoginInput loginInput, String host, String username, CachedCredentialType credType)
      throws SFException {
    if (StringUtils.isNullOrEmpty(username)) {
      logger.debug("Missing username; Cannot read from credential cache");
      return;
    }
    if (secureStorageManager == null) {
      logMissingJnaJarForSecureLocalStorage();
      return;
    }

    String cred;
    try {
      cred = secureStorageManager.getCredential(host, username, credType.getValue());
    } catch (NoClassDefFoundError error) {
      logMissingJnaJarForSecureLocalStorage();
      return;
    }

    if (cred == null) {
      logger.debug("Retrieved {} is null", credType);
    }

    logger.debug(
        "Setting {}{} token for user: {}, host: {}",
        cred == null ? "null " : "",
        credType.getValue(),
        username,
        host);
    switch (credType) {
      case ID_TOKEN:
        loginInput.setIdToken(cred);
        break;
      case MFA_TOKEN:
        loginInput.setMfaToken(cred);
        break;
      case OAUTH_ACCESS_TOKEN:
        loginInput.setOauthAccessToken(cred);
        break;
      case OAUTH_REFRESH_TOKEN:
        loginInput.setOauthRefreshToken(cred);
        break;
      default:
        throw new SFException(
            ErrorCode.INTERNAL_ERROR, "Unrecognized type {} for local cached credential", credType);
    }
  }

  static void writeIdToken(SFLoginInput loginInput, String idToken) throws SFException {
    logger.debug(
        "Caching id token in a secure storage for user: {}, host: {}",
        loginInput.getUserName(),
        loginInput.getHostFromServerUrl());
    getInstance()
        .writeTemporaryCredential(
            loginInput.getHostFromServerUrl(),
            loginInput.getUserName(),
            idToken,
            CachedCredentialType.ID_TOKEN);
  }

  static void writeMfaToken(SFLoginInput loginInput, String mfaToken) throws SFException {
    logger.debug(
        "Caching mfa token in a secure storage for user: {}, host: {}",
        loginInput.getUserName(),
        loginInput.getHostFromServerUrl());
    getInstance()
        .writeTemporaryCredential(
            loginInput.getHostFromServerUrl(),
            loginInput.getUserName(),
            mfaToken,
            CachedCredentialType.MFA_TOKEN);
  }

  /**
   * Store OAuth Access Token
   *
   * @param loginInput loginInput to denote to the cache
   */
  static void writeOAuthAccessToken(SFLoginInput loginInput) throws SFException {
    String host = getHostForOAuthCacheKey(loginInput);
    logger.debug(
        "Caching OAuth access token in a secure storage for user: {}, host: {}",
        loginInput.getUserName(),
        host);
    getInstance()
        .writeTemporaryCredential(
            host,
            loginInput.getUserName(),
            loginInput.getOauthAccessToken(),
            CachedCredentialType.OAUTH_ACCESS_TOKEN);
  }

  /**
   * Store OAuth Refresh Token
   *
   * @param loginInput loginInput to denote to the cache
   */
  static void writeOAuthRefreshToken(SFLoginInput loginInput) throws SFException {
    String host = getHostForOAuthCacheKey(loginInput);
    logger.debug(
        "Caching OAuth refresh token in a secure storage for user: {}, host: {}",
        loginInput.getUserName(),
        host);
    getInstance()
        .writeTemporaryCredential(
            host,
            loginInput.getUserName(),
            loginInput.getOauthRefreshToken(),
            CachedCredentialType.OAUTH_REFRESH_TOKEN);
  }

  /** Store the temporary credential */
  synchronized void writeTemporaryCredential(
      String host, String user, String cred, CachedCredentialType credType) {
    if (StringUtils.isNullOrEmpty(user)) {
      logger.debug("Missing username; Cannot write to credential cache");
      return;
    }
    if (Strings.isNullOrEmpty(cred)) {
      logger.debug("No {} is given.", credType);
      return; // no credential
    }

    if (secureStorageManager == null) {
      logMissingJnaJarForSecureLocalStorage();
      return;
    }

    try {
      secureStorageManager.setCredential(host, user, credType.getValue(), cred);
    } catch (NoClassDefFoundError error) {
      logMissingJnaJarForSecureLocalStorage();
    }
  }

  /** Delete the id token cache */
  static void deleteIdTokenCache(String host, String user) {
    logger.debug(
        "Removing cached id token from a secure storage for user: {}, host: {}", user, host);
    getInstance().deleteTemporaryCredential(host, user, CachedCredentialType.ID_TOKEN);
  }

  /** Delete the mfa token cache */
  static void deleteMfaTokenCache(String host, String user) {
    logger.debug(
        "Removing cached mfa token from a secure storage for user: {}, host: {}", user, host);
    getInstance().deleteTemporaryCredential(host, user, CachedCredentialType.MFA_TOKEN);
  }

  /** Delete the OAuth access token cache */
  static void deleteOAuthAccessTokenCache(SFLoginInput loginInput) throws SFException {
    String host = getHostForOAuthCacheKey(loginInput);
    logger.debug(
        "Removing cached OAuth access token from a secure storage for user: {}, host: {}",
        loginInput.getUserName(),
        host);
    getInstance()
        .deleteTemporaryCredential(
            host, loginInput.getUserName(), CachedCredentialType.OAUTH_ACCESS_TOKEN);
  }

  /** Delete the OAuth refresh token cache */
  static void deleteOAuthRefreshTokenCache(SFLoginInput loginInput) throws SFException {
    String host = getHostForOAuthCacheKey(loginInput);
    logger.debug(
        "Removing cached OAuth refresh token from a secure storage for user: {}, host: {}",
        loginInput.getUserName(),
        host);
    getInstance()
        .deleteTemporaryCredential(
            host, loginInput.getUserName(), CachedCredentialType.OAUTH_REFRESH_TOKEN);
  }

  /**
   * Method required for OAuth token caching, since actual token is not Snowflake account-specific,
   * but rather IdP-specific
   */
  static String getHostForOAuthCacheKey(SFLoginInput loginInput) throws SFException {
    String oauthTokenRequestUrl = loginInput.getOauthLoginInput().getTokenRequestUrl();
    if (oauthTokenRequestUrl != null) {
      URI parsedUrl = URI.create(oauthTokenRequestUrl);
      return parsedUrl.getHost();
    } else {
      return loginInput.getHostFromServerUrl();
    }
  }

  /**
   * Delete the temporary credential
   *
   * @param host host name
   * @param user user name
   * @param credType type of the credential
   */
  synchronized void deleteTemporaryCredential(
      String host, String user, CachedCredentialType credType) {
    if (secureStorageManager == null) {
      logMissingJnaJarForSecureLocalStorage();
      return;
    }
    if (StringUtils.isNullOrEmpty(user)) {
      logger.debug("Missing username; Cannot delete from credential cache");
      return;
    }

    try {
      secureStorageManager.deleteCredential(host, user, credType.getValue());
    } catch (NoClassDefFoundError error) {
      logMissingJnaJarForSecureLocalStorage();
    }
  }

  private static void logMissingJnaJarForSecureLocalStorage() {
    logger.warn(
        "JNA jar files are needed for Secure Local Storage service. Please follow the Snowflake JDBC instruction for Secure Local Storage feature. Fall back to normal process.",
        false);
  }
}
