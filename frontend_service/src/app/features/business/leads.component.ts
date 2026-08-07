import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  CrmApiService,
  INTERACTION_TYPES,
  InteractionType,
  LEAD_PRIORITIES,
  LeadPriority,
  LeadResponse,
  LeadStatus,
  PageResponse,
  SalesProductResponse,
  StockApiService,
  StoreResponse,
  UserResponse,
  UsersApiService,
} from '../../core/api';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { UiFeedbackService } from '../../core/ui/ui-feedback.service';

type LeadPanel = 'create' | 'detail' | 'assign' | 'interaction' | 'convert' | null;

@Component({
  selector: 'app-leads',
  imports: [FormsModule],
  templateUrl: './leads.component.html',
  styleUrl: './business-view.scss',
})
export class LeadsComponent implements OnInit {
  private readonly api = inject(CrmApiService);
  private readonly stockApi = inject(StockApiService);
  private readonly usersApi = inject(UsersApiService);
  readonly feedback = inject(UiFeedbackService);
  private readonly currentUser = inject(AuthSessionService).currentUser;

  readonly canCreateAndContact = this.currentUser()?.role === 'ROLE_CSM';
  readonly canAssign = this.currentUser()?.role === 'ROLE_ADMIN';
  readonly priorities = LEAD_PRIORITIES;
  readonly interactionTypes = INTERACTION_TYPES;
  readonly allFilterStatuses: readonly LeadStatus[] = [
    'NEW_LEAD', 'ATTEMPTED_CONTACT', 'IN_PROGRESS', 'SCHEDULED_RECALL',
    'UNREACHABLE', 'REFUSED', 'CONVERTED',
  ];

  readonly page = signal<PageResponse<LeadResponse> | null>(null);
  readonly selected = signal<LeadResponse | null>(null);
  readonly products = signal<readonly SalesProductResponse[]>([]);
  readonly locations = signal<readonly StoreResponse[]>([]);
  readonly csmAgents = signal<readonly UserResponse[]>([]);
  readonly loading = signal(true);

  panel: LeadPanel = null;
  statusFilter: LeadStatus | '' = '';
  agentFilter: number | null = null;
  busy = false;
  createForm = {
    ordrePriorite: 'MEDIUM' as LeadPriority,
    nomComplet: '',
    email: '',
    telephone: '',
    adresseLivraison: '',
    ville: '',
  };
  assignedAgentId: number | null = null;
  interactionForm = {
    typeInteraction: 'APPEL_TEL' as InteractionType,
    nouveauStatut: '' as LeadStatus | '',
    commentaireAgent: '',
  };
  orderForm = this.emptyOrderForm();

  ngOnInit(): void {
    this.load();
    if (this.canAssign) {
      this.usersApi.getEnterpriseStaff().subscribe({
        next: (staff) => this.csmAgents.set(
          staff.filter((user) => user.role === 'ROLE_CSM' && user.active),
        ),
        error: (error) => this.feedback.error(error, 'CSM staff could not be loaded.'),
      });
    }
    if (this.canCreateAndContact) {
      this.stockApi.getSalesCatalog().subscribe({
        next: (products) => this.products.set(products),
        error: (error) => this.feedback.error(error, 'Products could not be loaded.'),
      });
      this.stockApi.getStores().subscribe({
        next: (locations) => this.locations.set(locations),
        error: (error) => this.feedback.error(error, 'Fulfillment locations could not be loaded.'),
      });
    }
  }

  load(page = 0): void {
    this.loading.set(true);
    const agentId = this.canCreateAndContact ? this.currentUser()?.id : this.agentFilter ?? undefined;
    this.api.searchLeads(page, 20, this.statusFilter || undefined, agentId).subscribe({
      next: (value) => {
        this.page.set(value);
        this.loading.set(false);
      },
      error: (error) => {
        this.loading.set(false);
        this.feedback.error(error, 'Leads could not be loaded.');
      },
    });
  }

  loadAgentLeads(): void {
    this.load();
  }

  open(panel: LeadPanel, lead?: LeadResponse): void {
    this.selected.set(lead ?? null);
    this.panel = panel;
    if (panel === 'convert') this.orderForm = this.emptyOrderForm();
    if (panel === 'assign') this.assignedAgentId = lead?.agentId ?? null;
    if (panel === 'interaction') {
      this.interactionForm = {
        typeInteraction: 'APPEL_TEL',
        nouveauStatut: '',
        commentaireAgent: '',
      };
    }
    if (lead && panel === 'detail') {
      this.api.getLeadById(lead.idLead).subscribe({
        next: (value) => this.selected.set(value),
        error: (error) => this.feedback.error(error),
      });
    }
  }

