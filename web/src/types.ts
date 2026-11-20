/** A TV channel. Jio channels have a numeric id as a string and no url; free channels have id "f<hex>" and carry their own url/ua/ref. */
export interface Channel {
  id: string
  name: string
  order: number
  /** Jio language id (positive) or, for free channels, -(category index + 1). */
  lang: number
  hd: boolean
  logo: string
  url: string
  ua: string
  ref: string
  /** Jio genre id (0 for free channels). */
  cat: number
  /** Jio marks channels outside the base pack (`is_premium`); drawn with a crown. */
  premium: boolean
}

export interface Program { srno: number; title: string; showtime: string; start: number; end: number; desc: string }

export interface Play { url: string; dash: boolean; license: string }

export const isFree = (c: Channel) => c.url !== ''

/** Language ids as used by JioTV's channel list (same table as the Android app). */
export const LANGUAGES: Record<number, string> = {
  1: 'Hindi', 2: 'Marathi', 3: 'Punjabi', 4: 'Urdu', 5: 'Bengali', 6: 'English', 7: 'Malayalam', 8: 'Tamil', 9: 'Gujarati',
  10: 'Odia', 11: 'Telugu', 12: 'Bhojpuri', 13: 'Kannada', 14: 'Assamese', 15: 'Nepali', 16: 'French',
}

/** Genre ids (`channelCategoryId`) in display order. */
export const GENRES: Array<[number, string]> = [
  [5, 'Entertainment'], [6, 'Movies'], [12, 'News'], [13, 'Music'], [7, 'Kids'], [8, 'Sports'], [15, 'Devotional'],
  [17, 'Education'], [10, 'Knowledge'], [9, 'Lifestyle'], [16, 'Business'], [18, 'Shopping'],
]

/** ISO 639 codes of the Jio language ids, used to prefer the channel's own audio track. */
export const LANG_CODES: Record<number, string> = {
  1: 'hi', 2: 'mr', 3: 'pa', 4: 'ur', 5: 'bn', 6: 'en', 7: 'ml', 8: 'ta', 9: 'gu', 10: 'or', 11: 'te', 12: 'bho', 13: 'kn', 14: 'as', 15: 'ne', 16: 'fr',
}
