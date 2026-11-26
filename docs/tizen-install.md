# Vistaar on Samsung Tizen TVs (experimental)

The Tizen build is the same web app as the LG webOS one, packaged for Samsung TVs from 2020 (Tizen 5.5) onwards. Samsung does not list it in its store, so it is sideloaded in **Developer Mode**.

**Status:** it runs in the Samsung TV emulator and has been used on a real 2021 Samsung TV (Home, Free TV, HD, Guide, Favourites, Search, Settings and playback with the TV remote). Jio's encrypted channels are untested on Tizen. The phone remote is Android only.

## Easiest: Apps2Samsung (Android phone)

1. On the TV: open the **Apps** panel, type **12345** on the remote's number pad, switch **Developer mode** ON, enter the IP address of the device you will install from (your phone), then switch the TV fully off and on again (hold the power button for 2 seconds).
2. On an Android phone on the same Wi-Fi as the TV, install **[Apps2Samsung](https://github.com/Apps2Samsung/Apps2Samsung)** (see its page for the download and its own setup steps).
3. In Apps2Samsung, connect to the TV (its IP address is under Settings, General, Network, Network Status) and pick **[`Vistaar-Tizen-0.4.0.wgt`](https://github.com/fortysev-en/vistaar/raw/v0.4/releases/Vistaar-Tizen-0.4.0.wgt)** to install it.
4. Open **Vistaar** from the TV's app list.

Keep Developer Mode on: turning it off removes sideloaded apps. Apps2Samsung is a separate project, not made or checked by Vistaar; use it at your own risk and read its page first.

## Manual route: Tizen Studio

Samsung only lets a TV install a package signed with a certificate that lists that TV's own ID (DUID), so this route needs a Samsung certificate.

1. Install [Tizen Studio](https://developer.tizen.org/development/tizen-studio/download) (the CLI-only installer is enough) with the **TV Extensions**, **Certificate Manager** and **Samsung Certificate Extension** (`cert-add-on`) packages. On Windows the package manager needs to run as administrator.
2. In **Certificate Manager**, create a **Samsung** certificate profile for a **TV** with a free Samsung account. Add your TV's DUID (`sdb shell "0 getduid"` once connected). A plain Tizen certificate is rejected ("Invalid certificate chain"), and a certificate with the wrong DUID gives "Invalid format of certificate in signature".
3. Sign the package: `tizen package -t wgt -s <your-profile> -- tizen/app`.
4. Connect and install:
```
sdb connect <TV-IP>
tizen install -n Vistaar.wgt -t <TV-name-from-"sdb devices"> -- <folder with the signed wgt>
tizen run -p FtySvnVstr.Vistaar -t <TV-name>
```

## Build the package yourself
```
cd web && npm install
cd ../tizen && npm run package
```
This builds the web app for Tizen (`web/dist-tizen`, Chromium 67+), stages it with `config.xml` and the icon in `tizen/app/`, and writes `tizen/out/Vistaar-Tizen-<version>-unsigned.wgt`. Set `TIZEN_PROFILE=<your-profile>` to have `npm run package` sign it too.

## What to expect
- Same screens as the webOS app (Home, Jio TV, Free TV, HD, time-grid Guide, Favourites, Search, Settings, player with live preview). The remote's arrows, OK, Back, CH +/- and the media keys are mapped.
- **Free TV** channels play through the TV's own player, like on webOS.
- **Jio channels are the open question.** Their stream keys need the sign-in headers, and webOS gets them through a helper service that Tizen does not have. The Tizen build sends the headers from the page instead, which only works if the TV's web engine allows it. Testers are welcome; report what Settings, About shows and whether an encrypted channel plays.
- Not in the Tizen build: the phone remote. DRM channels are not supported.
