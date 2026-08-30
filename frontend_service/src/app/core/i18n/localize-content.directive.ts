import { afterNextRender, Directive, effect, ElementRef, inject, OnDestroy } from '@angular/core';
import { I18nService } from './i18n.service';
import type { AppLanguage } from './i18n.service';

@Directive({ selector: '[localizeContent]', standalone: true })
export class LocalizeContentDirective implements OnDestroy {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly i18n = inject(I18nService);
  private readonly originals = new WeakMap<Text, string>();
  private readonly attributeOriginals = new WeakMap<Element, Map<string, string>>();
  private translator?: (text: string, language: AppLanguage) => string | undefined;
  private observer?: MutationObserver;

  constructor() {
    void import('./content-translations').then(module => {
      this.translator = module.translateContentText;
      this.translateTree();
    });
    effect(() => { this.i18n.language(); queueMicrotask(() => this.translateTree()); });
    afterNextRender(() => {
      this.translateTree();
      this.observer = new MutationObserver(() => this.translateTree());
      this.observer.observe(this.host, { childList: true, subtree: true, characterData: true });
    });
  }

  ngOnDestroy(): void { this.observer?.disconnect(); }

  private translateTree(): void {
    if (!this.translator) return;
    const walker = document.createTreeWalker(this.host, NodeFilter.SHOW_TEXT);
    let node = walker.nextNode() as Text | null;
    while (node) {
      const parent = node.parentElement;
      if (parent && !parent.closest('[data-no-translate]')) this.translateNode(node);
      node = walker.nextNode() as Text | null;
    }
    this.host.querySelectorAll<Element>('[placeholder],[title],[aria-label]')
      .forEach((element: Element) => this.translateAttributes(element));
  }

  private translateNode(node: Text): void {
    const translator = this.translator;
    if (!translator) return;
    const source = this.originals.get(node) ?? node.data;
    this.originals.set(node, source);
    const trimmed = source.trim();
    const translated = translator(trimmed, this.i18n.language());
    if (!translated) return;
    const leading = source.match(/^\s*/)?.[0] ?? '';
    const trailing = source.match(/\s*$/)?.[0] ?? '';
    const next = `${leading}${translated}${trailing}`;
    if (node.data !== next) node.data = next;
  }

  private translateAttributes(element: Element): void {
    const originals = this.attributeOriginals.get(element) ?? new Map<string, string>();
    this.attributeOriginals.set(element, originals);
    for (const name of ['placeholder', 'title', 'aria-label']) {
      const current = element.getAttribute(name);
      if (!current) continue;
      if (!originals.has(name)) originals.set(name, current);
      const source = originals.get(name)!;
      element.setAttribute(name, this.translator?.(source, this.i18n.language()) ?? source);
    }
  }
}
