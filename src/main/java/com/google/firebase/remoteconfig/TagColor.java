package com.google.firebase.remoteconfig;

public enum TagColor {
  BLUE("BLUE"),
  BROWN("BROWN"),
  CYAN("CYAN"),
  DEEP_ORANGE("DEEP_ORANGE"),
  GREEN("GREEN"),
  INDIGO("INDIGO"),
  LIME("LIME"),
  ORANGE("ORANGE"),
  PINK("PINK"),
  PURPLE("PURPLE"),
  TEAL("TEAL"),
  UNSPECIFIED("CONDITION_DISPLAY_COLOR_UNSPECIFIED");

  private final String color;

  TagColor(String color) {
    this.color = color;
  }

  public String getColor() {
    return color;
  }
}
