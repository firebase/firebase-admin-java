/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.remoteconfig;

import static com.google.common.base.Preconditions.checkArgument;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

/**
 * A class representing options for Remote Config list versions operation.
 */
public final class ListVersionsOptions {

  private final Integer pageSize;
  private final String pageToken;
  private final String endVersionNumber;
  private final String startTime;
  private final String endTime;

  private ListVersionsOptions(Builder builder) {
    if (builder.pageSize != null) {
      checkArgument(builder.pageSize > 0 && builder.pageSize < 301,
              "pageSize must be a number between 1 and 300 (inclusive).");
    }
    if (builder.endVersionNumber != null) {
      checkArgument(RemoteConfigUtil.isValidVersionNumber(builder.endVersionNumber)
                      && (Integer.parseInt(builder.endVersionNumber) > 0),
              "endVersionNumber must be a non-empty string in int64 format and must be"
                      + " greater than 0.");
    }
    this.pageSize = builder.pageSize;
    this.pageToken = builder.pageToken;
    this.endVersionNumber = builder.endVersionNumber;
    this.startTime = builder.startTime;
    this.endTime = builder.endTime;
  }

  Map<String, Object> wrapForTransport() {
    Map<String, Object> optionsMap = new HashMap<>();
    if (this.pageSize != null) {
      optionsMap.put("pageSize", this.pageSize);
    }
    if (this.pageToken != null) {
      optionsMap.put("pageToken", this.pageToken);
    }
    if (this.endVersionNumber != null) {
      optionsMap.put("endVersionNumber", this.endVersionNumber);
    }
    if (this.startTime != null) {
      optionsMap.put("startTime", this.startTime);
    }
    if (this.endTime != null) {
      optionsMap.put("endTime", this.endTime);
    }
    return optionsMap;
  }

  String getPageToken() {
    return pageToken;
  }

  /**
   * Creates a new {@link ListVersionsOptions.Builder}.
   *
   * @return A {@link ListVersionsOptions.Builder} instance.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Creates a new {@code Builder} from the options object.
   *
   * <p>The new builder is not backed by this object's values; that is, changes made to the new
   * builder don't change the values of the origin object.
   */
  public Builder toBuilder() {
    return new Builder(this);
  }

  public static class Builder {
    private Integer pageSize;
    private String pageToken;
    private String endVersionNumber;
    private String startTime;
    private String endTime;

    private Builder() {}

    private Builder(ListVersionsOptions options) {
      this.pageSize = options.pageSize;
      this.pageToken = options.pageToken;
      this.endVersionNumber = options.endVersionNumber;
      this.startTime = options.startTime;
      this.endTime = options.endTime;
    }

    /**
     * Sets the page size.
     *
     * @param pageSize The maximum number of items to return per page.
     * @return This builder.
     */
    public Builder setPageSize(int pageSize) {
      this.pageSize = pageSize;
      return this;
    }

    /**
     * Sets the page token.
     *
     * @param pageToken The {@code nextPageToken} value returned from a previous List request,
     *                  if any.
     * @return This builder.
     */
    public Builder setPageToken(String pageToken) {
      this.pageToken = pageToken;
      return this;
    }

    /**
     * Sets the newest version number to include in the results.
     *
     * @param endVersionNumber Specify the newest version number to include in the results.
     *                         If specified, must be greater than zero. Defaults to the newest
     *                         version.
     * @return This builder.
     */
    public Builder setEndVersionNumber(String endVersionNumber) {
      this.endVersionNumber = endVersionNumber;
      return this;
    }

    /**
     * Sets the newest version number to include in the results.
     *
     * @param endVersionNumber Specify the newest version number to include in the results.
     *                         If specified, must be greater than zero. Defaults to the newest
     *                         version.
     * @return This builder.
     */
    public Builder setEndVersionNumber(long endVersionNumber) {
      this.endVersionNumber = String.valueOf(endVersionNumber);;
      return this;
    }

    /**
     * Sets the earliest update time to include in the results.
     *
     * @param startTimeMillis Specify the earliest update time to include in the results.
     *                        Any entries updated before this time are omitted.
     * @return This builder.
     */
    public Builder setStartTimeMillis(long startTimeMillis) {
      this.startTime = RemoteConfigUtil.convertToUtcZuluFormat(startTimeMillis);
      return this;
    }

    /**
     * Sets the latest update time to include in the results.
     *
     * @param endTimeMillis Specify the latest update time to include in the results.
     *                      Any entries updated on or after this time are omitted.
     * @return This builder.
     */
    public Builder setEndTimeMillis(long endTimeMillis) {
      this.endTime = RemoteConfigUtil.convertToUtcZuluFormat(endTimeMillis);
      return this;
    }

    /**
     * Builds a new {@link ListVersionsOptions} instance from the fields set on this builder.
     *
     * @return A non-null {@link ListVersionsOptions}.
     */
    public ListVersionsOptions build() {
      return new ListVersionsOptions(this);
    }
  }
}
