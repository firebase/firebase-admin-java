package com.google.firebase.internal;

/**
 * This class mirrors the GetAccessTokenResult in GITKit.
 */
public class GetTokenResult {

  private String mToken;

  /**
   * @param token represents the {@link String} access token.
   * @hide
   */
  public GetTokenResult(String token) {
    mToken = token;
  }

  @Nullable
  public String getToken() {
    return mToken;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(mToken);
  }

  @Override
  public boolean equals(Object obj) {
    return obj != null
        && obj instanceof GetTokenResult
        && Objects.equal(mToken, ((GetTokenResult) obj).mToken);
  }
}
