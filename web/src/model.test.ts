import { describe, expect, it } from 'vitest';
import { dateDay, formatMinor, safeJournal } from './model';
describe('cloud projection', () => {
  it('preserves money above the JS safe integer limit', () => {
    expect(formatMinor('9007199254740993', 'USD')).toContain('90.071.992.547.409,93');
    expect(formatMinor('-101', 'USD')).toBe('−1,01 USD');
    expect(formatMinor('101', 'JPY')).toBe('101 JPY');
  });
  it('uses UTC day boundaries independently of the PC timezone', () => {
    expect(dateDay('1970-01-01')).toBe(0);
    expect(dateDay('2026-10-06')).toBe(20732);
  });
  it('removes scripts, event handlers, remote images and unsafe links from journals', () => {
    const html = safeJournal('<p onclick="alert(1)">Hello <strong>world</strong></p><script>alert(1)</script><img src="https://tracker.test"><a href="javascript:alert(1)">link</a>');
    expect(html).toContain('<strong>world</strong>');
    expect(html).not.toMatch(/script|onclick|<img|href|javascript/);
  });
});
