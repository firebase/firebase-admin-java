package com.google.firebase.database.connection;

public interface ListenHashProvider {

  String getSimpleHash();

  boolean shouldIncludeCompoundHash();

  CompoundHash getCompoundHash();
}
