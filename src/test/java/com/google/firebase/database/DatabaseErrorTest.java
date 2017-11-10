package com.google.firebase.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class DatabaseErrorTest {

  @Test
  public void testFromCode() {
    DatabaseError error = DatabaseError.fromCode(DatabaseError.DATA_STALE);
    assertEquals(DatabaseError.DATA_STALE, error.getCode());
    assertEquals("The transaction needs to be run again with current data", error.getMessage());
    assertEquals("", error.getDetails());
    assertEquals("DatabaseError: " + error.getMessage(), error.toString());

    try {
      DatabaseError.fromCode(19191);
      fail("No error thrown for unknown error code");
    } catch (IllegalArgumentException expected) {
      // expected
    }
  }

  @Test
  public void testFromException() {
    DatabaseError error = DatabaseError.fromException(new RuntimeException("_test_"));
    assertEquals(DatabaseError.USER_CODE_EXCEPTION, error.getCode());
    assertTrue(error.getMessage().contains("_test_"));
    assertEquals("", error.getDetails());
  }

}
