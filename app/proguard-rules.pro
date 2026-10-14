# kotlinx.serialization 的序列化器由 R8 生成后按名字反射查找，官方保留规则
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.ifthen.app.**$$serializer { *; }
-keepclassmembers class com.ifthen.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.ifthen.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# 清单里声明的入口/服务必须由 R8 保留原名
-keep class com.ifthen.app.MainActivity { <init>(); }
