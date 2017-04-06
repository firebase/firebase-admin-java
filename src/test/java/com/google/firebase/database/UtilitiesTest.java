package com.google.firebase.database;

import com.google.firebase.database.core.DatabaseConfig;
import com.google.firebase.database.utilities.Utilities;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UtilitiesTest {

  @Test
  public void tryParseInt() {
    Assert.assertEquals(
        Utilities.tryParseInt("" + Integer.MAX_VALUE), Integer.valueOf(Integer.MAX_VALUE));
    Assert.assertEquals(
        Utilities.tryParseInt("" + Integer.MIN_VALUE), Integer.valueOf(Integer.MIN_VALUE));
    Assert.assertEquals(Utilities.tryParseInt("0"), Integer.valueOf(0));
    Assert.assertEquals(Utilities.tryParseInt("-0"), Integer.valueOf(0));
    Assert.assertEquals(Utilities.tryParseInt("-1"), Integer.valueOf(-1));
    Assert.assertEquals(Utilities.tryParseInt("1"), Integer.valueOf(1));
    Assert.assertNull(Utilities.tryParseInt("a"));
    Assert.assertNull(Utilities.tryParseInt("-0a"));
    Assert.assertNull(Utilities.tryParseInt("-"));
    Assert.assertNull(Utilities.tryParseInt("" + (Integer.MAX_VALUE + 1L)));
    Assert.assertNull(Utilities.tryParseInt("" + (Integer.MIN_VALUE - 1L)));
  }

  @Test
  public void defaultCacheSizeIs10MB() {
    assertEquals(10 * 1024 * 1024, new DatabaseConfig().getPersistenceCacheSizeBytes());
  }

  @Test
  public void settingValidCacheSizeSucceeds() {
    new DatabaseConfig().setPersistenceCacheSizeBytes(5 * 1024 * 1024); // works fine.
  }

  @Test(expected = DatabaseException.class)
  public void settingCacheSizeTooLowFails() {
    new DatabaseConfig().setPersistenceCacheSizeBytes(1024 * 1024 - 1);
  }

  @Test(expected = DatabaseException.class)
  public void settingCacheSizeTooHighFails() {
    new DatabaseConfig().setPersistenceCacheSizeBytes(100 * 1024 * 1024 + 1);
  }
}
