package com.google.firebase.remoteconfig.internal;

/**
 * An internal class for publish template options.
 */
public final class PublishOptions {

  private boolean validateOnly;
  private boolean forcePublish;

  public PublishOptions() {
    validateOnly = false;
    forcePublish = false;
  }

  public PublishOptions setForcePublish(boolean forcePublish) {
    this.forcePublish = forcePublish;
    return this;
  }

  public PublishOptions setValidateOnly(boolean validateOnly) {
    this.validateOnly = validateOnly;
    return this;
  }

  public boolean isForcePublish() {
    return forcePublish;
  }

  public boolean isValidateOnly() {
    return validateOnly;
  }
}
