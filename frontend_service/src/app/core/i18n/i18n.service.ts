import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { inject, Injectable, PLATFORM_ID, signal } from '@angular/core';

export type AppLanguage = 'en' | 'fr' | 'ar';

const TEXT: Record<AppLanguage, Record<string, string>> = {
  en: {
    'language.label': 'Language', 'language.en': 'English', 'language.fr': 'Français', 'language.ar': 'العربية',
    'nav.workspace': 'Workspace', 'nav.overview': 'Overview', 'nav.leads': 'Leads', 'nav.orders': 'Orders',
    'nav.stock': 'Stock', 'nav.deliveries': 'Deliveries', 'nav.management': 'Management', 'nav.billing': 'Billing',
    'nav.subscriptions': 'Subscriptions', 'nav.integrations': 'Store integrations', 'nav.team': 'Team & access',
    'nav.tools': 'Tools', 'nav.notifications': 'Notification log', 'nav.assistant': 'AI operations',
    'nav.analytics': 'Business intelligence', 'nav.profile': 'My profile', 'nav.businessWorkspace': 'Business workspace',
    'shell.enterpriseWorkspace': 'Enterprise workspace', 'shell.viewProfile': 'View profile', 'shell.secure': 'Secure',
    'auth.platform': 'Unified operations platform', 'auth.heroTitle': 'One workspace. Every operation.',
    'auth.heroText': 'Run customer, inventory, delivery, billing, and platform operations through a secure role-based workspace.',
    'auth.welcome': 'Welcome back', 'auth.signInTitle': 'Sign in to your workspace',
    'auth.signInIntro': 'Use the account assigned to your enterprise or platform role.',
    'auth.email': 'Email address', 'auth.password': 'Password', 'auth.passwordPlaceholder': 'Enter your password',
    'auth.forgot': 'Forgot your password?', 'auth.signIn': 'Sign in', 'auth.signingIn': 'Signing in…',
    'auth.newEnterprise': 'New enterprise?', 'auth.viewPlans': 'View plans and create a workspace',
    'auth.required': 'Enter your email address and password.', 'auth.failed': 'Sign-in failed. Please try again.',
    'assistant.title': 'Operational copilot', 'assistant.tools': 'Tools', 'assistant.aiOperations': 'AI operations',
    'assistant.subtitle': 'Investigate live business data, prepare permitted actions, and approve changes with a clear audit boundary.',
    'assistant.clear': 'Clear history', 'assistant.operational': 'Operational', 'assistant.unavailable': 'Unavailable',
    'assistant.approval': 'Human-approved operations', 'assistant.welcome': 'What should we investigate or operate?',
    'assistant.welcomeText': 'I use live ERP data. Read requests run immediately; write requests become approval cards until you confirm.',
    'assistant.placeholder': 'Ask naturally in English, French, Arabic or Darija…', 'assistant.send': 'Send',
    'analytics.section': 'Intelligence', 'analytics.label': 'Conversational BI', 'analytics.title': 'Ask your business data',
    'analytics.subtitle': 'Tenant-isolated, role-aware answers with persistent history, charts and reports.',
    'analytics.clear': 'Clear history', 'analytics.welcome': 'What would you like to understand?',
    'analytics.welcomeText': 'Your available questions reflect your operational responsibilities.',
    'common.you': 'You', 'common.processing': 'Processing…', 'common.reject': 'Reject', 'common.confirm': 'Confirm and execute',
  },
  fr: {
    'language.label': 'Langue', 'language.en': 'English', 'language.fr': 'Français', 'language.ar': 'العربية',
    'nav.workspace': 'Espace de travail', 'nav.overview': 'Vue d’ensemble', 'nav.leads': 'Prospects', 'nav.orders': 'Commandes',
    'nav.stock': 'Stock', 'nav.deliveries': 'Livraisons', 'nav.management': 'Gestion', 'nav.billing': 'Facturation',
    'nav.subscriptions': 'Abonnements', 'nav.integrations': 'Boutiques connectées', 'nav.team': 'Équipe et accès',
    'nav.tools': 'Outils', 'nav.notifications': 'Journal des notifications', 'nav.assistant': 'Opérations IA',
    'nav.analytics': 'Informatique décisionnelle', 'nav.profile': 'Mon profil', 'nav.businessWorkspace': 'Espace entreprise',
    'shell.enterpriseWorkspace': 'Espace de l’entreprise', 'shell.viewProfile': 'Voir le profil', 'shell.secure': 'Sécurisé',
    'auth.platform': 'Plateforme unifiée des opérations', 'auth.heroTitle': 'Un espace. Toutes vos opérations.',
    'auth.heroText': 'Gérez clients, stock, livraisons, facturation et opérations avec des accès sécurisés par rôle.',
    'auth.welcome': 'Bon retour', 'auth.signInTitle': 'Connectez-vous à votre espace',
    'auth.signInIntro': 'Utilisez le compte attribué à votre entreprise ou à votre rôle plateforme.',
    'auth.email': 'Adresse e-mail', 'auth.password': 'Mot de passe', 'auth.passwordPlaceholder': 'Saisissez votre mot de passe',
    'auth.forgot': 'Mot de passe oublié ?', 'auth.signIn': 'Se connecter', 'auth.signingIn': 'Connexion…',
    'auth.newEnterprise': 'Nouvelle entreprise ?', 'auth.viewPlans': 'Voir les offres et créer un espace',
    'auth.required': 'Saisissez votre adresse e-mail et votre mot de passe.', 'auth.failed': 'Échec de connexion. Réessayez.',
    'assistant.title': 'Copilote opérationnel', 'assistant.tools': 'Outils', 'assistant.aiOperations': 'Opérations IA',
    'assistant.subtitle': 'Analysez les données réelles, préparez les opérations autorisées et approuvez chaque changement.',
    'assistant.clear': 'Effacer l’historique', 'assistant.operational': 'Opérationnel', 'assistant.unavailable': 'Indisponible',
    'assistant.approval': 'Opérations approuvées par un humain', 'assistant.welcome': 'Que souhaitez-vous analyser ou exécuter ?',
    'assistant.welcomeText': 'J’utilise les données réelles de l’ERP. Les écritures restent en attente de votre confirmation.',
    'assistant.placeholder': 'Posez votre question naturellement en français, arabe, darija ou anglais…', 'assistant.send': 'Envoyer',
    'analytics.section': 'Intelligence', 'analytics.label': 'BI conversationnelle', 'analytics.title': 'Interrogez vos données métier',
    'analytics.subtitle': 'Des réponses isolées par entreprise et adaptées au rôle, avec historique, graphiques et rapports.',
    'analytics.clear': 'Effacer l’historique', 'analytics.welcome': 'Que souhaitez-vous comprendre ?',
    'analytics.welcomeText': 'Les questions disponibles correspondent à vos responsabilités opérationnelles.',
    'common.you': 'Vous', 'common.processing': 'Traitement…', 'common.reject': 'Refuser', 'common.confirm': 'Confirmer et exécuter',
  },
  ar: {
    'language.label': 'اللغة', 'language.en': 'English', 'language.fr': 'Français', 'language.ar': 'العربية',
    'nav.workspace': 'مساحة العمل', 'nav.overview': 'نظرة عامة', 'nav.leads': 'العملاء المحتملون', 'nav.orders': 'الطلبات',
    'nav.stock': 'المخزون', 'nav.deliveries': 'التوصيلات', 'nav.management': 'الإدارة', 'nav.billing': 'الفوترة',
    'nav.subscriptions': 'الاشتراكات', 'nav.integrations': 'ربط المتاجر', 'nav.team': 'الفريق والصلاحيات',
    'nav.tools': 'الأدوات', 'nav.notifications': 'سجل الإشعارات', 'nav.assistant': 'عمليات الذكاء الاصطناعي',
    'nav.analytics': 'ذكاء الأعمال', 'nav.profile': 'ملفي الشخصي', 'nav.businessWorkspace': 'مساحة المؤسسة',
    'shell.enterpriseWorkspace': 'مساحة عمل المؤسسة', 'shell.viewProfile': 'عرض الملف', 'shell.secure': 'آمن',
    'auth.platform': 'منصة موحدة للعمليات', 'auth.heroTitle': 'مساحة واحدة لكل العمليات.',
    'auth.heroText': 'أدِر العملاء والمخزون والتوصيل والفوترة ضمن مساحة آمنة تعتمد على الصلاحيات.',
    'auth.welcome': 'مرحباً بعودتك', 'auth.signInTitle': 'تسجيل الدخول إلى مساحة العمل',
    'auth.signInIntro': 'استخدم الحساب المخصص لمؤسستك أو لدورك في المنصة.',
    'auth.email': 'البريد الإلكتروني', 'auth.password': 'كلمة المرور', 'auth.passwordPlaceholder': 'أدخل كلمة المرور',
    'auth.forgot': 'نسيت كلمة المرور؟', 'auth.signIn': 'تسجيل الدخول', 'auth.signingIn': 'جارٍ تسجيل الدخول…',
    'auth.newEnterprise': 'مؤسسة جديدة؟', 'auth.viewPlans': 'عرض الخطط وإنشاء مساحة عمل',
    'auth.required': 'أدخل البريد الإلكتروني وكلمة المرور.', 'auth.failed': 'تعذر تسجيل الدخول. حاول مرة أخرى.',
    'assistant.title': 'المساعد التشغيلي', 'assistant.tools': 'الأدوات', 'assistant.aiOperations': 'عمليات الذكاء الاصطناعي',
    'assistant.subtitle': 'حلّل البيانات الفعلية وحضّر العمليات المسموح بها وراجع كل تغيير قبل تنفيذه.',
    'assistant.clear': 'مسح المحادثة', 'assistant.operational': 'متاح', 'assistant.unavailable': 'غير متاح',
    'assistant.approval': 'عمليات بموافقة بشرية', 'assistant.welcome': 'ما الذي تريد تحليله أو تنفيذه؟',
    'assistant.welcomeText': 'أستخدم بيانات النظام الفعلية ولا يتم تنفيذ أي تغيير قبل موافقتك.',
    'assistant.placeholder': 'اكتب سؤالك بالعربية أو الدارجة أو الفرنسية أو الإنجليزية…', 'assistant.send': 'إرسال',
    'analytics.section': 'التحليلات', 'analytics.label': 'ذكاء الأعمال الحواري', 'analytics.title': 'اسأل بيانات مؤسستك',
    'analytics.subtitle': 'إجابات معزولة حسب المؤسسة والدور مع سجل ورسوم وتقارير.',
    'analytics.clear': 'مسح السجل', 'analytics.welcome': 'ما الذي تريد فهمه؟',
    'analytics.welcomeText': 'الأسئلة المتاحة تتوافق مع مسؤولياتك التشغيلية.',
    'common.you': 'أنت', 'common.processing': 'جارٍ التنفيذ…', 'common.reject': 'رفض', 'common.confirm': 'تأكيد وتنفيذ',
  },
};

