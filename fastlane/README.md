fastlane documentation
----

# Installation

Make sure you have the latest version of the Xcode command line tools installed:

```sh
xcode-select --install
```

For _fastlane_ installation instructions, see [Installing _fastlane_](https://docs.fastlane.tools/#installing-fastlane)

# Available Actions

## Android

### android test

```sh
[bundle exec] fastlane android test
```

Run unit tests and lint (debug only)

### android build_debug

```sh
[bundle exec] fastlane android build_debug
```

Build debug APK (for PR artifact)

### android build_release

```sh
[bundle exec] fastlane android build_release
```

Build and sign release AAB

### android deploy_internal

```sh
[bundle exec] fastlane android deploy_internal
```

Build, sign, and upload AAB to Play Store internal track

### android upload_internal

```sh
[bundle exec] fastlane android upload_internal
```

Upload an already-built AAB to the Play Store internal track (no rebuild)

### android upload_alpha

```sh
[bundle exec] fastlane android upload_alpha
```

Upload AAB directly to Play Store closed testing (alpha) track

### android deploy_alpha

```sh
[bundle exec] fastlane android deploy_alpha
```

Build, sign, and upload AAB to Play Store closed testing (alpha) track

### android deploy_production

```sh
[bundle exec] fastlane android deploy_production
```

Promote internal build to production

### android update_changelog

```sh
[bundle exec] fastlane android update_changelog
```

Update release notes for an already-uploaded build (no rebuild, no re-upload). Pass track:internal|alpha|production (default internal) and version_code:<code> if the track has more than one release

### android upload_metadata

```sh
[bundle exec] fastlane android upload_metadata
```

Upload store listing metadata, images and screenshots (no build)

----

This README.md is auto-generated and will be re-generated every time [_fastlane_](https://fastlane.tools) is run.

More information about _fastlane_ can be found on [fastlane.tools](https://fastlane.tools).

The documentation of _fastlane_ can be found on [docs.fastlane.tools](https://docs.fastlane.tools).
