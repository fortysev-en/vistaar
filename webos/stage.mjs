// Copies the built web core (../web/dist) next to appinfo.json so ares-package can bundle it.
import { cpSync, existsSync } from 'node:fs'
if (!existsSync('../web/dist/app.js')) { console.error('Run "npm run build" in ../web first'); process.exit(1) }
for (const f of ['index.html', 'app.js', 'theme.css']) cpSync('../web/dist/' + f, 'app/' + f)