@Injectable({ providedIn: 'root' })
export class I18nService {
  private readonly document = inject(DOCUMENT);
  private readonly browser = isPlatformBrowser(inject(PLATFORM_ID));
  readonly language = signal<AppLanguage>(this.initialLanguage());
  readonly locale = () => ({ en: 'en-US', fr: 'fr-MA', ar: 'ar-MA' }[this.language()]);

  constructor() { this.apply(this.language()); }

  setLanguage(language: AppLanguage): void {
    if (!['en', 'fr', 'ar'].includes(language)) return;
    this.language.set(language);
    if (this.browser) localStorage.setItem('intelliops.language', language);
    this.apply(language);
  }

  translate(key: string): string { return TEXT[this.language()][key] ?? TEXT.en[key] ?? key; }

  private initialLanguage(): AppLanguage {
    if (!this.browser) return 'en';
    const stored = localStorage.getItem('intelliops.language');
    if (stored === 'en' || stored === 'fr' || stored === 'ar') return stored;
    const browserLanguage = navigator.language.toLowerCase();
    return browserLanguage.startsWith('ar') ? 'ar' : browserLanguage.startsWith('fr') ? 'fr' : 'en';
  }

  private apply(language: AppLanguage): void {
    this.document.documentElement.lang = language;
    this.document.documentElement.dir = language === 'ar' ? 'rtl' : 'ltr';
  }
}
