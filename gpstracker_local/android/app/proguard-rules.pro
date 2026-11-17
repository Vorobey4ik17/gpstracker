# Keep manifest components (instantiated by framework)
-keep public class com.secure.netcore.LocationService { public <init>(...); }
-keep public class com.secure.netcore.MainActivity { public <init>(...); }
-keep public class com.secure.netcore.BootReceiver { public <init>(...); }
-keep public class com.secure.netcore.MyVpnService { public <init>(...); }

# JSON / serialization
-keep class org.json.** { *; }
-keepclassmembers class * { @org.json.JSONObject <fields>; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Attributes
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod
