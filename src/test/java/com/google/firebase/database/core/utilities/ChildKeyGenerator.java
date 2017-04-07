package com.google.firebase.database.core.utilities;

import com.google.firebase.database.snapshot.ChildKey;
import net.java.quickcheck.Generator;
import net.java.quickcheck.generator.support.CharacterGenerator;
import net.java.quickcheck.generator.support.DoubleGenerator;
import net.java.quickcheck.generator.support.IntegerGenerator;

public class ChildKeyGenerator implements Generator<ChildKey> {

  private static final double PRIORITY_PROBABILITY = 0.05;
  private static final double INTEGER_KEY_PROBABILITY = 0.10;

  private final IntegerGenerator length;
  private final CharacterGenerator latinCharacters;
  private final CharacterGenerator unicodeCharacters;
  private final IntegerGenerator characterChooser;
  private final IntegerGenerator integerKeyGenerator;
  private final DoubleGenerator priority;
  private final boolean includePriority;

  // CSOFF: AvoidEscapedUnicodeCharactersCheck
  public ChildKeyGenerator(int maxSize, boolean includePriority) {
    this.latinCharacters = new CharacterGenerator('\u0020', '\u007e');
    this.unicodeCharacters = new CharacterGenerator('\u0000', '\uffff');
    this.characterChooser = new IntegerGenerator(0, 10);
    this.integerKeyGenerator = new IntegerGenerator();
    this.length = new IntegerGenerator(1, maxSize);
    this.priority = new DoubleGenerator(0, 1);
    this.includePriority = includePriority;
  }

  private boolean isValidChildKeyCharacter(char c) {
    // disallow ASCII control characters
    if (c <= '\u001F') {
      return false;
    }
    // reserved code points, can't be encoded in utf-8
    if (c >= '\ud800' && c <= '\udfff') {
      return false;
    }
    switch (c) {
      case '[':
      case ']':
      case '.':
      case '#':
      case '$':
      case '/':
      case '\u007F':
        // disallow any of [].#$/\u007f
        return false;
      default:
        // allow everything else
        return true;
    }
  }
  //CSON: AvoidEscapedUnicodeCharactersCheck

  @Override
  public ChildKey next() {
    if (this.includePriority && this.priority.nextDouble() < PRIORITY_PROBABILITY) {
      return ChildKey.getPriorityKey();
    } else if (this.priority.nextDouble() < INTEGER_KEY_PROBABILITY) {
      return ChildKey.fromString(String.valueOf(this.integerKeyGenerator.next()));
    } else {
      int size = this.length.nextInt();
      StringBuilder builder = new StringBuilder();

      for (int count = 0; count < size; ++count) {
        char next;
        do {
          boolean unicode = this.characterChooser.nextInt() == 0;
          next = unicode ? this.unicodeCharacters.nextChar() : this.latinCharacters.nextChar();
        } 
        while (!isValidChildKeyCharacter(next));
        builder.append(next);
      }

      return ChildKey.fromString(builder.toString());
    }
  }
}
