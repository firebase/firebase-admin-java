package com.google.firebase.auth;

public interface ListUsersCallback {

  boolean onResult(ExportedUserRecord userRecord);

  void onComplete();

  void onError(Exception e);

}
