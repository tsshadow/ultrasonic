-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }

-keepclassmembers public class org.ultrasonic.domain.api.models.** {
    public synthetic <methods>;
}

-keep class org.jetbrains.kotlin.** { *; }
-keep class org.jetbrains.annotations.** { *; }

