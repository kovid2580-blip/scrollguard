-keepattributes *Annotation*
-keep class com.scrollguard.app.data.entity.** { *; }
-keep class com.scrollguard.app.domain.model.** { *; }
-keep class com.scrollguard.app.domain.usecase.** { *; }
-keep class com.scrollguard.app.blocking.** { *; }
-keep class com.scrollguard.app.scheduling.** { *; }
-dontwarn kotlinx.serialization.**

