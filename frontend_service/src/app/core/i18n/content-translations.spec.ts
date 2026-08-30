import { translateContentText } from './content-translations';

describe('operational content translations', () => {
  it('covers every major business workspace in French and Arabic', () => {
    const phrases = [
      'Stock operations',
      'Needs my confirmation',
      'Lead pipeline',
      'My deliveries',
      'External stores',
      'Billing & invoices',
      'Organization subscription',
      'Team & access',
      'Notifications',
      'Platform control',
    ];

    for (const phrase of phrases) {
      expect(translateContentText(phrase, 'fr')).withContext(`French: ${phrase}`).toBeTruthy();
      expect(translateContentText(phrase, 'ar')).withContext(`Arabic: ${phrase}`).toBeTruthy();
      expect(translateContentText(phrase, 'ar')).withContext(`Arabic differs: ${phrase}`).not.toBe(phrase);
    }
  });

  it('normalizes formatted template whitespace', () => {
    expect(translateContentText('Stock   operations', 'fr')).toBe('Opérations de stock');
  });
});
