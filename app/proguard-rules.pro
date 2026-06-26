# ProGuard/R8 Obfuscation Rules for Commercial Release

# 1. Keep Room Database, DAOs, and Entities Intact
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>(...);
}
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Database class *
-keep class com.example.data.** { *; }
-dontwarn androidx.room.**

# 2. Keep RevenueCat SDK Intact
-keep class com.revenuecat.purchases.** { *; }
-dontwarn com.revenuecat.purchases.**

# 3. Keep Kotlin Coroutines Intact
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# 4. Obfuscation of offline activation logic
# By not declaring any -keep rules for com.example.security.SecurityManager, CrmViewModel,
# or verification methods, R8 is allowed to fully obfuscate and rename 'SecurityManager',
# 'verifyEnterpriseBulkKey', 'verifyOrganizationCode', and local checksum fields to a, b, c, etc.,
# shielding our offline licensing algorithm from reverse engineering!
# We can also add -repackageclasses to group all obfuscated classes into a single package.
-repackageclasses 'com.example.obfuscated'
