import { describe, expect, it } from 'vitest'
import { arrange } from '../src/ui/reorder'

const id = (s: string) => s
describe('arrange', () => {
  it('keeps the default when nothing is saved', () => expect(arrange(['a', 'b', 'c'], [], id)).toEqual(['a', 'b', 'c']))
  it('uses the saved order', () => expect(arrange(['a', 'b', 'c'], ['c', 'a', 'b'], id)).toEqual(['c', 'a', 'b']))
  it('puts unknown items after the known ones in default order', () => expect(arrange(['b', 'd', 'c'], ['c'], id)).toEqual(['c', 'b', 'd']))
  it('ignores saved items that no longer exist', () => expect(arrange(['a', 'b'], ['x', 'b', 'a'], id)).toEqual(['b', 'a']))
})
