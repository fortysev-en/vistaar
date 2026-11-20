import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { parseM3u } from '../src/free'

const india = readFileSync(new URL('./in.m3u', import.meta.url), 'utf8')
const tamil = readFileSync(new URL('./tam.m3u', import.meta.url), 'utf8')

describe('parseM3u', () => {
  it('parses the India list into channels and categories', () => {
    const p = parseM3u(india)!
    expect(p.channels.length).toBeGreaterThan(500)
    expect(p.cats.length).toBeGreaterThan(5)
    expect(p.channels.every((c) => c.url.startsWith('http') && !c.url.includes('youtube'))).toBe(true)
  })
  it('strips quality and tags from names and flags HD', () => {
    const p = parseM3u('#EXTM3U\n#EXTINF:-1 tvg-id="A.in@SD" group-title="News",Chan A (1080p) [Not 24/7]\nhttps://x/a.m3u8\n#EXTINF:-1 tvg-id="B.in@SD" group-title="News",Chan B (480p)\nhttps://x/b.m3u8\n')!
    expect(p.channels.map((c) => c.name)).toEqual(['Chan A', 'Chan B'])
    expect(p.channels.map((c) => c.hd)).toEqual([true, false])
  })
  it('keeps per-channel user agent and referrer, tolerating a repeated key', () => {
    const p = parseM3u('#EXTM3U\n#EXTINF:-1 tvg-id="A" group-title="X",A\n#EXTVLCOPT:http-user-agent=http-user-agent=Foo/1.0\n#EXTVLCOPT:http-referrer=https://r.example/\nhttps://x/a.m3u8\n')!
    expect(p.channels[0].ua).toBe('Foo/1.0')
    expect(p.channels[0].ref).toBe('https://r.example/')
  })
  it('ids are stable, unique and shared with the per-language lists', () => {
    const a = parseM3u(india)!, b = parseM3u(india)!
    expect(a.channels[0].id).toBe(b.channels[0].id)
    expect(new Set(a.channels.map((c) => c.id)).size).toBe(a.channels.length)
    const ids = new Set(a.channels.map((c) => c.id))
    const t = parseM3u(tamil)!
    expect(t.channels.filter((c) => ids.has(c.id)).length).toBeGreaterThan(30) // same hashing as the main list
  })
  it('returns null for an empty or unplayable list', () => {
    expect(parseM3u('#EXTM3U\n')).toBeNull()
    expect(parseM3u('#EXTM3U\n#EXTINF:-1,Yt\nhttps://www.youtube.com/watch?v=1\n')).toBeNull()
  })
})
