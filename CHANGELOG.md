# Changelog

All notable changes to Vistaar. Versions are the app's `versionName`; dates are when the build was made.

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
