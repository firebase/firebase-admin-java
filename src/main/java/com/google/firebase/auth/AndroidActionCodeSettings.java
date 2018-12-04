package com.google.firebase.auth;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.firebase.internal.NonNull;
import java.util.Map;

/**
 * Defines the Android related settings corresponding to the action code if it is to be handled in
 * an Android app.
 */
public final class AndroidActionCodeSettings {

  private final String packageName;
  private final boolean installApp;
  private final String minimumVersion;

  private AndroidActionCodeSettings(Builder builder) {
    checkArgument(!Strings.isNullOrEmpty(builder.packageName),
        "Package name must not be null or empty");
    this.packageName = builder.packageName;
    this.installApp = builder.installApp;
    this.minimumVersion = builder.minimumVersion;
  }

  Map<String, Object> getProperties() {
    return ImmutableMap.<String, Object>of(
        "packageName", packageName,
        "installApp", installApp,
        "minimumVersion", minimumVersion
    );
  }

  /**
   * Creates a new {@link AndroidActionCodeSettings.Builder}.
   *
   * @return A {@link AndroidActionCodeSettings.Builder} instance.
   */
  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private String packageName;
    private boolean installApp;
    private String minimumVersion;

    private Builder() { }

    /**
     * Sets the required Android package name of the app where the link should be handled if the
     * Android app is installed.
     *
     * @param packageName Package name string. Must be specified, and must not be null or empty.
     * @return This builder.
     */
    public Builder setPackageName(@NonNull String packageName) {
      this.packageName = packageName;
      return this;
    }

    /**
     * Specifies whether to install the Android app if the device supports it and the app is not
     * already installed.
     *
     * @param installApp true to install the app, and false otherwise.
     * @return This builder.
     */
    public Builder setInstallApp(boolean installApp) {
      this.installApp = installApp;
      return this;
    }

    /**
     * Sets the minimum version for Android app. If the installed app is an older version, the user
     * is taken to the Play Store to upgrade the app.
     *
     * @param minimumVersion Minimum version string.
     * @return This builder.
     */
    public Builder setMinimumVersion(String minimumVersion) {
      this.minimumVersion = minimumVersion;
      return this;
    }

    /**
     * Builds a new {@link AndroidActionCodeSettings}.
     *
     * @return A non-null {@link AndroidActionCodeSettings}.
     */
    public AndroidActionCodeSettings build() {
      return new AndroidActionCodeSettings(this);
    }
  }
}
