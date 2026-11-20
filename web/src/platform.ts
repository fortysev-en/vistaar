/** Thin platform layer: everything that differs between webOS (now) and Tizen (later) lives here. */
const SERVICE = 'luna://com.vistaar.tv.webos.service/'

export const Platform = {
  get isWebOS(): boolean { return /Web0S|webOS/i.test(navigator.userAgent) },
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

  /** Leave the app (Home screen back / Exit). */
  exit() {
    const w = window as any
    try { if (w.webOS && w.webOS.platformBack) return w.webOS.platformBack() } catch { /* fall through */ }
    try { if (w.webOSSystem && w.webOSSystem.close) return w.webOSSystem.close() } catch { /* fall through */ }
    window.close()
  },
}
