// Helper service for the Vistaar webOS test. It fetches a URL from a Jio host and returns the (small) body base64-encoded.
// Why a service: Jio's key server rejects requests carrying a browser Origin header, which web code cannot remove.
var Service = require('webos-service');
var https = require('https');
var url = require('url');

var service = new Service('com.vistaar.tv.webos.service');

function allowed(host) { return /(^|\.)jio\.com$/i.test(host || ''); } // not an open proxy: Jio hosts only

service.register('ping', function (message) {
  message.respond({ returnValue: true, node: process.version, platform: process.platform });
});

service.register('fetchBytes', function (message) {
  var a = message.payload || {};
  var done = false;
  function reply(o) { if (!done) { done = true; message.respond(o); } }
  try {
    var u = url.parse(String(a.url || ''));
    if (u.protocol !== 'https:' || !allowed(u.hostname)) { return reply({ returnValue: false, errorText: 'host not allowed' }); }
    var headers = {};
    var src = a.headers || {};
    Object.keys(src).forEach(function (k) { if (k.toLowerCase() !== 'origin' && k.toLowerCase() !== 'host') { headers[k] = String(src[k]); } });
    headers['User-Agent'] = 'plaYtv/7.1.8 (Linux;Android 11) ExoPlayerLib/1.3.1';
    var req = https.request({ hostname: u.hostname, port: u.port || 443, path: u.path, method: 'GET', headers: headers }, function (res) {
      var chunks = []; var size = 0;
      res.on('data', function (c) { size += c.length; if (size <= 65536) { chunks.push(c); } });
      res.on('end', function () {
        var body = Buffer.concat(chunks);
        reply({ returnValue: true, status: res.statusCode, length: size, base64: size <= 65536 ? body.toString('base64') : '' });
      });
    });
    req.on('error', function (e) { reply({ returnValue: false, errorText: String(e && e.message) }); });
    req.setTimeout(10000, function () { req.abort(); reply({ returnValue: false, errorText: 'timeout' }); });
    req.end();
  } catch (e) {
    reply({ returnValue: false, errorText: String(e && e.message) });
  }
});
