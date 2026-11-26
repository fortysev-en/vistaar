# Changelog

All notable changes to Vistaar. Versions are the app's `versionName`; dates are when the build was made.

## [0.4] - 2026-11-26 (Android 0.4, build 4) / webOS 0.4.0

Android, webOS and Samsung Tizen share the Guide, Favourites, splash and disclaimer, guide-first Up/Down and in-list zapping. The phone remote is Android only (a TV web app cannot run a server).

### Added
- **Phone remote** (Settings, Remote): turn it on, scan the QR code or type the address on a phone on the same Wi-Fi, enter the 4-digit PIN shown on the TV, and the phone becomes arrow keys, OK, Back, Home, Menu and CH +/-, plus a text box for Search and the playlist link. Off by default, home network only, five wrong PINs lock it for a minute, and it switches itself off after 15 minutes without use. A phone icon shows in the corner while one is connected.
- **First-run flow**: a splash screen with the Vistaar mark (and, on Android, the fortyseven wordmark), then a disclaimer with *Accept and continue* (also in Settings, About). Jio sign-in no longer opens the app; it lives in Settings, so the app starts straight in free mode.
- **Rearrange tabs and categories** (all platforms): hold OK on a top tab to move it with Left and Right, or on a category (Jio TV genres or languages, Free TV) to move it with Up and Down; press OK to drop it. The order is remembered, and Settings, Library, Reset order puts everything back.
- **Startup tab** (all platforms): Settings, Startup chooses which tab the app opens on (Home unless you pick another). Back from that tab goes to Home.
- **Pick your categories** (all platforms): the first-run screens now ask for languages and then categories. Pick them in order of importance. Home shows Recently watched, Your favourites, then a row per category (your picks first, up to eight rows, more if you picked more), with Jio and free channels together. It replaces the Free TV and language rows. Change it in Settings, Library, Home categories.
- **Guide as a time grid** (Android and webOS): channels down the left, a three-hour window of programmes across, the focused programme described above, a day picker, and Earlier, Now and Later controls.
- **Samsung Tizen build** (experimental): the web app packaged as `Vistaar-Tizen-0.4.0.wgt` for Samsung TVs from 2020, with the Tizen remote keys and exit. Install it with the Apps2Samsung phone app; see [docs/tizen-install.md](docs/tizen-install.md). Tried in the Samsung TV emulator and on one real TV; Jio's encrypted channels are untested on Tizen.

### Changed
- **Favourites** is a grid of large channel-logo cards. Hold OK on a card to move it, rename it or remove it. Your order is kept, and a new name shows wherever that favourite appears.
- In the player, Up and Down show the guide first; only while it is showing do they change channel. The remote's CH + and - still change it at once.
- Up and Down in the player stay inside the list you opened it from (a category on Jio TV, Free TV, HD or Favourites).
- Your channels and favourites show as soon as the app opens: the last channel list is kept and refreshed in the background, instead of waiting for Jio on every start.
- A stream that carries no audio at all (some free channels, such as All Time Movies) now shows "This stream has no audio" in the player instead of just playing silently.
- The splash screen is animated: a soft glow blooms behind the mark, the name rises in with a pass of light, and a loading line fills before the app opens (about 2.6 seconds).
- In Settings, Left from a row goes back to the category it belongs to.
- Settings scrolls, and a focused row is no longer clipped at the sides.

## [0.3] - 2026-11-20 (Android 0.3, build 3) / webOS 0.3.0 beta

### Added
- **LG webOS build (beta)** for 2020+ TVs (webOS 5+), with the same screens as the Android app, a helper service for stream keys, and a Windows one-click installer. See [docs/webos-install.md](docs/webos-install.md).
- **Languages**: choose the languages you watch on first run (several allowed); only those Jio channels are listed and scanned first. Change it in Settings.
- **Genres**: Jio TV can be browsed by Genre or by Language.
- **Live preview** panel: a muted live picture of the focused channel on Jio TV and Free TV.
- **Free-only mode**: *Continue with free channels* on the sign-in screen skips Jio completely.
- **Free TV playlist**: Settings, then Free TV playlist, switches to another country or any M3U link.
- **Device limit**: when Jio says the account is on too many devices, sign-in offers to log out the other devices. *Sign out* also tells Jio, so the TV stops counting against the limit.
- **Remembered audio language** per channel.
- **In-app updates**: checks GitHub releases and offers to install (Settings, About).
- Settings: GitHub and Documentation rows, Check for updates.

### Changed
- **Premium channels** (Jio's `is_premium` flag, for example the Star channels) show a small gold crown on their logo everywhere.
- A free channel that is also on your Jio plan (such as "StarPlus (India) HD") borrows that channel's programme guide, so Home, the rows, the player and the guide show what is on. Free channels with no Jio twin say they have no guide.
- The saved plan check is re-tested in the background: when it is more than 10 minutes old, the app quietly re-tries only the channels it had refused, so channels newly added to your plan (a new Star pack, say) appear without pressing Refresh.
- "Live TV" is now **Jio TV**.
- Free channels follow the languages you chose (using iptv-org's per-language lists).
- Down stops at the bottom of the selected category and Left returns to it; holding Down no longer jumps focus to the left list.
- The channel scan is throttled so the UI stays smooth, retries channels that failed, and checks your chosen languages first.

### Fixed
- Tamil and other non-selected channels appearing under Hindi.
- Focus leaking out of the list while a direction key is held.

## [0.2] - 2026-11-02

### Added
- **Free TV** tab: the iptv-org India playlist (about 850 channels) with favourites, a Home row, search and the same player.
- **HD** tab: every HD channel from your Jio plan and the free list.

### Changed
- Faster channel changes: the next and previous channel are resolved in the background and playback starts after a 1 second buffer.
- Category lists repaint in place so focus no longer jumps; Down on the last row stays put.
- Home falls back to free channels when the Jio scan finds none.

### Fixed
- Expired Jio sessions (HTTP 419) now refresh the token and retry.

## [0.1] - 2026-10-28

First release: Android TV client for your own Jio TV account with number and code sign-in, plan-aware channel list, Home, Live TV, Guide, Search, Favourites, Player (quality, audio, aspect) and Settings.
