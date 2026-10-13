# The app parses JSON into JsonElement trees by hand (no reflection or generated
# serializers), and OkHttp/coroutines ship their own consumer rules.

# The sign-in mode is persisted by enum name, so keep the names stable across builds.
-keepclassmembers enum com.vknxworks.craftyremote.data.AuthMode { *; }
