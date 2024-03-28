[![](https://jitpack.io/v/veonadtech/prebid-android-sdk.svg)](https://jitpack.io/#veonadtech/prebid-android-sdk)

# Prebid Mobile Android SDK
See [this page](https://jitpack.io/#veonadtech/prebid-android-sdk) for options.

## Use Maven?
Step 1. Add the JitPack repository to your build file
Add it in your root build.gradle at the end of repositories:
```
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

Step 2. Add the dependency
```
implementation 'com.github.veonadtech:prebid-android-sdk:v0.0.3.2'
implementation 'com.github.veonadtech.prebid-android-sdk:core:v0.0.3.2'
implementation 'com.github.veonadtech.prebid-android-sdk:mobile:v0.0.3.2'
implementation 'com.github.veonadtech.prebid-android-sdk:eventhandlers:v0.0.3.2'
```

## Build from source

Build Prebid Mobile from source code. After cloning the repo, from the root directory run

```
scripts/buildPrebidMobile.sh
```

to output the final lib jar and package you a demo app.