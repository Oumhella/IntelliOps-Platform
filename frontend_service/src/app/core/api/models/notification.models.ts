import { IsoDateTime } from './common.models';

export const NOTIFICATION_TYPES = ['EMAIL', 'SMS', 'PUSH', 'WHATSAPP'] as const;
export type NotificationType = (typeof NOTIFICATION_TYPES)[number];

export const NOTIFICATION_STATUSES = ['QUEUED', 'SENT', 'DELIVERED', 'FAILED'] as const;
export type NotificationStatus = (typeof NOTIFICATION_STATUSES)[number];

export interface NotificationRequest {
  readonly type: NotificationType;
  readonly recipientContact: string;
  readonly subject?: string;
  readonly contenu: string;
}

export interface NotificationResponse {
  readonly idNotification: number;
  readonly type: NotificationType;
  readonly recipientContact: string;
  readonly contenu: string;
  readonly subject: string | null;
  readonly statut: NotificationStatus;
  readonly createdAt: IsoDateTime;
  readonly sentAt: IsoDateTime | null;
  readonly errorMessage: string | null;
}
