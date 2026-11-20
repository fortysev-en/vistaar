import * as Api from '../api'
import { h, icon, brandMark } from '../dom'
import { focusFirst, setFocus } from '../nav/focus'
import { Store } from '../store'
import { App, type Screen } from './app'
import { artBg } from './widgets'

type Step = 'phone' | 'sending' | 'otp' | 'verifying' | 'limit' | 'expiring' | 'done'

/** Sign in with a mobile number and a one-time code; or continue with the free channels (no Jio account). Port of LoginActivity. */
export function loginScreen(params?: { add?: boolean }): Screen {
  const adding = !!(params && params.add)
  let step: Step = 'phone'
  let phone = '', otp = '', error = '', resendIn = 0
  let timer = 0

  const left = h('div', { class: 'lleft' })
  const card = h('div', { class: 'card2' })
  const el = h('div', { class: 'screen' },
    h('div', { class: 'bg-art', style: { background: artBg('login') } }), h('div', { class: 'bg-shade' }),
    h('div', { class: 'lbrand' }, brandMark(40), 'Vistaar'), left, card)

  const fmt = (p: string) => (p.length > 5 ? p.slice(0, 5) + ' ' + p.slice(5) : p)
  const readable = (e: string) => e.replace(/\s+/g, ' ').slice(0, 110)

  function key(label: string, onPress: () => void, small = false) {
    return h('div', { class: 'key' + (small ? ' sm' : ''), f: true, 'data-key': label, onclick: onPress }, label === 'del' ? icon('backspace', 32) : label === 'clear' ? 'Clear' : label)
  }
  function keypad() {
    const ks = ['1', '2', '3', '4', '5', '6', '7', '8', '9', 'del', '0', 'clear']
    return h('div', { class: 'keypad' }, ks.map((k) => key(k, () => press(k), k === 'del' || k === 'clear')))
  }

  function press(k: string) {
    if (step !== 'phone' && step !== 'otp') return
    const isPhone = step === 'phone'; const max = isPhone ? 10 : 6
    let v = isPhone ? phone : otp
    if (k === 'del') v = v.slice(0, -1); else if (k === 'clear') v = ''; else if (/^\d$/.test(k) && v.length < max) v += k
    if (isPhone) phone = v; else otp = v
    error = ''; render()
    if (v.length === max) focusFirst('[data-go]')
  }

  async function sendOtp() {
    if (phone.length !== 10) return
    if ('6789'.indexOf(phone[0]) < 0) { error = 'Indian mobile numbers start with 6, 7, 8 or 9'; render(); return }
    step = 'sending'; error = ''; render()
    const err = await Api.sendOtp(phone).catch((e: Error) => 'Network error: ' + e.message)
    if (err) { step = 'phone'; error = readable(err) } else { step = 'otp'; otp = ''; startTimer() }
    render(); focusFirst('[data-key="5"]')
  }

  async function verify() {
    if (otp.length !== 6) return
    step = 'verifying'; error = ''; render()
    const err = await Api.verifyOtp(phone, otp).catch((e: Error) => 'Network error: ' + e.message)
    if (err === Api.DEVICE_LIMIT) { step = 'limit'; error = ''; otp = '' }
    else if (err) { step = 'otp'; error = readable(err); otp = '' }
    else { step = 'done'; clearInterval(timer) }
    render(); focusFirst(step === 'done' ? '[data-start]' : step === 'limit' ? '[data-go]' : '[data-key="5"]')
  }

  async function logOutOthers() {
    step = 'expiring'; error = ''; render()
    const err = await Api.expireAllUsers().catch((e: Error) => 'Network error: ' + e.message)
    if (err) { error = readable(err); step = 'limit' } else { step = 'done'; clearInterval(timer) }
    render(); focusFirst(step === 'done' ? '[data-start]' : '[data-go]')
  }

  function startTimer() { resendIn = 30; clearInterval(timer); timer = window.setInterval(() => { if (resendIn > 0) { resendIn--; render(true) } else clearInterval(timer) }, 1000) }
  function resend() { error = ''; otp = ''; startTimer(); render(); Api.sendOtp(phone).catch(() => {}) }
  function changeNumber() { clearInterval(timer); step = 'phone'; otp = ''; error = ''; render(); focusFirst('[data-key="5"]') }
  function cancelLimit() { step = 'phone'; otp = ''; error = ''; render(); focusFirst('[data-key="5"]') }
  function done() { if (Store.loggedIn) Store.freeOnly = false; App.home() }

  function freeBtn() {
    return h('div', { class: 'bigbtn sec', f: true, onclick: () => { Store.freeOnly = true; if (adding) App.go('settings'); else App.home() } }, 'Continue with free channels')
  }

  function render(timerOnly = false) {
    const onPhone = step === 'phone' || step === 'sending', onOtp = step === 'otp' || step === 'verifying', onLimit = step === 'limit' || step === 'expiring'
    const busy = step === 'sending' || step === 'verifying'
    const hadFocus = (document.querySelector('.card2 .focus') as HTMLElement | null)
    const focusKey = hadFocus ? hadFocus.getAttribute('data-key') : null
    left.innerHTML = ''
    left.appendChild(h('div', { class: 'step' }, step === 'done' ? 'All set' : onLimit ? 'Device limit' : onPhone ? 'Step 1 of 2' : 'Step 2 of 2'))
    left.appendChild(h('h1', null, onPhone ? 'Sign in with your mobile' : onOtp ? 'Enter your code' : onLimit ? 'Too many devices' : 'Welcome to Vistaar'))
    left.appendChild(h('div', { class: 'lead' }, onPhone ? 'Use the number linked to your subscription. We’ll text you a one-time code.' : onOtp ? 'Check your phone for a 6-digit code. It expires in 10 minutes.' : onLimit ? 'Your Jio account is already signed in on the maximum number of devices.' : 'Your channels, guide and favourites are ready.'))
    left.appendChild(h('div', { class: 'dots' }, h('i', { class: 'on' }), h('i', { class: onPhone ? '' : 'on' })))

    card.innerHTML = ''
    if (onPhone) {
      const ready = !busy && phone.length === 10
      card.appendChild(h('div', { class: 'lbl' }, 'Mobile number'))
      card.appendChild(h('div', { class: 'field' + (error ? ' err' : phone.length < 10 ? ' act' : '') }, h('div', { class: 'cc' }, '+91'), h('div', { class: 'val' + (phone ? '' : ' ph') }, phone ? fmt(phone) : '98765 43210')))
      card.appendChild(h('div', { class: 'help' + (error ? ' err' : '') }, error || (phone.length === 10 ? 'Looks good. Press OK to get your code.' : `${10 - phone.length} digits to go`)))
      card.appendChild(keypad())
      card.appendChild(h('div', { class: 'bigbtn ' + (ready ? 'go' : 'off'), f: ready, 'data-go': true, onclick: () => ready && sendOtp() }, step === 'sending' ? 'Sending code…' : 'Send OTP'))
      card.appendChild(freeBtn())
      card.appendChild(h('div', { class: 'lfoot' }, 'Standard SMS rates may apply. We never share your number.'))
    } else if (onOtp) {
      const ready = !busy && otp.length === 6
      card.appendChild(h('div', { style: { display: 'flex', alignItems: 'center' } }, h('div', { class: 'lbl', style: { flex: '1' } }, 'Code sent to +91 ' + fmt(phone)), h('div', { class: 'btn small', f: true, onclick: changeNumber }, 'Change')))
      const boxes = h('div', { class: 'otpr' })
      for (let i = 0; i < 6; i++) boxes.appendChild(h('div', { class: 'otpb' + (i === otp.length && step === 'otp' ? ' act' : i < otp.length ? ' f' : '') }, otp[i] || ''))
      card.appendChild(boxes)
      card.appendChild(h('div', { class: 'help' + (error ? ' err' : ''), style: { display: 'flex', alignItems: 'center' } },
        h('span', { style: { flex: '1' } }, error || (otp.length === 6 ? 'Press OK to sign in.' : 'Didn’t get it? Check the number above.')),
        resendIn === 0 ? h('div', { class: 'btn small ghost', f: true, onclick: resend }, 'Resend code') : h('span', { class: 'mono' }, 'Resend in 0:' + ('0' + resendIn).slice(-2))))
      card.appendChild(keypad())
      card.appendChild(h('div', { class: 'bigbtn ' + (ready ? 'go' : 'off'), f: ready, 'data-go': true, onclick: () => ready && verify() }, step === 'verifying' ? 'Verifying…' : 'Log in'))
      card.appendChild(h('div', { class: 'lfoot' }, 'Signing in on this TV keeps you signed in until you sign out in Settings.'))
    } else if (onLimit) {
      card.appendChild(h('div', { class: 'center' },
        h('div', { class: 'wicon', style: { background: 'rgba(242,181,68,.16)', color: 'var(--amber)' } }, icon('warning', 56)),
        h('h2', null, 'Device limit reached'),
        h('p', { style: error ? { color: 'var(--amber)' } : {} }, error || 'Log out the other devices to sign in here. They will need to sign in again; your plan and favourites are not affected.'),
        h('div', { class: 'bigbtn go', f: step === 'limit', 'data-go': true, style: { width: '640px' }, onclick: () => step === 'limit' && logOutOthers() }, step === 'expiring' ? 'Logging out…' : 'Log out other devices and sign in'),
        h('div', { class: 'bigbtn sec', f: step === 'limit', style: { width: '640px' }, onclick: () => step === 'limit' && cancelLimit() }, 'Cancel')))
    } else {
      card.appendChild(h('div', { class: 'center' },
        h('div', { class: 'wicon', style: { background: 'rgba(90,169,255,.16)', color: 'var(--accent)' } }, icon('check', 60)),
        h('h2', null, 'You’re signed in'),
        h('p', null, '+91 ' + fmt(phone) + ' · this TV stays signed in'),
        h('div', { class: 'bigbtn go', f: true, 'data-start': true, style: { width: '420px' }, onclick: done }, 'Start watching')))
    }
    if (focusKey) { const k = card.querySelector('[data-key="' + focusKey + '"]') as HTMLElement | null; if (k) setFocus(k) }
    else if (timerOnly) { /* keep focus */ }
  }

  render()
  return {
    el,
    mount() { focusFirst('[data-key="5"]') },
    unmount() { clearInterval(timer) },
    onBack() {
      if (step === 'expiring') return true
      if (step === 'limit') { cancelLimit(); return true }
      if (step === 'otp') { changeNumber(); return true }
      if (adding) { App.go('settings'); return true }
      return true // on the sign-in screen Back does nothing (the TV's Home button exits)
    },
    onKey(a) {
      if (a.type === 'digit') { press(a.digit); return true }
      if (a.type === 'del') { if (step === 'phone' || step === 'otp') { press('del'); return true } }
      return false
    },
  }
}
