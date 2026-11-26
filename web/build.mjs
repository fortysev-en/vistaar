// Bundles src/main.ts into one classic script (webOS blocks ES modules under file://) for Chromium 68, and stages dist/.
import { build } from 'esbuild'
import { cpSync, mkdirSync, rmSync, readFileSync, writeFileSync } from 'node:fs'

// --tizen builds for Samsung TVs from 2020 (Tizen 5.5, Chromium 69; BigInt in free.ts needs 67+) into dist-tizen; the default is LG webOS 5+ (Chromium 68) into dist.
const tizen = process.argv.includes('--tizen')
const out = tizen ? 'dist-tizen' : 'dist'
rmSync(out, { recursive: true, force: true }); mkdirSync(out, { recursive: true })
const res = await build({
  entryPoints: ['src/main.ts'], bundle: true, format: 'iife', target: tizen ? 'chrome67' : 'chrome68', outfile: out + '/app.js',
  minify: process.argv.includes('--min'), sourcemap: false, logLevel: 'info', legalComments: 'none',
})
cpSync('src/index.html', out + '/index.html')
cpSync('src/theme.css', out + '/theme.css')
if (res.errors.length) process.exit(1)
