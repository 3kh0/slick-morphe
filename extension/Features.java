package slick.android;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

public final class Features {
  private static final SecureRandom random = new SecureRandom();
  private static final Map<String, String> names =
      new LinkedHashMap<String, String>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> entry) {
          return size() > 256;
        }
      };

  public static boolean amoled() {
    return Settings.enabled("amoled");
  }

  private static final class PronounSuffix {}

  public static void timestampPronouns(Object binding) {
    try {
      TextView time = (TextView) Host.get(Host.get(binding, "L$3"), "messageTime");
      SpannableStringBuilder text = new SpannableStringBuilder(time.getText());
      for (PronounSuffix m : text.getSpans(0, text.length(), PronounSuffix.class)) {
        int start = text.getSpanStart(m), end = text.getSpanEnd(m);
        text.removeSpan(m);
        text.delete(start, end);
      }
      Object metadata = Host.get(binding, "L$5");
      if (time.getVisibility() == View.VISIBLE
          && text.length() > 0
          && !Boolean.TRUE.equals(Host.get(metadata, "isHidden"))
          && !Boolean.TRUE.equals(Host.get(metadata, "isPendingEdit"))) {
        String suffix = pronouns("", Host.get(Host.get(binding, "L$1"), "user"));
        if (!suffix.isEmpty()) {
          int start = text.length();
          text.append(suffix);
          text.setSpan(new PronounSuffix(), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
      }
      // Inherit Slack's timestamp font, size and color; never alter sender text.
      time.setText(text);
    } catch (ReflectiveOperationException | RuntimeException e) {
      Log.e("Slick", "Timestamp pronoun hook failed", e);
    }
  }

  public static String pronouns(String original, Object member) {
    if (!Settings.enabled("pronouns")
        || original == null
        || member == null
        || !member.getClass().getName().equals("slack.model.User")) return original;
    try {
      if (Boolean.TRUE.equals(Host.get(member, "isBotOrSlackBot"))) return original;
      if (!Host.string(member, "id").matches("[UW][A-Z0-9]+")) return original;
      String p = cleanPronouns(Host.string(Host.get(member, "profile"), "pronouns"));
      return p.isEmpty() ? original : original + " · " + p;
    } catch (ReflectiveOperationException e) {
      Log.e("Slick", "Pronoun hook failed", e);
      return original;
    }
  }

  static String cleanPronouns(String text) {
    String v = text.replaceAll("\\s+", " ").trim();
    return v.substring(0, Math.min(40, v.length()));
  }

  public static synchronized String anonymousName(String original) {
    if (!Settings.enabled("anonymous_uploads") || original == null) return original;
    String base =
        original.substring(Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\')) + 1);
    if (base.matches("slick-[a-z0-9]{7}(\\.[^./\\\\]+)?")) return base;
    String existing = names.get(original);
    if (existing != null) return existing;
    StringBuilder name = new StringBuilder("slick-");
    for (int i = 0; i < 7; i++)
      name.append("abcdefghijklmnopqrstuvwxyz0123456789".charAt(random.nextInt(36)));
    int dot = base.lastIndexOf('.');
    if (dot > 0 && dot < base.length() - 1) name.append(base.substring(dot));
    String v = name.toString();
    names.put(original, v);
    return v;
  }

  public static void reactions(Object view) {
    if (!Settings.enabled("who_reacted")) return;
    try {
      LinkedHashSet<String> labels = new LinkedHashSet<>();
      for (Object r : (Collection<?>) Host.get(Host.get(view, "viewModel"), "reactions")) {
        String names = Host.string(r, "displayNames");
        if (!names.isEmpty()) labels.add(names);
        else for (Object u : (Collection<?>) Host.get(r, "users")) labels.add(u.toString());
      }
      if (labels.isEmpty()) return;
      TextView count = (TextView) Host.get(view, "count");
      StringBuilder text = new StringBuilder(count.getText()).append(" · ");
      int n = 0;
      for (String label : labels) {
        if (n == 3) break;
        if (n++ > 0) text.append(", ");
        text.append(label.substring(0, Math.min(label.length(), 60)));
      }
      if (labels.size() > 3) text.append(" +").append(labels.size() - 3);
      count.setText(text);
    } catch (ReflectiveOperationException | RuntimeException e) {
      Log.e("Slick", "Reaction hook failed", e);
    }
  }
}
