package com.google.firebase.remoteconfig;

/**
 * An internal class for publish template options.
 */
final class PublishOptions {

  private boolean validateOnly;
  private boolean forcePublish;

  PublishOptions() {
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