  close(): void {
    this.panel = null;
    this.selected.set(null);
  }

  create(): void {
    const form = this.createForm;
    if (!form.nomComplet || !form.telephone || !form.adresseLivraison || !form.ville) return;
    this.busy = true;
    this.api.createLead({
      ordrePriorite: form.ordrePriorite,
      infosClient: {
        nomComplet: form.nomComplet,
        email: form.email,
        telephone: form.telephone,
        adresseLivraison: form.adresseLivraison,
        ville: form.ville,
      },
    }).subscribe({
      next: () => {
        this.busy = false;
        this.feedback.success('Lead created in the New lead stage.');
        this.close();
        this.load();
      },
      error: (error) => {
        this.busy = false;
        this.feedback.error(error);
      },
    });
  }

  assign(): void {
    const lead = this.selected();
    if (!lead || !this.assignedAgentId) return;
    this.busy = true;
    this.api.assignAgent(lead.idLead, this.assignedAgentId).subscribe({
      next: () => {
        this.busy = false;
        this.feedback.success('Lead assigned.');
        this.close();
        this.load();
      },
      error: (error) => {
        this.busy = false;
        this.feedback.error(error);
      },
    });
  }

  addInteraction(): void {
    const lead = this.selected();
    if (!lead || !this.interactionForm.commentaireAgent) return;
    this.busy = true;
    this.api.addInteraction(lead.idLead, {
      typeInteraction: this.interactionForm.typeInteraction,
      nouveauStatut: this.interactionForm.nouveauStatut || undefined,
      commentaireAgent: this.interactionForm.commentaireAgent,
    }).subscribe({
      next: () => {
        this.busy = false;
        this.feedback.success('Interaction recorded.');
        this.close();
        this.load();
      },
      error: (error) => {
        this.busy = false;
        this.feedback.error(error);
      },
    });
  }

  convert(): void {
    const lead = this.selected();
    const form = this.orderForm;
    if (!lead || lead.statutLead !== 'IN_PROGRESS' || !form.productId
      || !form.stockLocationId || form.quantity < 1) return;
    this.busy = true;
    this.api.convertToOrder(lead.idLead, {
      idempotencyKey: form.idempotencyKey,
      stockLocationId: form.stockLocationId,
      items: [{ productId: form.productId, quantity: form.quantity }],
    }).subscribe({
      next: (order) => {
        this.busy = false;
        this.feedback.success(`Order ${order.reference} created with catalog pricing and reserved stock.`);
        this.close();
        this.load();
      },
      error: (error) => {
        this.busy = false;
        this.feedback.error(error);
      },
    });
  }

  allowedLeadStatuses(lead = this.selected()): readonly LeadStatus[] {
    if (!lead) return [];
    switch (lead.statutLead) {
      case 'NEW_LEAD': return ['ATTEMPTED_CONTACT', 'IN_PROGRESS', 'SCHEDULED_RECALL', 'UNREACHABLE', 'REFUSED'];
      case 'ATTEMPTED_CONTACT': return ['IN_PROGRESS', 'SCHEDULED_RECALL', 'UNREACHABLE', 'REFUSED'];
      case 'IN_PROGRESS': return ['SCHEDULED_RECALL', 'UNREACHABLE', 'REFUSED'];
      case 'SCHEDULED_RECALL': return ['ATTEMPTED_CONTACT', 'IN_PROGRESS', 'UNREACHABLE', 'REFUSED'];
      case 'UNREACHABLE': return ['SCHEDULED_RECALL', 'ATTEMPTED_CONTACT', 'REFUSED'];
      default: return [];
    }
  }

  agentLabel(agentId: number): string {
    if (agentId === this.currentUser()?.id) return 'You';
    const agent = this.csmAgents().find((candidate) => candidate.id === agentId);
    return agent ? `${agent.firstname} ${agent.lastname}` : `#${agentId}`;
  }

  tone(status: LeadStatus): string {
    return status === 'CONVERTED' ? 'success'
      : status === 'REFUSED' || status === 'UNREACHABLE' ? 'critical'
        : status === 'IN_PROGRESS' || status === 'SCHEDULED_RECALL' ? 'info' : 'neutral';
  }

  label(value: string): string {
    return value.replaceAll('_', ' ').toLowerCase().replace(/^./, (character) => character.toUpperCase());
  }

  private emptyOrderForm() {
    return {
      idempotencyKey: globalThis.crypto.randomUUID(),
      stockLocationId: null as number | null,
      productId: null as number | null,
      quantity: 1,
    };
  }
}
