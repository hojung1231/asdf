# HUGGY social backend

1. Create a Supabase project in the user's account.
2. Apply migrations/202611040001_social.sql once.
3. Enable Anonymous Sign-Ins. The app creates a device-bound authenticated account from a nickname. Sign-out or uninstall loses access unless an identity is linked later.
4. Set GitHub Actions secrets HUGGY_SUPABASE_URL and HUGGY_SUPABASE_KEY.
   Use the project HTTPS URL and publishable key. Never put a service-role key in the Android app.
5. Run Android APK again. Public release publishing is skipped until both configuration values exist and HUGGY_SOCIAL_READY is true.
6. Test with two independent accounts and devices. Enable current-state sharing, exchange friend codes,
   follow, record through the widget, verify the follower sees the new level, disable sharing, and unfollow.
   Test offline recording followed by reconnection and app restart.

The server exposes only the current level, nickname, record time and three recent events to followers
when sharing is enabled. Weight and hunger history remain on the device.
The foreground polls every 15 seconds. Background widgets use Android's inexact scheduling.

CI tests the SQL permissions, disabled sharing, follow/unfollow and duplicate/out-of-order events
against PostgreSQL 16 before building Android. This does not replace verification against the deployed
Supabase Auth service and real phones.
