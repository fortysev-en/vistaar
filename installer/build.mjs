// Builds installer/out/Vistaar-webOS-Installer-<version>.zip (the version is the app's, from ../webos/package.json): the template + a portable Node + LG's ares tools + the newest .ipk from ../webos/out.
// Run from the installer folder: node build.mjs   (needs `npm run package` in ../webos first, and `npm install` there for the tools)
import { cpSync, existsSync, mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync, statSync } from 'node:fs'
import { execFileSync } from 'node:child_process'
import { join, resolve } from 'node:path'

const here = resolve('.')
const stage = join(here, 'out', 'Vistaar-webOS')
const version = JSON.parse(readFileSync(resolve('..', 'webos', 'package.json'), 'utf8')).version
const zip = join(here, 'out', `Vistaar-webOS-Installer-${version}.zip`)
const cliSrc = resolve('..', 'webos', 'node_modules', '@webos-tools', 'cli')
const outDir = resolve('..', 'webos', 'out')

if (!existsSync(cliSrc)) throw new Error('Run "npm install" in ../webos first (the LG tools are needed).')
const ipks = existsSync(outDir) ? readdirSync(outDir).filter((f) => f.endsWith('.ipk')).sort() : []
if (!ipks.length) throw new Error('No .ipk found. Run "npm run package" in ../webos first.')

rmSync(join(here, 'out'), { recursive: true, force: true })
mkdirSync(stage, { recursive: true })
cpSync(join(here, 'template'), stage, { recursive: true })

// portable Node (the same official build that runs this script)
mkdirSync(join(stage, 'tools'), { recursive: true })
cpSync(process.execPath, join(stage, 'tools', 'node.exe'))

// LG's command-line tools, without the project templates and tests (only needed to create new projects)
cpSync(cliSrc, join(stage, 'tools', 'cli'), { recursive: true, filter: (p) => !/[\\/]cli[\\/](files[\\/]templates|spec)([\\/]|$)/.test(p) })

mkdirSync(join(stage, 'app'), { recursive: true })
cpSync(join(outDir, ipks[ipks.length - 1]), join(stage, 'app', ipks[ipks.length - 1]))
writeFileSync(join(stage, 'tools', 'NODE-LICENSE.txt'), 'tools/node.exe is the official, unmodified Node.js build (https://nodejs.org), MIT licence: https://github.com/nodejs/node/blob/main/LICENSE\n')

// zip (Python's zipfile: Windows' tar.exe crashes on this many files)
execFileSync('python', ['-c', `
import os, sys, zipfile
src, out = sys.argv[1], sys.argv[2]
base = os.path.dirname(src)
with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as z:
    for root, _, files in os.walk(src):
        for f in files:
            p = os.path.join(root, f)
            z.write(p, os.path.relpath(p, base))
`, stage, zip], { stdio: 'inherit' })
console.log('built', zip, (statSync(zip).size / 1048576).toFixed(1) + ' MB', '| app:', ipks[ipks.length - 1])
