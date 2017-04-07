package com.google.firebase.integration;

import com.google.firebase.database.DataSnapshotTestIT;
import com.google.firebase.database.DataTestIT;
import com.google.firebase.database.FirebaseDatabaseTestIT;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({
  FirebaseDatabaseTestIT.class,
  DatabaseServerAuthTestIT.class,
  DataSnapshotTestIT.class,
  DataTestIT.class
})
public class MainSuiteIT {
  
}
