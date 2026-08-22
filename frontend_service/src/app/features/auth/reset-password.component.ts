import {Component,inject} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {ActivatedRoute,RouterLink} from '@angular/router';
import {finalize} from 'rxjs';
import {AuthApiService} from '../../core/api';
@Component({selector:'app-reset-password',imports:[FormsModule,RouterLink],templateUrl:'./reset-password.component.html',styleUrl:'./login.component.scss'})
export class ResetPasswordComponent { private api=inject(AuthApiService); token=inject(ActivatedRoute).snapshot.queryParamMap.get('token')||'';password='';confirm='';busy=false;message='';error='';submit(){if(!this.token||this.password.length<8||this.password!==this.confirm){this.error='The link must be valid and both passwords must match (minimum 8 characters).';return}this.busy=true;this.api.resetPassword(this.token,this.password,this.confirm).pipe(finalize(()=>this.busy=false)).subscribe({next:r=>this.message=r.message,error:()=>this.error='This reset link is invalid or expired.'});}}
