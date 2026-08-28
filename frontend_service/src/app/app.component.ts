import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { I18nService } from './core/i18n/i18n.service';
import { LocalizeContentDirective } from './core/i18n/localize-content.directive';

@Component({
  selector: 'app-root',
  hostDirectives: [LocalizeContentDirective],
  imports: [RouterOutlet],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent {
  private readonly i18n = inject(I18nService);
  readonly title = 'IntelliOps';
}
