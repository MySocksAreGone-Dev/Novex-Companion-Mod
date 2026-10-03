# Novex Companion

Lightweight Java/Fabric client mod for [Novex Client](https://github.com/MySocksAreGone-Dev/Novex-Client) and its existing Novex account and social service. Adds a collision-aware Novex button to the in-world pause menu without replacing vanilla controls or changing the title screen.

## License

Novex Companion is **source-visible, not open source**. Copyright (c) 2026 Wild. All Rights Reserved. Source viewing does not grant permission to copy, modify, reuse or redistribute the project or its builds. See [LICENSE](LICENSE). Third-party components retain their own licenses; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Novex Companion is an independent third-party Minecraft mod, not affiliated with, endorsed by, or associated with Mojang Studios or Microsoft.

## Build / install

Java 21, Fabric Loader 0.19.5, Loom 1.17.21, Gradle wrapper 9.6.0. Exact Yarn and Fabric API versions are pinned in `versions.json`.

```sh
./gradlew build -Ptarget=1.21.11
# One separate JAR for each Minecraft version:
python3 tools/build-matrix.py
```

Output: `build/<minecraft>/libs/novex-companion-0.3.0+mc<minecraft>.jar`. Install the matching Fabric API and this JAR into that version's instance `mods` directory. Do not install multiple Companion builds together. `versions.json` lists 1.21 and 1.21.1–1.21.11; consult `build/matrix-results.json` for actual local build results. A successful compile does not prove runtime compatibility with every mod.

The published release provides separate JARs for Minecraft **1.21, 1.21.1–1.21.11, 26.1.2, 26.2 and 26.3**. Choose exactly your Minecraft version, install Fabric Loader 0.19.5 or newer and the matching Fabric API, then put the production JAR in your instance’s `mods` directory. Open a world/server, press ESC and select **Novex**. Minecraft 1.21.x needs Java 21; 26.x needs Java 25.

## Persistent sign-in

Windows uses the OS account to encrypt `novex-session.dpapi` beside the normal config; the encrypted file is not portable to another Windows account. Linux uses `/usr/bin/secret-tool` and your desktop Secret Service keyring, scoped to this instance and Supabase origin. On Linux, install `libsecret` (Fedora/Arch) or `libsecret-tools` (Ubuntu/Mint) if `secret-tool` is missing, and use an unlocked desktop keyring such as GNOME Keyring or a Secret Service-compatible wallet. Novex never runs sudo or saves a plaintext fallback. The account screen reports when secure storage is unavailable. A temporary network failure does not delete the saved session.

Do not copy encrypted session files into modpacks or exports. No Microsoft/Minecraft tokens are persisted.

## Minecraft 26.x

The isolated `minecraft26/` build uses Java 25 and unobfuscated Minecraft names (no Yarn). It shares the existing API, secure session storage, settings and placement code. Use a Java 25 **JDK**, then run from the project root:

```sh
./gradlew -p minecraft26 build -Ptarget=26.3
```

Exact API versions are in `minecraft26/versions.json`. Outputs go to `build/<minecraft>/libs/`. Each JAR requires the matching Minecraft release, Fabric Loader 0.19.5 or later, and that release's Fabric API. See the validation notes for which targets have passed. Official porting reference: https://fabricmc.net/2026/09/15/263.html .

## Features

- Friends, incoming requests, username search, accept/decline/remove, text conversations, older history and full-message reading.
- Same **Novex email/password account** as the launcher. This is separate from Microsoft/Minecraft authentication. Create/reset an account through the existing launcher. No second registration system.
- Access tokens remain in memory. Refresh tokens are saved using Windows DPAPI (CurrentUser) or Linux Secret Service, restored at startup and rotated when refreshed. Sign-out removes the saved session; an expired/revoked session requires a new login. Password display and narration are masked; passwords are never saved.
- Authenticated Supabase Realtime invalidates social views, with a 30-second fallback check. Lightweight, coalesced unread/request/online notifications; the pause button shows unread count.
- Opt-in server sharing/join permission, friends-only Minecraft presence, 60-second heartbeat and 150-second expiry after unexpected termination. Presence is removed best-effort on normal exit.
- Published partner servers use existing `home_slides`. Copy IP and confirmed Join use Minecraft's normal connection screen without changing `servers.dat`. Remote player counts/icons are not fetched yet.
- Verified Minecraft badge in TAB, nametags and signed player chat, independently controlled. Uses the existing Novex logo as a tiny bitmap glyph. No badges on unverified accounts or system/server chat; cached/batched UUID lookups at most once per minute, up to 100 visible players.
- Explicit Settings → Badges verification checks the current Minecraft session through the Novex HTTPS verification function and Minecraft Services. The Minecraft token is not stored. Verified links expire after 30 days and can be removed. Only Minecraft UUIDs are public; Novex account links stay private.
- Local JSON preferences, local session duration/mode and an Open Screenshots Folder action. Vanilla F2 behavior is preserved.

## Supabase deployment

These additions were deployed to the **existing Novex project** `ouzycyksxgennsyphhxg`, not a new social database. Tracked SQL is in `supabase/migrations/`; the Edge Function is in `supabase/functions/companion-verify-minecraft/index.ts`.

The migrations depend on the launcher's existing `profiles`, `friends`, `friend_requests`, `messages`, `novex_private` schema and Home content migration. Do not apply them to an empty project without those prerequisites. Already-deployed migrations do not need manually rerunning.

- Original social tables and `accept_friend_request` remain in use.
- Message INSERT requires the authenticated sender and an accepted friendship. Friend-request INSERT requires the authenticated sender, pending status and a different recipient. No direct client friendship grants.
- Decline/remove/read/presence RPCs derive identity from `auth.uid()`, use an empty search path and have restricted grants.
- `novex_private.companion_reads`, `companion_presence`, `minecraft_links` deny direct client access. No-policy RLS advisor notices on these private RPC-only tables are intentional.
- Only the server's `service_role` can call `companion_record_verified_link`; authenticated clients cannot forge verification. The Edge Function requires JWT verification and also validates the Novex session through Auth.
- Realtime publication preserves the Home tables and adds only `companion_social_revision`, readable by its owner. Private social tables are not broadcast; triggers emit per-user invalidation counters.
- The JAR includes only the existing public URL/anon configuration in `novex-services.json`. Privileged keys come exclusively from Supabase's server environment, never the mod.

Backend operators should configure password protection according to [Supabase’s password-security guidance](https://supabase.com/docs/guides/auth/password-security).

## Validation

Version 0.3.0 validation completed on 2026-10-03: all 15 targets (1.21, 1.21.1–1.21.11, 26.1.2, 26.2 and 26.3) passed build and automated checks. Restart restoration, refresh-token rotation, sign-out removal, revoked-session rejection and offline preservation passed with a stub service. An actual Linux Secret Service store/load/delete test passed using a temporary dummy credential, which was removed. Public API access and invalid-session rejection passed. All deliverable JARs passed version, session-class and private-file/credential-pattern checks.

The 26.3 development client initialized its window, OpenGL, audio, fonts and texture atlases without a Novex startup error. It was deliberately stopped with SIGTERM; Gradle therefore reports exit 143 for that smoke run. This was a startup check, not a visual/gameplay or real-account login test. Windows DPAPI, real-account restart/sign-out, and manual 26.x UI/gameplay checks remain required.

Previous 0.2.0 validation completed on 2026-10-02: all 12 separate targets (1.21 and 1.21.1–1.21.11) passed Gradle build and the automated foundation/API/session checks. All output JARs passed exact-version metadata and private-file/credential-pattern checks.

`build` runs dependency-free Java checks for placement/resizing, privacy defaults, config persistence/recovery, URL/key validation, bounded responses, session login/refresh/logout races and privileged RPC rejection. Session success tests use a local HTTP stub, not a real user's password.

`supabase/tests/social_security.sql` tests authorization in a transaction and rolls every fixture back. Live tests checked non-friend message rejection, private conversation/presence isolation, accept/decline/remove, unread state and badge grant rejection. Public HTTP checks confirmed anonymous private-API/verification rejection.

The Minecraft 1.21.11 development client ran with Fabric API and Mod Menu 17.0.1, entered and left a singleplayer world, and exited cleanly. This is confirmed by runtime logs, not a visual inspection of the screens. The development account cannot authenticate to Realms; that warning is expected.

Manual checks still required: real-account sign-in/refresh, launcher ↔ game messaging, Realtime reconnection, settings/privacy timing, badge linking with a legitimate Minecraft session, rendering and joining, other-mod compatibility and the full window-size/version matrix. OS screen-capture permission previously prevented visual automation; do not interpret builds as visual testing.

## Integration limits

Secure launcher single sign-on is not wired yet. `SupabaseApi.attachSession` is the short-lived, server-validated handoff foundation; it must only be called after an authenticated local handoff. Never put a session in arguments or ordinary config files. The mod works with manual Novex login independently of the launcher.

Launcher presence itself has no compatible heartbeat implementation yet, so the mod currently reports Companion Minecraft presence, not whether somebody merely has the launcher open. Optional HUD modules, automatic F2 actions and remote server ping/icon downloads are not implemented. No analytics or world/log/screenshot uploads are performed.
