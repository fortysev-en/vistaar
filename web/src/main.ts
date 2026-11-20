import { App } from './ui/app'
import { browserScreen } from './ui/browser'
import { favouritesScreen } from './ui/favourites'
import { guideScreen } from './ui/guide'
import { homeScreen } from './ui/home'
import { languageScreen } from './ui/language'
import { loginScreen } from './ui/login'
import { playerScreen } from './ui/player'
import { playlistScreen } from './ui/playlist'
import { searchScreen } from './ui/search'
import { settingsScreen } from './ui/settings'

App.register('login', loginScreen)
App.register('language', languageScreen)
App.register('home', () => homeScreen())
App.register('jiotv', () => browserScreen('jio'))
App.register('freetv', () => browserScreen('free'))
App.register('hd', () => browserScreen('hd'))
App.register('guide', guideScreen)
App.register('favs', () => favouritesScreen())
App.register('search', () => searchScreen())
App.register('settings', settingsScreen)
App.register('playlist', () => playlistScreen())
App.register('player', playerScreen)

import * as Api from './api'
import { Catalog } from './catalog'
import { resolve } from './playback'
import { Free } from './free'
import { logoBox } from './ui/widgets'
;(window as any).__v = { Api, Catalog, Free, logoBox, resolve } // debugging hook (used by the emulator test scripts)

App.start()

// Fit the 1920x1080 canvas to the window (a no-op on a 1080p TV; handy when testing on a PC).
function fit() {
  const app = document.getElementById('app')!
  const k = Math.min(window.innerWidth / 1920, window.innerHeight / 1080)
  app.style.transformOrigin = '0 0'; app.style.transform = k === 1 ? '' : 'scale(' + k + ')'
}
fit(); window.addEventListener('resize', fit)
