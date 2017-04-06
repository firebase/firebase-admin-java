package com.google.firebase.internal;

/**
 * This class mirrors the GetAccessTokenResult in GITKit.
 */
public class GetTokenResult {

  private String token;

  /**
   * @param token represents the {@link String} access token.
   * @hide
   */
  public GetTokenResult(String token) {
    this.token = token;
  }

  @Nullable
  public String getToken() {
    return token;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(token);
  }

  @Override
  public boolean equals(Object obj) {
    return obj != null
        && obj instanceof GetTokenResult
        && Objects.equal(token, ((GetTokenResult) obj).token);
  }
}
