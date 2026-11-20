// Builds the webOS compatibility probe (src/probe) into ../webos-probe/app as one classic script for Chromium 68.
import { build } from 'esbuild'
import { cpSync } from 'node:fs'
await build({ entryPoints: ['src/probe/main.ts'], bundle: true, format: 'iife', target: 'chrome68', outfile: '../webos-probe/app/app.js', minify: true, logLevel: 'info', legalComments: 'none' })
cpSync('src/probe/probe.html', '../webos-probe/app/index.html')
