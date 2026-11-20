package slick.android;

public final class Emoji {
  private Emoji() {}

  /** Use a built-in emoji when a profile contains an empty or whitespace-only emoji name. */
  public static String name(String name) {
    if (name == null) return name;
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (!Character.isWhitespace(c) && !Character.isSpaceChar(c)) return name;
    }
    return "question";
  }
}
