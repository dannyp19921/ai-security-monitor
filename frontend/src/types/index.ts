// frontend/src/types/index.ts
export interface User {
  id: number;
  username: string;
  email: string;
  roles: string[];
  enabled: boolean;
  createdAt: string;
  lastLogin: string | null;
  mfaEnabled?: boolean;
}

export interface AuthResponse {
  token: string;
  username: string;
  roles: string[];
  mfaRequired?: boolean;
  mfaToken?: string; // Temporary token for MFA verification
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

export interface AuditLog {
  id: number;
  timestamp: string;
  action: string;
  username: string;
  resourceType: string | null;
  resourceId: string | null;
  ipAddress: string | null;
  details: string | null;
  success: boolean;
}

export interface UserResponse {
  id: number;
  username: string;
  email: string;
  roles: string[];
  enabled: boolean;
  createdAt: string;
  lastLogin: string | null;
  mfaEnabled?: boolean;
}

// MFA Types
export interface MfaSetupResponse {
  secret: string;
  qrCodeUri: string;
  issuer: string;
  accountName: string;
}

export interface MfaSetupVerifyRequest {
  secret: string;
  code: string;
}

export interface MfaSetupCompleteResponse {
  mfaEnabled: boolean;
  backupCodes: string[];
  message: string;
}

export interface MfaVerifyRequest {
  code: string;
}

export interface MfaStatusResponse {
  mfaEnabled: boolean;
  mfaEnabledAt: string | null;
  backupCodesRemaining: number | null;
}

export interface MfaDisableRequest {
  password: string;
  code: string;
}
