# Add project specific ProGuard rules here.

# --- Persistence (see persistence/MatchSaveStore.kt) ---
# Gson reads these classes' fields by NAME via reflection to save and load
# a match. Minification renames classes and fields by default, which would
# make a saved match unreadable the moment the JSON's keys no longer match
# what Gson looks for on the next launch. Keep the whole logic and
# persistence packages exactly as compiled, names included.
-keep class com.cricketsim.logic.** { *; }
-keep class com.cricketsim.persistence.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepattributes Signature
-keepattributes *Annotation*

# Gson ships its own consumer rules for TypeToken/generic handling; the
# above is the app-specific addition on top of that.
