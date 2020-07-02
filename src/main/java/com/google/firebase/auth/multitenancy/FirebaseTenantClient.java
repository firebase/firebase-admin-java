package com.google.firebase.auth.multitenancy;

import com.google.firebase.auth.internal.ListTenantsResponse;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

class FirebaseTenantClient {

  static final int MAX_LIST_TENANTS_RESULTS = 100;

  Tenant getTenant(String tenantId) {
    throw new NotImplementedException();
  }

  ListTenantsResponse listTenants(int maxResults, String pageToken) {
    throw new NotImplementedException();
  }

  Tenant createTenant(Tenant.CreateRequest request) {
    throw new NotImplementedException();
  }

  Tenant updateTenant(Tenant.UpdateRequest request) {
    throw new NotImplementedException();
  }

  void deleteTenant(String tenantId) {
    throw new NotImplementedException();
  }

}
