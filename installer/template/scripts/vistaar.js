'use strict'
// Vistaar webOS installer. Usage: node vistaar.js install | extend | remove
// Everything it needs (Node, LG's command-line tools, the .ipk) is inside this folder; nothing is installed on the computer.
const fs = require('fs')
const os = require('os')
const path = require('path')
const readline = require('readline')
const { spawnSync } = require('child_process')

const ROOT = path.resolve(__dirname, '..')
const CLI = path.join(ROOT, 'tools', 'cli', 'bin')
const APP_ID = 'com.vistaar.tv.webos'
const DEVICE = process.env.VISTAAR_DEVICE || 'vistaar-tv' // VISTAAR_DEVICE=emulator is for testing without a TV
const SETTINGS = path.join(os.homedir(), '.vistaar-installer.json')

const say = (s) => console.log(s)
const step = (n, t, s) => say(`\n[${n}/${t}] ${s}`)

// One shared reader, so several questions work both at a keyboard and with piped input.
const rl = readline.createInterface({ input: process.stdin })
const queued = []; const waiting = []
rl.on('line', (l) => { if (waiting.length) waiting.shift()(l); else queued.push(l) })
rl.on('close', () => { waiting.splice(0).forEach((w) => w('')) })
function ask(q, def) {
  process.stdout.write(q + (def ? ` [${def}]` : '') + ': ')
  return new Promise((res) => {
    const done = (a) => res((a || '').trim() || def || '')
    if (queued.length) done(queued.shift()); else waiting.push(done)
  })
}
function loadSettings() { try { return JSON.parse(fs.readFileSync(SETTINGS, 'utf8')) } catch { return {} } }
function saveSettings(s) { try { fs.writeFileSync(SETTINGS, JSON.stringify(s)) } catch { /* not important */ } }

