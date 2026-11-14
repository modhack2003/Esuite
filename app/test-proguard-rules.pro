# Only the minified instrumentation APK uses this rule. Error Prone's
# IncompatibleModifiers meta-annotation describes Java compiler modifiers;
# javax.lang.model is not an Android runtime API. AndroidX uses the supplied
# CanIgnoreReturnValue/MustBeClosed annotations, not that compiler model.
-dontwarn javax.lang.model.element.Modifier

# JUnit/AndroidJUnitRunner/Compose test discovery uses reflection. Preserve the
# instrumentation harness, which is not shipped. The tested application and
# production release retain their own independent R8 optimization settings.
-keep class ** { *; }
