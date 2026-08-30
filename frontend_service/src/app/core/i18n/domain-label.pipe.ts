import { inject, Pipe, PipeTransform } from '@angular/core';
import { AppLanguage, I18nService } from './i18n.service';

const LABELS: Record<AppLanguage, Record<string, string>> = {
  en: {
    ROLE_SUPER_ADMIN:'Super administrator', ROLE_ADMIN:'Administrator', ROLE_CSM:'Customer success agent', ROLE_LOGISTIC:'Logistics agent', ROLE_LIVREUR:'Courier',
    SUPER_ADMIN:'Super administrator', ADMIN:'Administrator', CSM:'Customer success agent', LOGISTIC:'Logistics agent', LIVREUR:'Courier',
    NEW_LEAD:'New lead', ATTEMPTED_CONTACT:'Contact attempted', IN_PROGRESS:'In progress', SCHEDULED_RECALL:'Recall scheduled', UNREACHABLE:'Unreachable', REFUSED:'Refused', CONVERTED:'Converted',
    IMMEDIATE:'Immediate', HIGH:'High', MEDIUM:'Medium', LOW:'Low', MANUAL:'Manual', EXTERNAL_API:'External API', IMPORT:'Import', APPEL_TEL:'Phone call', WHATSAPP:'WhatsApp', EMAIL_AUTO:'Automated email',
    EN_ATTENTE:'Pending', CONFIRMEE:'Confirmed', PREPARATION:'Preparation', EXPEDIEE:'Shipped', LIVREE:'Delivered', ANNULEE:'Cancelled', RETOURNEE:'Returned',
    UNPAID:'Unpaid', AWAITING_COLLECTION:'Awaiting collection', PAID:'Paid', PARTIALLY_REFUNDED:'Partially refunded', REFUNDED:'Refunded',
    ASSIGNEE:'Assigned', ACCEPTEE:'Accepted', EN_PREPARATION:'In preparation', CHEZ_TRANSPORTEUR:'With carrier', EN_COURS:'In transit', ECHEC:'Failed', RETOUR_DEMANDE:'Return requested', RETOUR:'Returned',
    CLIENT_ABSENT:'Customer absent', ADRESSE_INCORRECTE:'Incorrect address', CLIENT_REFUSE:'Customer refused', PROBLEME_PAIEMENT:'Payment problem', COLIS_ENDOMMAGE:'Damaged parcel', AUTRE:'Other',
    SOCIETE_LIVRAISON:'Delivery company', LIVREUR_INTERNE:'Internal courier',
    PENDING:'Pending', AUTHORIZED:'Authorized', COMPLETED:'Completed', FAILED:'Failed', CANCELLED:'Cancelled',
    COMMANDE_PRODUCT:'Product order', ABONNEMENT_PLATFORM:'Platform subscription', CASH_ON_DELIVERY:'Cash on delivery', CREDIT_CARD:'Credit card',
    ACTIF:'Active', DESACTIVE:'Disabled', SUPPRIME:'Deleted', EXPIRE:'Expired', SUSPENDU:'Suspended', ANNULATION_EN_COURS:'Cancellation pending', ANNULE:'Cancelled', ECHEC_REMBOURSEMENT:'Refund failed',
    HEBDOMADAIRE:'Weekly', MENSUEL:'Monthly', TRIMESTRIEL:'Quarterly', ANNUEL:'Yearly',
    QUEUED:'Queued', SENT:'Sent', DELIVERED:'Delivered', EMAIL:'Email', SMS:'SMS', PUSH:'Push notification',
    REASSORT:'Restock', PERTE:'Loss', AJUSTEMENT:'Adjustment',
    CONNECTED:'Connected', DISCONNECTED:'Disconnected', ACTION_REQUIRED:'Action required', PROCESSED:'Processed', IGNORED:'Ignored',
  },
  fr: {
    ROLE_SUPER_ADMIN:'Super-administrateur', ROLE_ADMIN:'Administrateur', ROLE_CSM:'Agent de la relation client', ROLE_LOGISTIC:'Agent logistique', ROLE_LIVREUR:'Livreur',
    SUPER_ADMIN:'Super-administrateur', ADMIN:'Administrateur', CSM:'Agent de la relation client', LOGISTIC:'Agent logistique', LIVREUR:'Livreur',
    NEW_LEAD:'Nouveau prospect', ATTEMPTED_CONTACT:'Contact tenté', IN_PROGRESS:'En cours', SCHEDULED_RECALL:'Rappel planifié', UNREACHABLE:'Injoignable', REFUSED:'Refusé', CONVERTED:'Converti',
    IMMEDIATE:'Immédiate', HIGH:'Élevée', MEDIUM:'Moyenne', LOW:'Faible', MANUAL:'Manuel', EXTERNAL_API:'API externe', IMPORT:'Import', APPEL_TEL:'Appel téléphonique', WHATSAPP:'WhatsApp', EMAIL_AUTO:'E-mail automatique',
    EN_ATTENTE:'En attente', CONFIRMEE:'Confirmée', PREPARATION:'En préparation', EXPEDIEE:'Expédiée', LIVREE:'Livrée', ANNULEE:'Annulée', RETOURNEE:'Retournée',
    UNPAID:'Non payée', AWAITING_COLLECTION:'Encaissement attendu', PAID:'Payée', PARTIALLY_REFUNDED:'Partiellement remboursée', REFUNDED:'Remboursée',
    ASSIGNEE:'Affectée', ACCEPTEE:'Acceptée', EN_PREPARATION:'En préparation', CHEZ_TRANSPORTEUR:'Chez le transporteur', EN_COURS:'En livraison', ECHEC:'Échec', RETOUR_DEMANDE:'Retour demandé', RETOUR:'Retournée',
    CLIENT_ABSENT:'Client absent', ADRESSE_INCORRECTE:'Adresse incorrecte', CLIENT_REFUSE:'Refus du client', PROBLEME_PAIEMENT:'Problème de paiement', COLIS_ENDOMMAGE:'Colis endommagé', AUTRE:'Autre',
    SOCIETE_LIVRAISON:'Société de livraison', LIVREUR_INTERNE:'Livreur interne',
    PENDING:'En attente', AUTHORIZED:'Autorisée', COMPLETED:'Terminée', FAILED:'Échouée', CANCELLED:'Annulée',
    COMMANDE_PRODUCT:'Commande produit', ABONNEMENT_PLATFORM:'Abonnement plateforme', CASH_ON_DELIVERY:'Paiement à la livraison', CREDIT_CARD:'Carte bancaire',
    ACTIF:'Actif', DESACTIVE:'Désactivé', SUPPRIME:'Supprimé', EXPIRE:'Expiré', SUSPENDU:'Suspendu', ANNULATION_EN_COURS:'Annulation en cours', ANNULE:'Annulé', ECHEC_REMBOURSEMENT:'Échec du remboursement',
    HEBDOMADAIRE:'Hebdomadaire', MENSUEL:'Mensuel', TRIMESTRIEL:'Trimestriel', ANNUEL:'Annuel',
    QUEUED:'En attente d’envoi', SENT:'Envoyée', DELIVERED:'Distribuée', EMAIL:'E-mail', SMS:'SMS', PUSH:'Notification push',
    REASSORT:'Réassort', PERTE:'Perte', AJUSTEMENT:'Ajustement',
    CONNECTED:'Connectée', DISCONNECTED:'Déconnectée', ACTION_REQUIRED:'Action requise', PROCESSED:'Traité', IGNORED:'Ignoré',
  },
  ar: {
    ROLE_SUPER_ADMIN:'المسؤول العام', ROLE_ADMIN:'مسؤول المؤسسة', ROLE_CSM:'مسؤول خدمة العملاء', ROLE_LOGISTIC:'مسؤول الخدمات اللوجستية', ROLE_LIVREUR:'الموزع',
    SUPER_ADMIN:'المسؤول العام', ADMIN:'مسؤول المؤسسة', CSM:'مسؤول خدمة العملاء', LOGISTIC:'مسؤول الخدمات اللوجستية', LIVREUR:'الموزع',
    NEW_LEAD:'عميل محتمل جديد', ATTEMPTED_CONTACT:'تمت محاولة الاتصال', IN_PROGRESS:'قيد المتابعة', SCHEDULED_RECALL:'تمت جدولة معاودة الاتصال', UNREACHABLE:'متعذر الوصول إليه', REFUSED:'رافض', CONVERTED:'تم تحويله',
    IMMEDIATE:'فورية', HIGH:'مرتفعة', MEDIUM:'متوسطة', LOW:'منخفضة', MANUAL:'يدوي', EXTERNAL_API:'واجهة خارجية', IMPORT:'استيراد', APPEL_TEL:'مكالمة هاتفية', WHATSAPP:'واتساب', EMAIL_AUTO:'بريد إلكتروني آلي',
    EN_ATTENTE:'قيد الانتظار', CONFIRMEE:'مؤكد', PREPARATION:'قيد التجهيز', EXPEDIEE:'تم الشحن', LIVREE:'تم التسليم', ANNULEE:'ملغى', RETOURNEE:'مرتجع',
    UNPAID:'غير مدفوع', AWAITING_COLLECTION:'في انتظار التحصيل', PAID:'مدفوع', PARTIALLY_REFUNDED:'مسترد جزئيًا', REFUNDED:'مسترد',
    ASSIGNEE:'مسندة', ACCEPTEE:'مقبولة', EN_PREPARATION:'قيد التحضير', CHEZ_TRANSPORTEUR:'لدى الناقل', EN_COURS:'قيد التوصيل', ECHEC:'فشلت', RETOUR_DEMANDE:'تم طلب الإرجاع', RETOUR:'مرتجعة',
    CLIENT_ABSENT:'العميل غير موجود', ADRESSE_INCORRECTE:'العنوان غير صحيح', CLIENT_REFUSE:'رفض العميل', PROBLEME_PAIEMENT:'مشكلة في الدفع', COLIS_ENDOMMAGE:'الطرد تالف', AUTRE:'سبب آخر',
    SOCIETE_LIVRAISON:'شركة توصيل', LIVREUR_INTERNE:'موزع داخلي',
    PENDING:'قيد الانتظار', AUTHORIZED:'مصرح بها', COMPLETED:'مكتملة', FAILED:'فاشلة', CANCELLED:'ملغاة',
    COMMANDE_PRODUCT:'طلب منتجات', ABONNEMENT_PLATFORM:'اشتراك المنصة', CASH_ON_DELIVERY:'الدفع عند التسليم', CREDIT_CARD:'بطاقة بنكية',
    ACTIF:'نشط', DESACTIVE:'معطل', SUPPRIME:'محذوف', EXPIRE:'منتهي', SUSPENDU:'معلق', ANNULATION_EN_COURS:'الإلغاء قيد المعالجة', ANNULE:'ملغى', ECHEC_REMBOURSEMENT:'فشل الاسترداد',
    HEBDOMADAIRE:'أسبوعي', MENSUEL:'شهري', TRIMESTRIEL:'ربع سنوي', ANNUEL:'سنوي',
    QUEUED:'في قائمة الإرسال', SENT:'تم الإرسال', DELIVERED:'تم التسليم', EMAIL:'بريد إلكتروني', SMS:'رسالة نصية', PUSH:'إشعار فوري',
    REASSORT:'إعادة تزويد', PERTE:'فقدان', AJUSTEMENT:'تسوية',
    CONNECTED:'متصل', DISCONNECTED:'غير متصل', ACTION_REQUIRED:'يتطلب إجراءً', PROCESSED:'تمت المعالجة', IGNORED:'تم التجاهل',
  },
};

@Pipe({ name: 'domainLabel', standalone: true, pure: false })
export class DomainLabelPipe implements PipeTransform {
  private readonly i18n = inject(I18nService);
  transform(value: unknown): string {
    if (value === null || value === undefined || value === '') return '—';
    const raw = String(value);
    return LABELS[this.i18n.language()][raw] ?? raw.replace(/^ROLE_/, '').replaceAll('_', ' ').toLowerCase().replace(/^./, c => c.toUpperCase());
  }
}
