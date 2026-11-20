// Bundles src/main.ts into one classic script (webOS blocks ES modules under file://) for Chromium 68, and stages dist/.
import { build } from 'esbuild'
import { cpSync, mkdirSync, rmSync, readFileSync, writeFileSync } from 'node:fs'

rmSync('dist', { recursive: true, force: true }); mkdirSync('dist', { recursive: true })
const res = await build({
  entryPoints: ['src/main.ts'], bundle: true, format: 'iife', target: 'chrome68', outfile: 'dist/app.js',
  minify: process.argv.includes('--min'), sourcemap: false, logLevel: 'info', legalComments: 'none',
})
cpSync('src/index.html', 'dist/index.html')
cpSync('src/theme.css', 'dist/theme.css')
if (res.errors.length) process.exit(1)
