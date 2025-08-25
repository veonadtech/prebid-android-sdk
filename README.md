[![Build Status](https://api.travis-ci.org/prebid/prebid-mobile-android.svg?branch=master)](https://travis-ci.org/prebid/prebid-mobile-android)

# Prebid Mobile Android SDK

To work with Prebid Mobile, you will need access to a Prebid Server.
See [this page](https://docs.prebid.org/prebid-server/overview/prebid-server-overview.html) for options.

## Use Maven?

Easily include the Prebid Mobile SDK using Maven. Simply add this line to your gradle dependencies:

```
implementation 'com.github.veonadtech.prebid-android-sdk:mobile:0.0.7'
implementation 'com.github.veonadtech.prebid-android-sdk:core:0.0.7'
implementation 'com.github.veonadtech.prebid-android-sdk:eventhandlers:0.0.7'
implementation 'com.github.veonadtech.prebid-android-sdk:prebidorg:0.0.7'
```

## Known Issues and Solutions

### Guava ListenableFuture Conflict with CameraX

If you're using CameraX or other libraries that depend on `com.google.guava:listenablefuture`, you may encounter dependency conflicts. 

**Solution**: Add the following to your app's `build.gradle`:

```gradle
dependencies {
    implementation 'org.prebid:prebid-mobile-sdk:2.1.4' {
        exclude group: 'com.google.guava', module: 'listenablefuture'
    }
    implementation 'com.google.guava:listenablefuture:1.0'
}
```

For more detailed information, see [GUAVA_CONFLICT_RESOLUTION.md](GUAVA_CONFLICT_RESOLUTION.md).

## Build from source

Build Prebid Mobile from source code. After cloning the repo, from the root directory run

```
scripts/buildPrebidMobile.sh
```

to output the final lib jar and package you a demo app.


## Test Prebid Mobile

Run the test script to run unit tests and integration tests.

```
scripts/testPrebidMobile.sh
```

## Impression counting method

auto
