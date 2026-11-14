# Only the minified instrumentation APK uses this rule. Error Prone's
# IncompatibleModifiers meta-annotation describes Java compiler modifiers;
# javax.lang.model is not an Android runtime API. AndroidX uses the supplied
# CanIgnoreReturnValue/MustBeClosed annotations, not that compiler model.
-dontwarn javax.lang.model.element.Modifier
