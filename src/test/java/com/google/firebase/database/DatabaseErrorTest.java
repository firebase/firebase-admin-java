package com.google.firebase.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class DatabaseErrorTest {

  @Test
  public void testFromCode() {
    DatabaseError error = DatabaseError.fromCode(DatabaseErrorCode.DATA_STALE);
    assertEquals(DatabaseErrorCode.DATA_STALE, error.getCode());
    assertEquals("The transaction needs to be run again with current data", error.getMessage());
    assertEquals("", error.getDetails());
    assertEquals("DatabaseError: " + error.getMessage(), error.toString());
  }

  @Test
  public void testFromException() {
    DatabaseError error = DatabaseError.fromException(new RuntimeException("_test_"));
    assertEquals(DatabaseErrorCode.USER_CODE_EXCEPTION, error.getCode());
    assertTrue(error.getMessage().contains("_test_"));
    assertEquals("", error.getDetails());
  }

}