/** Runs one of LG's ares tools and returns { ok, out }. */
function ares(tool, args, timeoutMs) {
  const r = spawnSync(process.execPath, [path.join(CLI, 'ares-' + tool + '.js')].concat(args), { encoding: 'utf8', timeout: timeoutMs || 90000, cwd: ROOT, windowsHide: true })
  const out = ((r.stdout || '') + (r.stderr || '')).replace(/\x1b\[[0-9;]*m/g, '').trim()
  return { ok: r.status === 0 && !/ERR!/.test(out), out }
}

/** Plain-language explanation for the usual failures. */
function explain(out) {
  const t = out.toLowerCase()
  if (/timed out|etimedout|ehostunreach|enetunreach|econnrefused|enotfound|handshake/.test(t))
    return 'The TV did not answer.\n  - Is the TV on, with the Developer Mode app open?\n  - Are "Dev Mode Status" and "Key Server" both ON?\n  - Are the TV and this computer on the same Wi-Fi or network? (Turn off any VPN.)\n  - Is the IP address exactly as shown in the Developer Mode app?'
  if (/passphrase|authentication|permission denied|key|ssh/.test(t))
    return 'The TV did not accept the passphrase.\n  - Type it exactly as shown in the Developer Mode app (capital letters matter).\n  - If you switched Developer Mode off and on, it shows a new passphrase.'
  if (/no space|disk/.test(t)) return 'The TV is out of storage. Free some space and try again.'
  return null
}

function fail(what, out) {
  say(`\nSomething went wrong while ${what}.`)
  const e = explain(out)
  if (e) say(e)
  const lines = out.split('\n').filter(Boolean).slice(-4).join('\n  ')
  if (lines) say('\nDetails:\n  ' + lines)
  process.exitCode = 1
}

function ipk() {
  const f = fs.readdirSync(path.join(ROOT, 'app')).filter((x) => /\.ipk$/i.test(x))[0]
  if (!f) { say('The Vistaar app file (.ipk) is missing from the "app" folder.'); process.exit(1) }
  return path.join(ROOT, 'app', f)
}

async function connect() {
  if (DEVICE === 'emulator') return true
  const s = loadSettings()
  say('Open the "Developer Mode" app on your TV. It shows the TV\'s IP address and a passphrase.\n')
  let ip = ''
  for (;;) {
    ip = await ask('TV IP address (for example 192.168.1.50)', s.ip)
    if (/^(\d{1,3}\.){3}\d{1,3}$/.test(ip) && ip.split('.').every((n) => Number(n) <= 255)) break
    say('  That does not look like an IP address. It is four numbers separated by dots.')
  }
  let pass = ''
  for (;;) {
    pass = await ask('Passphrase from the Developer Mode app (6 characters, capitals matter)')
    if (/^[A-Za-z0-9]{6}$/.test(pass)) break
    say('  The passphrase is 6 letters and digits. Please check the TV screen.')
  }
  saveSettings({ ip })

  step(1, 4, 'Adding your TV...')
  ares('setup-device', ['--remove', DEVICE], 20000) // fine if it did not exist
  let r = ares('setup-device', ['--add', DEVICE, '--info', `{'host':'${ip}','port':'9922','username':'prisoner'}`], 20000)
  if (!r.ok) { fail('adding the TV', r.out); return false }

  step(2, 4, 'Pairing with the TV (this can take a few seconds)...')
  r = ares('novacom', ['--device', DEVICE, '--getkey', '--passphrase', pass], 60000)
  if (!r.ok) {
    if (/failed to get ssh private key/i.test(r.out)) { // LG's tool gives this one message for a wrong IP and a wrong passphrase
      say('\nCould not pair with the TV. Please check both of these:')
      say("  1. The IP address and passphrase are exactly as shown in the TV's Developer Mode app (capital letters matter).")
      say('  2. The TV is on, "Dev Mode Status" and "Key Server" are both ON, and the TV and this computer are on the same Wi-Fi/network (no VPN).')
      process.exitCode = 1
    } else fail('pairing with the TV', r.out)
    return false
  }
  return true
}

async function install() {
  say('=== Vistaar for LG webOS: installer ===\n')
  if (!(await connect())) return
  const total = DEVICE === 'emulator' ? 2 : 4
  const base = DEVICE === 'emulator' ? 0 : 2
  let r = ares('device', ['-i', '-d', DEVICE], 40000)
  if (!r.ok) { fail('reading the TV details', r.out); return }
  const model = (/modelName\s*:\s*(.+)/.exec(r.out) || [])[1] || 'LG TV'
  const sdk = (/sdkVersion\s*:\s*(\d+)/.exec(r.out) || [])[1]
  say(`  Found: ${model.trim()}${sdk ? ' (webOS ' + sdk + ')' : ''}`)
  if (sdk && Number(sdk) < 5) say('  Note: Vistaar needs webOS 5 or newer (TVs from 2020). It may not run on this TV.')

  step(base + 1, total, 'Installing Vistaar on the TV...')
  r = ares('install', ['-d', DEVICE, ipk()], 180000)
  if (!r.ok) { fail('installing the app', r.out); return }

  step(base + 2, total, 'Opening Vistaar...')
  r = ares('launch', ['-d', DEVICE, APP_ID], 40000)
  if (!r.ok) { fail('opening the app', r.out); return }
  say('\nDone! Vistaar is installed. Look for "Vistaar" in your TV\'s app list.')
  say('\nIMPORTANT: Developer Mode runs out after a while. Double-click "Extend Developer Mode" every few weeks')
  say('(or press EXTEND in the TV\'s Developer Mode app), otherwise the app can disappear.')
  say('Also keep "Clear Cache Data" switched OFF in the Developer Mode app: it erases Vistaar\'s sign-in and favourites.')
}

async function extend() {
  say('=== Extend Developer Mode on your TV ===\n')
  const have = ares('setup-device', ['--list'], 20000).out.indexOf(DEVICE) >= 0
  if (!have && !(await connect())) return
  const r = ares('launch', ['-d', DEVICE, 'com.palmdts.devmode', '-p', 'extend=true'], 40000)
  if (r.ok) say('\nDone. Developer Mode was extended. (Open the Developer Mode app on the TV to see the time left.)')
  else {
    fail('extending Developer Mode', r.out)
    say('\nYou can also do it on the TV: open the "Developer Mode" app and press EXTEND.')
    say('If the session already ran out, it cannot be extended: switch Dev Mode Status ON again and re-run the installer.')
  }
}

async function remove() {
  say('=== Remove Vistaar from your TV ===\n')
  say('Tip: on the TV, open Vistaar > Settings > Account > Sign out first, so the TV stops counting towards Jio\'s device limit.\n')
  const have = ares('setup-device', ['--list'], 20000).out.indexOf(DEVICE) >= 0
  if (!have && !(await connect())) return
  const r = ares('install', ['-d', DEVICE, '--remove', APP_ID], 90000)
  if (r.ok) say('\nVistaar was removed.'); else fail('removing the app', r.out)
}

const cmd = process.argv[2]
const run = { install, extend, remove }[cmd]
if (!run) { say('Usage: vistaar.js install | extend | remove'); process.exit(1) }
run().catch((e) => { say('Unexpected problem: ' + (e && e.message)); process.exitCode = 1 }).then(() => rl.close())
