export const USER_ROLES = ['ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_CSM', 'ROLE_LOGISTIC', 'ROLE_LIVREUR'] as const;
export type UserRole = (typeof USER_ROLES)[number];

export const STAFF_ROLES = ['CSM', 'LOGISTIC', 'LIVREUR'] as const;
export type StaffRole = (typeof STAFF_ROLES)[number];

export interface LoginRequest {
  readonly email: string;
  readonly password: string;
}

export interface AuthResponse {
  readonly token: string;
  readonly id: number;
  readonly email: string;
  readonly firstname: string;
  readonly lastname: string;
  readonly role: UserRole;
  readonly enterpriseId: number;
}

export interface RegisterRequest {
  readonly companyName: string;
  readonly activityType: string;
  readonly email: string;
  readonly password: string;
  readonly firstname: string;
  readonly lastname: string;
  readonly phone?: string;
}

export interface UserCreationRequest {
  readonly email: string;
  readonly password: string;
  readonly firstname: string;
  readonly lastname: string;
  readonly phone?: string;
  readonly role: StaffRole;
}

export interface UserResponse {
  readonly id: number;
  readonly email: string;
  readonly firstname: string;
  readonly lastname: string;
  readonly phone: string | null;
  readonly role: UserRole;
  /** Jackson serializes Java's `isActive()` property as `active`. */
  readonly active: boolean;
}

export interface ProfileUpdateRequest {
  readonly firstname?: string;
  readonly lastname?: string;
  readonly phone?: string;
}

export interface ChangePasswordRequest {
  readonly currentPassword: string;
  readonly newPassword: string;
  readonly confirmPassword: string;
}

export interface StaffStatusRequest {
  readonly active: boolean;
}
