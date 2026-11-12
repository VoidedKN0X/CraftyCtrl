# The app parses JSON into JsonElement trees by hand (no reflection or generated
# serializers), and OkHttp/coroutines ship their own consumer rules.

# The sign-in mode is persisted by enum name, so keep the names stable across builds.
-keepclassmembers enum com.vknxworks.craftyctrl.data.AuthMode { *; }

# No debug, verbose or info logging in release builds (CraftyCtrl's own code doesn't log; this also
# silences libraries).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
