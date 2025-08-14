# Add project-specific ProGuard rules here.
# By default, Android Studio and the Android Gradle plugin now apply
# a default rules file included in the plugin.
#
# To enable ProGuard in your project, add the following to your module-level
# build.gradle file:
#
# buildTypes {
#     release {
#         minifyEnabled true
#         proguardFiles getDefaultProguardFile('proguard-android.txt'),
#                       'proguard-rules.pro'
#     }
# }

# Firebase Realtime Database
-keepclassmembers class com.wains.app.utils.FirebaseManager {
    <init>(...);
}

# Keep the Firebase models from being obfuscated
-keep class com.wains.app.models.** { *; }

# Keep all custom services
-keepclassmembers class com.wains.app.services.** { *; }

# Keep all custom utilities
-keepclassmembers class com.wains.app.utils.** { *; }

# Keep all enums, they're often used by Firebase
-keepclassmembers enum * {
    *;
}
