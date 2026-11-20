import type { Dir } from './spatial'

/** What a remote (or PC keyboard) key means to the app. webOS key codes: Back 461, Ch+ 427, Ch- 428, colour keys 403-406. */
export type Action =
  | { type: 'dir'; dir: Dir }
  | { type: 'ok' }
  | { type: 'back' }
  | { type: 'chUp' }
  | { type: 'chDown' }
  | { type: 'digit'; digit: string }
  | { type: 'char'; ch: string }
  | { type: 'del' }
  | { type: 'play' }
  | { type: 'pause' }

export function keyAction(code: number): Action | null {
  switch (code) {
    case 37: return { type: 'dir', dir: 'left' }
    case 38: return { type: 'dir', dir: 'up' }
    case 39: return { type: 'dir', dir: 'right' }
    case 40: return { type: 'dir', dir: 'down' }
    case 13: return { type: 'ok' }
    case 461: case 27: case 10009: return { type: 'back' } // webOS Back, Esc, Tizen Back
    case 8: return { type: 'del' } // Backspace edits text; the app also treats it as Back where nothing is being typed
    case 427: case 33: return { type: 'chUp' } // webOS Ch+, PageUp on a PC
    case 428: case 34: return { type: 'chDown' }
    case 415: return { type: 'play' }
    case 19: return { type: 'pause' }
  }
  if (code >= 65 && code <= 90) return { type: 'char', ch: String.fromCharCode(code + 32) } // PC keyboard / USB keyboard
  if (code === 32) return { type: 'char', ch: ' ' }
  if (code >= 48 && code <= 57) return { type: 'digit', digit: String(code - 48) }
  if (code >= 96 && code <= 105) return { type: 'digit', digit: String(code - 96) }
  return null
}
