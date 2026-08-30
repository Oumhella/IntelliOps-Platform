import {Component,inject} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {RouterLink} from '@angular/router';
import {finalize} from 'rxjs';
import {AuthApiService} from '../../core/api';
import {LanguageSwitcherComponent} from '../../core/i18n/language-switcher.component';
import {TranslatePipe} from '../../core/i18n/translate.pipe';
@Component({selector:'app-forgot-password',imports:[FormsModule,RouterLink,LanguageSwitcherComponent,TranslatePipe],templateUrl:'./forgot-password.component.html',styleUrl:'./login.component.scss'})
export class ForgotPasswordComponent { private api=inject(AuthApiService); email=''; busy=false; message=''; error=''; submit(){if(!this.email.trim())return;this.busy=true;this.api.forgotPassword(this.email.trim()).pipe(finalize(()=>this.busy=false)).subscribe({next:r=>this.message=r.message,error:()=>this.error='Unable to process the request. Please try again.'});}}
