// Stages the Tizen build (../web/dist-tizen) with config.xml and the icon into app/, then zips it to out/Vistaar-Tizen-<version>-unsigned.wgt.
// A TV only installs a package signed with a certificate that lists the TV's DUID, so the .wgt made here is unsigned: sign it with the
// Tizen CLI (see ../docs/tizen-install.md). If the `tizen` command is on PATH and TIZEN_PROFILE names a security profile, this also signs it.
import { cpSync, existsSync, mkdirSync, readFileSync, rmSync, statSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import { join, resolve } from 'node:path'

if (!existsSync('../web/dist-tizen/app.js')) { console.error('Run "npm run package" (it builds the web app with --tizen first)'); process.exit(1) }
const version = JSON.parse(readFileSync('package.json', 'utf8')).version
const app = resolve('app'), out = resolve('out')
rmSync(app, { recursive: true, force: true }); rmSync(out, { recursive: true, force: true })
mkdirSync(app, { recursive: true }); mkdirSync(out, { recursive: true })
for (const f of ['index.html', 'app.js', 'theme.css']) cpSync('../web/dist-tizen/' + f, join(app, f))
cpSync('config.xml', join(app, 'config.xml')); cpSync('icon.png', join(app, 'icon.png'))

const wgt = join(out, `Vistaar-Tizen-${version}-unsigned.wgt`)
execFileSync('python', ['-c', `
import os, sys, zipfile
src, dst = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(dst, 'w', zipfile.ZIP_DEFLATED) as z:
    for root, _, files in os.walk(src):
        for f in sorted(files):
            p = os.path.join(root, f)
            z.write(p, os.path.relpath(p, src))
`, app, wgt], { stdio: 'inherit' })
console.log('built', wgt, (statSync(wgt).size / 1024).toFixed(0) + ' KB (unsigned)')

if (process.env.TIZEN_PROFILE) {
  try { execFileSync('tizen', ['package', '-t', 'wgt', '-s', process.env.TIZEN_PROFILE, '-o', out, '--', app], { stdio: 'inherit', shell: true }) }
  catch { console.error('Signing failed: is the `tizen` command on PATH and the profile name right?') }
}
