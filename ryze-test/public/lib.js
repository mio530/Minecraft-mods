/* Shared, testable dashboard helpers (UMD: browser global `RyzeLib` + CommonJS). */
(function (root, factory) {
  var lib = factory();
  if (typeof module === 'object' && module && module.exports) module.exports = lib;
  if (root) root.RyzeLib = lib;
})(typeof self !== 'undefined' ? self : typeof globalThis !== 'undefined' ? globalThis : undefined, function () {
  'use strict';

  /** Build fetch options. Only send a JSON content-type when there IS a body
   *  (an empty body with application/json is rejected by the server). */
  function buildFetchOptions(opts, token) {
    opts = opts || {};
    var headers = { authorization: 'Bearer ' + token };
    var body = opts.body;
    if (body !== undefined && body !== null && typeof body !== 'string') body = JSON.stringify(body);
    if (body !== undefined && body !== null) headers['content-type'] = 'application/json';
    var extra = opts.headers || {};
    for (var k in extra) if (Object.prototype.hasOwnProperty.call(extra, k)) headers[k] = extra[k];
    var out = { method: opts.method || 'GET', headers: headers };
    if (body !== undefined && body !== null) out.body = body;
    return out;
  }

  /** Human-readable error text from a failed API response body. */
  function apiErrorMessage(status, body) {
    if (body && typeof body === 'object') {
      if (body.message) return body.message;
      if (body.error) return body.error;
    }
    if (typeof body === 'string' && body.trim()) return body.trim().slice(0, 200);
    return 'HTTP ' + status;
  }

  /** Decide what the Microsoft-login banner shows for a given auth status. */
  function msViewModel(s, now) {
    now = now || Date.now();
    if (!s) return { visible: true, button: { label: 'Login starten', disabled: false }, code: null, error: null };
    if (s.authenticated) return { visible: false, button: null, code: null, error: null };
    var vm = { visible: true, code: null, error: null, button: { label: 'Login starten', disabled: false } };
    if (s.pending && s.pending.userCode) {
      var secs = Math.max(0, Math.round((s.pending.expiresAt - now) / 1000));
      vm.code = {
        userCode: s.pending.userCode,
        verificationUri: s.pending.verificationUri,
        directUri: s.pending.directUri,
        expiresInText: secs > 0 ? 'Code gültig noch ' + Math.floor(secs / 60) + ':' + String(secs % 60).padStart(2, '0') + ' min' : 'Code abgelaufen – neu starten',
      };
      vm.button = { label: 'Warte auf Anmeldung…', disabled: true };
    } else if (s.inProgress) {
      vm.button = { label: 'Code wird angefordert…', disabled: true };
    }
    if (s.error && !s.inProgress) {
      vm.error = 'Microsoft-Login fehlgeschlagen: ' + s.error;
      vm.button = { label: 'Erneut versuchen', disabled: false };
    }
    return vm;
  }

  /** Copy text. Uses the async Clipboard API when available (HTTPS only),
   *  else falls back to a selected input + execCommand (works on plain-HTTP
   *  Safari/iPad). Resolves true on success, false if the user must copy
   *  manually. */
  function copyText(text, doc, nav) {
    doc = doc || (typeof document !== 'undefined' ? document : null);
    nav = nav || (typeof navigator !== 'undefined' ? navigator : null);
    if (nav && nav.clipboard && typeof nav.clipboard.writeText === 'function') {
      return nav.clipboard.writeText(text).then(function () { return true; }, function () { return fallback(); });
    }
    return Promise.resolve(fallback());

    function fallback() {
      if (!doc || !doc.body) return false;
      var input = doc.createElement('input');
      input.value = text;
      input.setAttribute('readonly', '');
      input.style.position = 'fixed';
      input.style.opacity = '0';
      input.style.fontSize = '16px'; // avoid iOS zoom
      doc.body.appendChild(input);
      try {
        input.focus();
        input.select();
        if (input.setSelectionRange) input.setSelectionRange(0, text.length);
        return !!(doc.execCommand && doc.execCommand('copy'));
      } catch (e) {
        return false;
      } finally {
        doc.body.removeChild(input);
      }
    }
  }

  return { buildFetchOptions: buildFetchOptions, apiErrorMessage: apiErrorMessage, msViewModel: msViewModel, copyText: copyText };
});
