# Install Vistaar on an LG webOS TV (beta)

Vistaar for webOS is not in the LG Content Store, so it is installed through LG's **Developer Mode**. With the installer it takes about **5 minutes**: a few switches on the TV, then one double-click on a Windows computer on the same Wi-Fi or network.

**Works with:** LG TVs from **2020 or newer** (webOS 5 and above). Check on the TV under **Settings → All Settings → General → About This TV**.

> Vistaar is an unofficial client. Sign in only with your own Jio account.

---

## 1. On the TV (once)

1. Create a free LG developer account at <https://webostv.developer.lge.com> and confirm your email.
2. On the TV, open the **LG Content Store**, install **Developer Mode**, and open it. Sign in with your LG developer account.
3. Click **Dev Mode Status** to turn it **ON**. The TV restarts.
4. Open **Developer Mode** again and switch **Key Server** to **ON**.
5. Leave **Clear Cache Data** **OFF**. It erases the data of every app on the next restart, including Vistaar's sign-in and favourites.
6. Keep this screen in view. You need two things from it: the **IP Address** and the **Passphrase** (6 characters, capital letters matter).

   The screen lists the IP Address, the Passphrase, the Remain Session time, and the switches Dev Mode Status, Key Server, EXTEND and Clear Cache Data.

## 2. On the computer

1. Download **`Vistaar-webOS-Installer-0.4.0.zip`** and unzip it anywhere. Nothing is installed on your computer.
2. Double-click **`Install Vistaar.bat`**.
3. Type the TV's **IP address** and the **passphrase** when asked. Wait for **"Done!"**.

Vistaar now appears in the TV's app list. If something goes wrong, the installer says in plain words what to check (TV on, Key Server on, same network, no VPN, passphrase typed exactly).

The other files in the folder:

| File | Use |
|---|---|
| `Extend Developer Mode.bat` | Adds time to the Developer Mode session. Run it every few weeks. |
| `Remove Vistaar.bat` | Uninstalls Vistaar from the TV. |
| `README.txt` | The same steps on one page. |

To **update** Vistaar, run `Install Vistaar.bat` from the newer folder.

---

## Keep it working: Developer Mode time limits

These are the facts from LG's documentation and forum:

- The **Remain Session** line in the Developer Mode app shows the time left. The **maximum** session is **1000 hours** (about 41 days). Your TV may show less after a fresh start (for example about 100 hours), so press **EXTEND** to top it up.
- **Extend before it runs out.** If the time has already run out, it can no longer be extended: turn **Dev Mode Status** ON again and run the installer once more.
- **When the session runs out, apps installed this way are removed.** After a restart with no time left, Developer Mode switches off and Vistaar disappears.
- If the TV is **not connected to a network** and restarts **ten times**, Developer Mode is switched off too.
- You can extend from the TV (open **Developer Mode** and press **EXTEND**, with the TV online) or from the computer with `Extend Developer Mode.bat`.

## Using the app

- **First start:** pick the languages you watch, then sign in with your Jio number and the one-time code, or choose **Continue with free channels** (no account needed).
- **Remote:** arrows move, **OK** opens, **hold OK** saves a favourite, **Back** goes back, **CH ▲▼** change channel while watching.
- **Magic Remote pointer:** hover to highlight, click to open.
- To remove the app cleanly, open **Settings → Account → Sign out** first (this frees the TV on your Jio account), then run `Remove Vistaar.bat`.

## Troubleshooting

| Problem | What to try |
|---|---|
| The installer cannot pair with the TV | Check the IP and passphrase against the TV screen. The TV must be on, **Dev Mode Status** and **Key Server** both **ON**, and on the same network as the computer (turn off any VPN). If you switched Developer Mode off and on, the passphrase is new. |
| Developer Mode app not found in the Content Store | Search for "Developer Mode". If it is not offered, your TV's region or model may not have it. |
| Vistaar disappeared | The session ran out. Switch Dev Mode Status ON again and run `Install Vistaar.bat`. |
| Sign-in or favourites were lost | **Clear Cache Data** was ON, or Vistaar was removed. Keep it OFF. |
| A channel does not play | Some channels are off air or outside your Jio plan, and free channels go offline often. Try another. |
| "Device limit reached" when signing in | Choose **Log out other devices and sign in** on that screen. |
| A message about DRM | A few Jio channels use protected streaming that this version does not support yet. |
| No picture after signing in | Tell the developer your TV model and the webOS version from **About This TV**. |

## Reporting a problem

Send the developer your TV model, the webOS version, what you did and what you saw. For a playback issue, add the channel name and a photo of the error. Please do **not** send your Jio number, one-time codes or passwords.

---

## Without the installer (any computer)

1. Install **Node.js** (LTS) and run `npm install -g @webos-tools/cli`.
2. Replace `192.168.1.50` with the TV's IP address and run:

   ```bash
   ares-setup-device --add mytv --info "{'host':'192.168.1.50','port':'9922','username':'prisoner'}"
   ares-novacom --device mytv --getkey          # asks for the passphrase
   ares-install --device mytv com.vistaar.tv.webos_0.4.0_all.ipk
   ares-launch --device mytv com.vistaar.tv.webos
   ```

3. Extend later with `ares-launch com.palmdts.devmode -p extend=true -d mytv`, remove with `ares-install --device mytv --remove com.vistaar.tv.webos`.

Prefer a window instead of commands? **webOS Dev Manager** (free, open source) does the same: <https://github.com/webosbrew/dev-manager-desktop>.

## For the developer: build it

```bash
cd web && npm install && cd ../webos && npm install
npm run package                      # builds webos/out/com.vistaar.tv.webos_<version>_all.ipk
cd ../installer && node build.mjs    # builds installer/out/Vistaar-webOS-Installer-<version>.zip
```

The `.ipk` contains the app (`webos/app`) and a small helper service (`webos/services/com.vistaar.tv.webos.service`). The service fetches the 16-byte stream keys for encrypted Jio channels, because Jio's key server rejects the `Origin` header a web app always sends. It only talks to `*.jio.com` hosts.

The installer zip bundles the official Node.js `node.exe` (taken from the Node installed on the build machine), LG's `ares` tools and the `.ipk`, so a user needs nothing else.
