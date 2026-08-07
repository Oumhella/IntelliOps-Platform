import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CrmApiService, ORDER_STATUSES, OrderResponse, OrderStatus, PageResponse, SalesProductResponse, StockApiService } from '../../core/api';
import { UiFeedbackService } from '../../core/ui/ui-feedback.service';
import { AuthSessionService } from '../../core/auth/auth-session.service';

@Component({ selector:'app-orders', imports:[FormsModule], templateUrl:'./orders.component.html', styleUrl:'./business-view.scss' })
export class OrdersComponent implements OnInit {
  private readonly api=inject(CrmApiService); readonly feedback=inject(UiFeedbackService); readonly statuses=ORDER_STATUSES;
  private readonly stockApi=inject(StockApiService);
  private readonly role=inject(AuthSessionService).currentUser()?.role;
  readonly canEditLines=this.role==='ROLE_CSM';
  readonly canChangeStatus=this.role==='ROLE_CSM'||this.role==='ROLE_LOGISTIC';
  readonly page=signal<PageResponse<OrderResponse>|null>(null); readonly selected=signal<OrderResponse|null>(null); readonly loading=signal(true);
  readonly products=signal<readonly SalesProductResponse[]>([]);
  statusFilter:OrderStatus|''=''; panel:'detail'|'line'|'status'|null=null; busy=false;
  lineForm={productId:null as number|null,quantity:1}; newStatus:OrderStatus='EN_ATTENTE'; lookupId:number|null=null;
  ngOnInit():void{this.load();if(this.canEditLines)this.stockApi.getSalesCatalog().subscribe({next:v=>this.products.set(v),error:e=>this.feedback.error(e,'Products could not be loaded.')});}
  load(page=0):void{this.loading.set(true);this.api.searchOrders(page,20,this.statusFilter||undefined).subscribe({next:v=>{this.page.set(v);this.loading.set(false)},error:e=>{this.loading.set(false);this.feedback.error(e,'Orders could not be loaded.')}})}
  lookup():void{if(!this.lookupId)return;this.api.getOrderById(this.lookupId).subscribe({next:v=>{this.selected.set(v);this.panel='detail'},error:e=>this.feedback.error(e)});}
  open(panel:'detail'|'line'|'status',order:OrderResponse):void{this.selected.set(order);this.panel=panel;if(panel==='status')this.newStatus=this.allowedStatuses(order)[0]??order.statutCommande;if(panel==='detail')this.api.getOrderById(order.idCommande).subscribe({next:v=>this.selected.set(v),error:e=>this.feedback.error(e)});}
  close():void{this.panel=null;this.selected.set(null)}
  addLine():void{const o=this.selected(),f=this.lineForm;if(!o||!f.productId)return;this.busy=true;this.api.addProductToOrder(o.idCommande,f.productId,f.quantity).subscribe({next:()=>{this.busy=false;this.feedback.success('Product added at the current catalog price with stock reserved.');this.close();this.load()},error:e=>{this.busy=false;this.feedback.error(e)}})}
  changeStatus():void{const o=this.selected();if(!o)return;this.busy=true;this.api.changeOrderStatus(o.idCommande,this.newStatus).subscribe({next:()=>{this.busy=false;this.feedback.success('Order status updated.');this.close();this.load()},error:e=>{this.busy=false;this.feedback.error(e)}})}
  allowedStatuses(order:OrderResponse):readonly OrderStatus[]{if(this.role==='ROLE_CSM'){return order.statutCommande==='EN_ATTENTE'?['CONFIRMEE','ANNULEE']:order.statutCommande==='CONFIRMEE'||order.statutCommande==='PREPARATION'?['ANNULEE']:[]}if(this.role==='ROLE_LOGISTIC'){switch(order.statutCommande){case'CONFIRMEE':return['PREPARATION','ANNULEE'];case'PREPARATION':return['EXPEDIEE','ANNULEE'];case'EXPEDIEE':return['LIVREE','RETOURNEE'];case'LIVREE':return['RETOURNEE'];default:return[]}}return[]}
  label(v:string):string{return v.replaceAll('_',' ').toLowerCase().replace(/^./,c=>c.toUpperCase())} tone(v:OrderStatus):string{return v==='LIVREE'?'success':v==='ANNULEE'||v==='RETOURNEE'?'critical':v==='EXPEDIEE'||v==='PREPARATION'?'info':'warning'}
}
