# HUGGY social backend

1. Create a Supabase project in the user's account.
2. Apply migrations/202611040001_social.sql, then migrations/20261104110959_mutual_friend_requests.sql.
3. Enable Anonymous Sign-Ins. The app creates a device-bound authenticated account from a nickname. Sign-out or uninstall loses access unless an identity is linked later.
4. Set GitHub Actions secrets HUGGY_SUPABASE_URL and HUGGY_SUPABASE_KEY.
   Use the project HTTPS URL and publishable key. Never put a service-role key in the Android app.
5. Run Android APK again. Public release publishing is skipped until both configuration values exist and HUGGY_SOCIAL_READY is true.
6. Test with two independent accounts and devices. Enable current-state sharing, exchange friend codes,
   send a request and confirm neither user sees the other's state before approval. Accept as the recipient,
   confirm both lists show each other, record through the widget, disable sharing, then remove the friend.
   Also test decline, sender cancellation, duplicate requests and reverse requests.
   Test offline recording followed by reconnection and app restart.

The server exposes only the current level, nickname, record time and three recent events to approved mutual friends
when sharing is enabled. Weight and hunger history remain on the device.
The foreground polls every 15 seconds. Background widgets use Android's inexact scheduling.

CI tests the SQL permissions, disabled sharing, follow/unfollow and duplicate/out-of-order events
against PostgreSQL 16 before building Android. This does not replace verification against the deployed
Supabase Auth service and real phones.

## Mutual friend upgrade (2.3)

The second migration is transactional and rerunnable. It converts prior one-way follows into pending
requests, retaining already mutual follows. Accepting as the recipient inserts both friend edges in one
transaction. Only the two participants can see a request. Pair-level transaction locks serialize requests,
approval, rejection, cancellation and removal. Removing a friend removes both directions.

`huggy_follow` remains available to older apps but now sends a request. An older app cannot approve
requests; the recipient needs 2.3. Current-state sharing remains a separate, opt-in profile setting.
The app and widget show real friends only after approval. Weight/history stay local.

After applying the second migration, `select public.huggy_health();` must return `{"version":2}`.
CI tests migration compatibility, reruns, permissions and request lifecycle. Publishing 2.3 waits until
the live health RPC reports version 2, so clients are not offered an update before its backend is ready.
After updating the server, dispatch Android APK to publish the tested app and its update manifest.
