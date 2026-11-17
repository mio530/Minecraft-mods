import { describe, expect, it } from 'vitest';
import { createRequire } from 'node:module';

// public/lib.js is a UMD browser script; load it through CommonJS.
const require = createRequire(import.meta.url);
const loaded = require('../../public/lib.js');
const Lib = loaded && loaded.msViewModel ? loaded : (globalThis as any).RyzeLib;

describe('buildFetchOptions', () => {
  it('does NOT send a JSON content-type when there is no body (the iPad bug)', () => {
    const o = Lib.buildFetchOptions({ method: 'POST' }, 'tok');
    expect(o.headers['content-type']).toBeUndefined();
    expect(o.body).toBeUndefined();
    expect(o.headers.authorization).toBe('Bearer tok');
  });
  it('sends JSON content-type with a body (string or object)', () => {
    expect(Lib.buildFetchOptions({ method: 'POST', body: '{"a":1}' }, 't').headers['content-type']).toBe('application/json');
    const o = Lib.buildFetchOptions({ method: 'POST', body: { a: 1 } }, 't');
    expect(o.body).toBe('{"a":1}');
  });
});

describe('apiErrorMessage', () => {
  it('prefers server message, then error, then HTTP status', () => {
    expect(Lib.apiErrorMessage(400, { message: 'Body cannot be empty' })).toBe('Body cannot be empty');
    expect(Lib.apiErrorMessage(409, { error: 'no active session' })).toBe('no active session');
    expect(Lib.apiErrorMessage(502, null)).toBe('HTTP 502');
  });
});

describe('msViewModel', () => {
  const now = 1_000_000;
  it('hides the banner when authenticated', () => {
    expect(Lib.msViewModel({ authenticated: true }, now).visible).toBe(false);
  });
  it('shows a loading state while the code is being requested', () => {
    const vm = Lib.msViewModel({ authenticated: false, inProgress: true, pending: null }, now);
    expect(vm.button).toEqual({ label: 'Code wird angefordert…', disabled: true });
    expect(vm.code).toBeNull();
  });
  it('shows the code, links and expiry while waiting for the user', () => {
    const vm = Lib.msViewModel(
      { authenticated: false, inProgress: true, pending: { userCode: 'AB-12', verificationUri: 'https://www.microsoft.com/link', directUri: 'https://www.microsoft.com/link?otc=AB-12', expiresAt: now + 125_000 } },
      now,
    );
    expect(vm.code.userCode).toBe('AB-12');
    expect(vm.code.directUri).toContain('otc=AB-12');
    expect(vm.code.expiresInText).toBe('Code gültig noch 2:05 min');
    expect(vm.button.disabled).toBe(true);
  });
  it('shows an error and re-enables the button after a failure', () => {
    const vm = Lib.msViewModel({ authenticated: false, inProgress: false, error: 'network down' }, now);
    expect(vm.error).toBe('Microsoft-Login fehlgeschlagen: network down');
    expect(vm.button).toEqual({ label: 'Erneut versuchen', disabled: false });
  });
});

describe('copyText', () => {
  it('uses the Clipboard API when available (HTTPS)', async () => {
    let copied = '';
    const nav = { clipboard: { writeText: async (t: string) => { copied = t; } } };
    expect(await Lib.copyText('AB-12', null, nav)).toBe(true);
    expect(copied).toBe('AB-12');
  });

  function fakeDoc(execResult: boolean) {
    const children: any[] = [];
    const doc: any = {
      body: { appendChild: (n: any) => children.push(n), removeChild: (n: any) => children.splice(children.indexOf(n), 1) },
      createElement: () => ({ style: {}, setAttribute() {}, focus() {}, select() {}, setSelectionRange() {}, value: '' }),
      execCommand: (cmd: string) => cmd === 'copy' && execResult,
      children,
    };
    return doc;
  }

  it('falls back to execCommand on plain-HTTP Safari (no navigator.clipboard)', async () => {
    const doc = fakeDoc(true);
    expect(await Lib.copyText('AB-12', doc, {})).toBe(true);
    expect(doc.children.length).toBe(0); // temp input cleaned up
  });

  it('reports failure (so the UI can ask for manual copy) when nothing works', async () => {
    expect(await Lib.copyText('AB-12', fakeDoc(false), {})).toBe(false);
  });
});
