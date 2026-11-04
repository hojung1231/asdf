# HUGGY Android distribution

HUGGY currently distributes APKs from its public GitHub Releases page. It has not been reviewed or
approved by Google Play. Browser APK warnings, unknown-source installer prompts and Play Protect
harmful-app classifications are distinct. A successful build or signature check is not a malware verdict.

Starting with 2.3.1, public APKs use the non-debuggable release build, tested in both build variants.
The CI job verifies the permanent signing certificate and an explicit merged-APK permission allowlist.
The app targets API 35 and applies system-bar, display-cutout and keyboard insets on Android 15+.
The package ID and certificate remain unchanged so existing records and device accounts survive updates.

The only Android system permissions requested are Internet, network state, boot completed and package
installation. AndroidX also supplies a signature-protected internal receiver permission. Package
installation is used only after the user taps the update action. The updater accepts this repository's
release URLs, requires a higher version code, verifies SHA-256 and compares the APK's package and
certificate with the installed app. This permission must be reviewed or removed when preparing a Google
Play version, which should use Google Play updates instead of downloading APK updates.

No SMS, contacts, location, microphone, camera, accessibility or notification-reading permission is
requested by the current merged APK. This limited permission review does not establish why Google
classified 2.3 as harmful, and does not guarantee that 2.3.1 will be accepted by Play Protect.

For the harmful-app screenshot, review the official guidance and request classification review if the
app complies. Do not describe a new build as approved or warning-free without a confirmed result.
Submitting an appeal or publishing on Google Play is a separate external action requiring the owner's
Google account. No appeal has been submitted by this change.

- [Google Play Protect developer guidance](https://developers.google.com/android/play-protect/warning-dev-guidance)
- [Package installation permission policy](https://support.google.com/googleplay/android-developer/answer/12085295)

Store distribution requires the owner's developer account, current target API compliance, store update
integration, and the applicable Google review steps. Moving to a store does not itself reverse an existing
Play Protect harmful-app classification.
