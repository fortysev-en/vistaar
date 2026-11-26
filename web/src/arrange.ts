/** [items] in the saved [order]; anything the order doesn't know keeps its default place after the known ones. */
export function arrange<T>(items: T[], order: string[], id: (t: T) => string): T[] {
  if (!order.length) return items
  const rank: { [k: string]: number } = {}
  order.forEach((k, i) => { rank[k] = i })
  const known = items.filter((t) => id(t) in rank).sort((a, b) => rank[id(a)] - rank[id(b)])
  return known.concat(items.filter((t) => !(id(t) in rank)))
}
