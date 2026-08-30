import { Component, inject } from '@angular/core';
import { AppLanguage, I18nService } from './i18n.service';
import { TranslatePipe } from './translate.pipe';

@Component({
  selector: 'app-language-switcher', imports: [TranslatePipe],
  template: `<label class="language-switcher"><span class="sr-only">{{'language.label'|t}}</span><select [value]="i18n.language()" (change)="change($event)" [attr.aria-label]="'language.label'|t"><option value="en">EN</option><option value="fr">FR</option><option value="ar">AR</option></select></label>`,
  styles: [`.language-switcher select{height:34px;padding:0 9px;color:var(--text-secondary);background:var(--bg-surface);border:1px solid var(--border-color);border-radius:8px;font-size:11px;font-weight:700;cursor:pointer}`],
})
export class LanguageSwitcherComponent {
  readonly i18n = inject(I18nService);
  change(event: Event): void { this.i18n.setLanguage((event.target as HTMLSelectElement).value as AppLanguage); }
}
