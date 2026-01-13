// frontend/src/types/index.ts
export interface User {
  id: number;
  username: string;
  email: string;
  roles: string[];
  enabled: boolean;
  createdAt: string;
  lastLogin: string | null;
}

export interface AuthResponse {
  token: string;
  username: string;
  roles: string[];
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
}