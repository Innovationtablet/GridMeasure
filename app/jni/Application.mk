APP_STL := gnustl_static
APP_CPPFLAGS := -frtti -fexceptions -std=c++11
# note: i'm not sure what the difference between this setting and
# the app's build.gradle android.defaultConfig.externalNativeBuild.ndkBuild.abifilters is.
#APP_ABI := arm64-v8a
