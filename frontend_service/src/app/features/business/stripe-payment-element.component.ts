import { AfterViewInit, Component, ElementRef, EventEmitter, Input, OnDestroy, Output, ViewChild } from '@angular/core';
import { loadStripe } from '@stripe/stripe-js/pure';
import type { Stripe, StripeElements, StripePaymentElement } from '@stripe/stripe-js';
import { CheckoutPreparationResponse } from '../../core/api';
import { TranslatePipe } from '../../core/i18n/translate.pipe';

@Component({
  selector: 'app-stripe-payment-element',
  imports: [TranslatePipe],
  templateUrl: './stripe-payment-element.component.html',
  styleUrl: './stripe-payment-element.component.scss',
})
export class StripePaymentElementComponent implements AfterViewInit, OnDestroy {
  @Input({ required: true }) preparation!: CheckoutPreparationResponse;
  @Input({ required: true }) returnUrl!: string;
  @Output() readonly confirmed = new EventEmitter<void>();
  @ViewChild('paymentElement', { static: true }) private readonly mountPoint!: ElementRef<HTMLDivElement>;

  loading = true;
  processing = false;
  errorMessage = '';
  private stripe: Stripe | null = null;
  private elements: StripeElements | null = null;
  private paymentElement: StripePaymentElement | null = null;
  private destroyed = false;

  async ngAfterViewInit(): Promise<void> {
    try {
      this.stripe = await loadStripe(this.preparation.publishableKey);
      if (!this.stripe || this.destroyed) {
        throw new Error('Stripe.js could not be initialized.');
      }
      this.elements = this.stripe.elements({
        clientSecret: this.preparation.clientSecret,
        appearance: {
          theme: 'stripe',
          variables: {
            colorPrimary: '#4f46e5',
            colorBackground: '#ffffff',
            colorText: '#0f172a',
            colorDanger: '#dc2626',
            colorTextSecondary: '#475569',
            borderRadius: '8px',
            fontFamily: 'Inter, system-ui, -apple-system, sans-serif',
            spacingUnit: '4px',
          },
        },
      });
      this.paymentElement = this.elements.create('payment', { layout: 'tabs' });
      this.paymentElement.mount(this.mountPoint.nativeElement);
      this.paymentElement.on('ready', () => this.loading = false);
      this.paymentElement.on('loaderror', (event) => {
        this.loading = false;
        this.errorMessage = event.error.message ?? 'The secure payment form could not be loaded.';
      });
    } catch (error) {
      this.loading = false;
      this.errorMessage = error instanceof Error ? error.message : 'The secure payment form could not be loaded.';
    }
  }

  async submit(): Promise<void> {
    if (!this.stripe || !this.elements || this.processing) return;
    this.processing = true;
    this.errorMessage = '';
    const result = await this.stripe.confirmPayment({
      elements: this.elements,
      confirmParams: { return_url: this.returnUrl },
      redirect: 'if_required',
    });
    this.processing = false;

    if (result.error) {
      this.errorMessage = result.error.message ?? 'Stripe could not confirm this payment.';
      return;
    }
    if (result.paymentIntent?.status === 'succeeded') {
      this.confirmed.emit();
      return;
    }
    this.errorMessage = 'Payment is still processing. No subscription change has been applied yet.';
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    this.paymentElement?.destroy();
  }
}
