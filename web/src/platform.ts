/** Thin platform layer: everything that differs between LG webOS, Samsung Tizen and a plain browser lives here. */
const SERVICE = 'luna://com.vistaar.tv.webos.service/'

export const Platform = {
  get isWebOS(): boolean { return /Web0S|webOS/i.test(navigator.userAgent) },
  get isTizen(): boolean { return /Tizen/i.test(navigator.userAgent) },
  /** Short name of the TV platform, for the About screen. */
  get name(): string { return this.isTizen ? 'Samsung Tizen' : this.isWebOS ? 'LG webOS' : 'Browser' },
  /** Chromium major version, e.g. 68 on webOS 5. */
  get chromium(): number { return parseInt((/Chrome\/(\d+)/.exec(navigator.userAgent) || [])[1] || '0', 10) },
  get hasService(): boolean { return typeof (window as any).PalmServiceBridge === 'function' },

  /** Call the helper JS service packaged with the app. */
  luna(method: string, params: unknown, timeoutMs = 20000): Promise<any> {
    return new Promise((resolve, reject) => {
      const B = (window as any).PalmServiceBridge
      if (!B) return reject(new Error('no service bridge'))
      const bridge = new B()
      const t = setTimeout(() => reject(new Error('service call timed out')), timeoutMs)
      bridge.onservicecallback = (msg: string) => { clearTimeout(t); try { resolve(JSON.parse(msg)) } catch (e) { reject(e) } }
      bridge.call(SERVICE + method, JSON.stringify(params))
    })
  },

  /** Tizen delivers only some remote keys unless the app asks for them (Back, arrows and OK always arrive). Call once at start. */
  init() {
    const t = (window as any).tizen
    if (!t || !t.tvinputdevice) return
    ;['ChannelUp', 'ChannelDown', 'MediaPlay', 'MediaPause', 'MediaPlayPause', 'MediaStop'].forEach((k) => { try { t.tvinputdevice.registerKey(k) } catch { /* not available on this model */ } })
  },

  /** Leave the app (Home screen back / Exit). */
  exit() {
    const w = window as any
    try { if (w.tizen && w.tizen.application) return w.tizen.application.getCurrentApplication().exit() } catch { /* fall through */ }
    try { if (w.webOS && w.webOS.platformBack) return w.webOS.platformBack() } catch { /* fall through */ }
    try { if (w.webOSSystem && w.webOSSystem.close) return w.webOSSystem.close() } catch { /* fall through */ }
    window.close()
  },
}
