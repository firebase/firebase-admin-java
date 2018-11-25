package com.google.firebase.auth;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.api.client.util.Key;
import com.google.common.base.Strings;


public final class AndroidActionCodeSettings {

  @Key("packageName")
  private final String packageName;

  @Key("installApp")
  private final boolean installApp;

  @Key("minimumVersion")
  private final String minimumVersion;

  private AndroidActionCodeSettings(Builder builder) {
    checkArgument(!Strings.isNullOrEmpty(builder.packageName),
        "Package name must not be null or empty");
    this.packageName = builder.packageName;
    this.installApp = builder.installApp;
    this.minimumVersion = builder.minimumVersion;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private String packageName;
    private boolean installApp;
    private String minimumVersion;

    private Builder() { }

    public Builder setPackageName(String packageName) {
      this.packageName = packageName;
      return this;
    }

    public Builder setInstallApp(boolean installApp) {
      this.installApp = installApp;
      return this;
    }

    public Builder setMinimumVersion(String minimumVersion) {
      this.minimumVersion = minimumVersion;
      return this;
    }

    public AndroidActionCodeSettings build() {
      return new AndroidActionCodeSettings(this);
    }
  }
}
