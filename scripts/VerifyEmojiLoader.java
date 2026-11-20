import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

/** Runs the real Slack coroutine on Android, without a workspace or network connection. */
public final class VerifyEmojiLoader {
  private static Object allocate(String name) throws Exception {
    Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
    var field = unsafeClass.getDeclaredField("theUnsafe");
    field.setAccessible(true);
    Object unsafe = field.get(null);
    return unsafeClass
        .getMethod("allocateInstance", Class.class)
        .invoke(unsafe, Class.forName(name));
  }

  public static void main(String[] args) throws Exception {
    boolean patched = args[0].equals("patched");
    Class<?> referenceType = Class.forName("slack.libraries.emoji.api.model.EmojiReference");
    Class<?> loaderType = Class.forName("slack.emoji.impl.routine.LoadEmojiRoutine$invoke$2");
    Constructor<?> constructor = loaderType.getDeclaredConstructors()[0];
    constructor.setAccessible(true);
    var invoke = loaderType.getDeclaredMethod("invokeSuspend", Object.class);
    invoke.setAccessible(true);
    Object host = allocate("slack.emoji.impl.routine.LoadEmojiRoutine");
    Object options = allocate("slack.libraries.emoji.api.routine.LoadEmoji$Options");
    for (String name : new String[] {"", " ", "\t\n", "\u00a0", "\u2007", "\u202f", "::"}) {
      Object reference =
          Proxy.newProxyInstance(
              referenceType.getClassLoader(),
              new Class<?>[] {referenceType},
              (proxy, method, arguments) -> name);
      Object coroutine = constructor.newInstance(reference, host, options, 32, null);
      try {
        Object result = invoke.invoke(coroutine, new Object[] {null});
        if (!patched || result != null)
          throw new AssertionError("Expected " + (patched ? "null" : "exception"));
      } catch (InvocationTargetException error) {
        Throwable cause = error.getCause();
        if (patched
            || !(cause instanceof IllegalArgumentException)
            || !(cause.getMessage().equals("Emoji name is blank")
                || cause
                    .getMessage()
                    .equals("Unable to transform an empty emoji name to a canonical emoji name")))
          throw error;
      }
    }
    System.out.println(
        "PASS: "
            + (patched
                ? "invalid emoji names return no emoji"
                : "stock blank-name crashes reproduced"));
  }
}
