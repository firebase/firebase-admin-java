package com.google.firebase.remoteconfig;

import com.google.common.collect.ImmutableMap;
import com.google.firebase.remoteconfig.internal.ServerTemplateDataResponse;

public final class ServerTemplateData {

  private final String etag;
  private final ImmutableMap<String, Parameter> parameters;
  private final ImmutableMap<String, ServerCondition> conditions;
  private final Version version;

  /**
   * Constructor for the Template class.
   *
   * @param etag       The ETag of the template.
   * @param parameters The parameters of the template.
   * @param conditions The conditions of the template.
   * @param version    The version of the template.
   */
  public ServerTemplateData(String etag, ImmutableMap<String, Parameter> parameters,
                  ImmutableMap<String, ServerCondition> conditions, Version version) {
    this.etag = etag;
    this.parameters = parameters;
    this.conditions = conditions;
    this.version = version;
  }

  /**
   * Returns the ETag of the template.
   *
   * @return The ETag of the template.
   */
  public String getEtag() {
    return etag;
  }

  /**
   * Returns the parameters of the template.
   *
   * @return The parameters of the template.
   */
  public ImmutableMap<String, Parameter> getParameters() {
    return parameters;
  }

  /**
   * Returns the conditions of the template.
   *
   * @return The conditions of the template.
   */
  public ImmutableMap<String, ServerCondition> getConditions() {
    return conditions;
  }

  /**
   * Returns the version of the template.
   *
   * @return The version of the template.
   */
  public Version getVersion() {
    return version;
  }

  /**
   * Constructor for ServerTemplateData.
   *
   * @param serverTemplateResponse The ServerTemplateDataResponse object used to initialize the fields.
   */
  public ServerTemplateData(ServerTemplateDataResponse serverTemplateResponse) {
    this.parameters = buildParametersMap(serverTemplateResponse.getParameters());
    this.conditions = buildServerConditionsMap(serverTemplateResponse.getServerConditions());
    this.version = buildVersion(serverTemplateResponse.getVersion());
    this.etag = serverTemplateResponse.getEtag();
  }

  // Helper methods to convert response objects to domain objects

  private ImmutableMap<String, Parameter> buildParametersMap(ImmutableMap<String, ServerTemplateDataResponse.ParameterResponse> parameterResponses) {
    ImmutableMap.Builder<String, Parameter> builder = ImmutableMap.builder();
    if (parameterResponses != null) {
      for (String key : parameterResponses.keySet()) {
        builder.put(key, new Parameter(parameterResponses.get(key)));
      }
    }
    return builder.build();
  }

  private ImmutableMap<String, ServerCondition> buildServerConditionsMap(ImmutableMap<String, ServerTemplateDataResponse.ServerConditionResponse> serverConditionResponses) {
    ImmutableMap.Builder<String, ServerCondition> builder = ImmutableMap.builder();
    if (serverConditionResponses != null) {
      for (String key : serverConditionResponses.keySet()) {
        builder.put(key, new ServerCondition(serverConditionResponses.get(key)));
      }
    }
    return builder.build();
  }

  private Version buildVersion(ServerTemplateDataResponse.VersionResponse versionResponse) {
    if (versionResponse != null) {
      return new Version(versionResponse);
    }
    return null;
  }
}