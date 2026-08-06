import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { CrmApiService, INTERACTION_TYPES, LEAD_PRIORITIES, LEAD_STATUSES, InteractionType, LeadPriority, LeadResponse, LeadStatus, PageResponse } from '../../core/api';
import { UiFeedbackService } from '../../core/ui/ui-feedback.service';
import { AuthSessionService } from '../../core/auth/auth-session.service';

type LeadPanel = 'create' | 'detail' | 'assign' | 'interaction' | 'convert' | null;

@Component({ selector: 'app-leads', imports: [FormsModule], templateUrl: './leads.component.html', styleUrl: './business-view.scss' })
export class LeadsComponent implements OnInit {
  private readonly api = inject(CrmApiService); readonly feedback = inject(UiFeedbackService);
  private readonly currentUser = inject(AuthSessionService).currentUser;
  readonly canCreateAndContact = this.currentUser()?.role === 'ROLE_CSM';
  readonly canAssign = this.currentUser()?.role === 'ROLE_ADMIN';
  readonly statuses = LEAD_STATUSES; readonly priorities = LEAD_PRIORITIES; readonly interactionTypes = INTERACTION_TYPES;
  readonly page = signal<PageResponse<LeadResponse> | null>(null); readonly loading = signal(true); readonly selected = signal<LeadResponse | null>(null);
  panel: LeadPanel = null; statusFilter: LeadStatus | '' = ''; agentFilter: number | null = null; busy = false;
  createForm = { ordrePriorite: 'MEDIUM' as LeadPriority, statutLead: 'NEW_LEAD' as LeadStatus, nomComplet: '', email: '', telephone: '', adresseLivraison: '', ville: '', boutiqueId: null as number | null };
  assignedAgentId: number | null = null;
  interactionForm = { typeInteraction: 'APPEL_TEL' as InteractionType, nouveauStatut: '' as LeadStatus | '', commentaireAgent: '' };
  orderForm = { totalAmount: 0, productId: null as number | null, quantity: 1, unitPrice: 0 };

  ngOnInit(): void { this.load(); }
  load(page = 0): void { this.loading.set(true); const agentId = this.canCreateAndContact ? this.currentUser()?.id : this.agentFilter ?? undefined; this.api.searchLeads(page, 20, this.statusFilter || undefined, agentId).subscribe({ next: (v) => { this.page.set(v); this.loading.set(false); }, error: (e) => { this.loading.set(false); this.feedback.error(e, 'Leads could not be loaded.'); } }); }
  loadAgentLeads(): void { if (!this.agentFilter) { this.load(); return; } this.loading.set(true); this.api.getLeadsByAgent(this.agentFilter).subscribe({ next: (items) => { this.page.set({ content: items, page: 0, size: items.length, totalElements: items.length, totalPages: 1, first: true, last: true }); this.loading.set(false); }, error: (e) => { this.loading.set(false); this.feedback.error(e); } }); }
  open(panel: LeadPanel, lead?: LeadResponse): void { this.selected.set(lead ?? null); this.panel = panel; if (lead && panel === 'detail') this.api.getLeadById(lead.idLead).subscribe({ next: (v) => this.selected.set(v), error: (e) => this.feedback.error(e) }); }
  close(): void { this.panel = null; this.selected.set(null); }
  create(): void { if (!this.createForm.nomComplet || !this.createForm.email || !this.createForm.telephone) return; this.busy = true; this.api.createLead({ statutLead: this.createForm.statutLead, ordrePriorite: this.createForm.ordrePriorite, infosClient: { nomComplet: this.createForm.nomComplet, email: this.createForm.email, telephone: this.createForm.telephone, adresseLivraison: this.createForm.adresseLivraison, ville: this.createForm.ville }, boutiqueId: this.createForm.boutiqueId ?? undefined }).subscribe({ next: () => { this.busy = false; this.feedback.success('Lead created.'); this.close(); this.load(); }, error: (e) => { this.busy = false; this.feedback.error(e); } }); }
  assign(): void { const lead = this.selected(); if (!lead || !this.assignedAgentId) return; this.busy = true; this.api.assignAgent(lead.idLead, this.assignedAgentId).subscribe({ next: () => { this.busy = false; this.feedback.success('Lead assigned.'); this.close(); this.load(); }, error: (e) => { this.busy = false; this.feedback.error(e); } }); }
  addInteraction(): void { const lead = this.selected(); if (!lead || !this.interactionForm.commentaireAgent) return; this.busy = true; this.api.addInteraction(lead.idLead, { typeInteraction: this.interactionForm.typeInteraction, nouveauStatut: this.interactionForm.nouveauStatut || undefined, commentaireAgent: this.interactionForm.commentaireAgent }).subscribe({ next: () => { this.busy = false; this.feedback.success('Interaction recorded.'); this.close(); this.load(); }, error: (e) => { this.busy = false; this.feedback.error(e); } }); }
  convert(): void { const lead = this.selected(); const f = this.orderForm; if (!lead || !f.productId || f.quantity < 1) return; this.busy = true; this.api.convertToOrder(lead.idLead, { totalAmount: f.totalAmount, items: [{ productId: f.productId, quantity: f.quantity, unitPrice: f.unitPrice }] }).subscribe({ next: (order) => { this.busy = false; this.feedback.success(`Order ${order.reference} created.`); this.close(); this.load(); }, error: (e) => { this.busy = false; this.feedback.error(e); } }); }
  tone(status: LeadStatus): string { return status === 'CONVERTED' ? 'success' : status === 'REFUSED' || status === 'UNREACHABLE' ? 'critical' : status === 'IN_PROGRESS' || status === 'SCHEDULED_RECALL' ? 'info' : 'neutral'; }
  label(value: string): string { return value.replaceAll('_', ' ').toLowerCase().replace(/^./, (c) => c.toUpperCase()); }
}
