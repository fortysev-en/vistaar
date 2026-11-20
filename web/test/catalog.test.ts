import { describe, expect, it } from 'vitest'
import { Catalog } from '../src/catalog'
import { Free } from '../src/free'
import type { Channel } from '../src/types'

const ch = (id: string, name: string, extra: Partial<Channel> = {}): Channel =>
  ({ id, name, order: 0, lang: 1, hd: false, logo: '', url: '', ua: '', ref: '', cat: 0, premium: false, ...extra })
const free = (id: string, name: string) => ch(id, name, { url: 'https://x/' + id + '.m3u8' })

describe('twinOf (free channel to the same channel on the Jio plan)', () => {
  it('matches names across spacing, "(India)" and HD', () => {
    Catalog.all = [ch('1132', 'Star Plus HD', { premium: true }), ch('931', 'Star Bharat HD')]
    Catalog.playable = new Set(['1132', '931'])
    Free.channels = [free('fa', 'StarPlus (India) HD'), free('fb', 'Star Bharat (India) HD'), free('fc', 'Sony Sports Ten 3 Hindi'), free('fd', '&TV')]
    expect(Catalog.twinOf('fa')).toBe('1132')
    expect(Catalog.twinOf('fb')).toBe('931')
    expect(Catalog.twinOf('fc')).toBeNull()
    expect(Catalog.twinOf('fd')).toBeNull()
  })
  it('ignores Jio channels the plan does not include, and picks up new ones', () => {
    Catalog.all = [ch('1132', 'Star Plus HD')]
    Catalog.playable = new Set()
    Free.channels = [free('fa', 'StarPlus (India) HD')]
    expect(Catalog.twinOf('fa')).toBeNull()
    Catalog.playable.add('1132')
    expect(Catalog.twinOf('fa')).toBe('1132')
  })
})
