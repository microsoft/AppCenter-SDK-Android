# The following options are set by default.
# Make sure they are always set, even if the default proguard config changes.
-dontskipnonpubliclibraryclasses
-verbose
-dontwarn com.google.firebase.iid.FirebaseInstanceId
-dontwarn com.google.firebase.iid.FirebaseInstanceIdService
-keep class com.google.firebase.iid.FirebaseInstanceId {
    ** getInstance();
    ** getToken();
}
