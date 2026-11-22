# The app parses JSON into JsonElement trees by hand (no reflection or generated
# serializers), and OkHttp/coroutines ship their own consumer rules.

# The sign-in mode is persisted by enum name, so keep the names stable across builds.
-keepclassmembers enum com.vknxworks.craftyctrl.data.AuthMode { *; }

# No logging at all in release builds: CraftyCtrl's own code doesn't log, and any Log call that
# sneaks in later (or one in a library) is stripped.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
    public static int println(...);
}
