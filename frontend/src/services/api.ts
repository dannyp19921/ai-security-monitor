// frontend/src/services/api.ts
import axios from 'axios';
import type { 
  AuthResponse, 
  LoginRequest, 
  RegisterRequest, 
  AuditLog, 
  UserResponse,
  MfaSetupResponse,
  MfaSetupVerifyRequest,
  MfaSetupCompleteResponse,
  MfaStatusResponse
} from '../types';

const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

const api = axios.create({
  baseURL: API_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Add token to requests if available
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Auth services
export const authService = {
  login: async (data: LoginRequest): Promise<AuthResponse> => {
    const response = await api.post<AuthResponse>('/api/auth/login', data);
    // Only store token if MFA is not required
    if (!response.data.mfaRequired) {
      localStorage.setItem('token', response.data.token);
    }
    return response.data;
  },

  register: async (data: RegisterRequest): Promise<AuthResponse> => {
    const response = await api.post<AuthResponse>('/api/auth/register', data);
    localStorage.setItem('token', response.data.token);
    return response.data;
  },

  logout: () => {
    localStorage.removeItem('token');
  },

  getToken: () => localStorage.getItem('token'),

  isAuthenticated: () => !!localStorage.getItem('token'),
};

// MFA services
export const mfaService = {
  // Get MFA status for current user
  getStatus: async (): Promise<MfaStatusResponse> => {
    const response = await api.get<MfaStatusResponse>('/api/auth/mfa/status');
    return response.data;
  },

  // Initiate MFA setup - returns secret and QR code URI
  initiateSetup: async (): Promise<MfaSetupResponse> => {
    const response = await api.post<MfaSetupResponse>('/api/auth/mfa/setup');
    return response.data;
  },

  // Complete MFA setup by verifying the first code
  completeSetup: async (data: MfaSetupVerifyRequest): Promise<MfaSetupCompleteResponse> => {
    const response = await api.post<MfaSetupCompleteResponse>('/api/auth/mfa/setup/verify', data);
    return response.data;
  },

  // Verify MFA code during login
  verifyCode: async (code: string, mfaToken?: string): Promise<{ success: boolean; message: string; token?: string }> => {
    const config = mfaToken 
      ? { headers: { Authorization: `Bearer ${mfaToken}` } }
      : {};
    const response = await api.post('/api/auth/mfa/verify', { code }, config);
    return response.data;
  },

  // Verify backup code during login
  verifyBackupCode: async (backupCode: string, mfaToken?: string): Promise<{ success: boolean; message: string; token?: string; remainingBackupCodes?: number }> => {
    const config = mfaToken 
      ? { headers: { Authorization: `Bearer ${mfaToken}` } }
      : {};
    const response = await api.post('/api/auth/mfa/backup', { backupCode }, config);
    return response.data;
  },

  // Disable MFA
  disable: async (password: string, code: string): Promise<{ mfaEnabled: boolean; message: string }> => {
    const response = await api.post('/api/auth/mfa/disable', { password, code });
    return response.data;
  },

  // Regenerate backup codes
  regenerateBackupCodes: async (code: string): Promise<{ backupCodes: string[]; message: string }> => {
    const response = await api.post('/api/auth/mfa/backup-codes', { code });
    return response.data;
  },
};

// Audit services
export const auditService = {
  getRecentLogs: async (): Promise<AuditLog[]> => {
    const response = await api.get<AuditLog[]>('/api/audit/logs');
    return response.data;
  },

  getLogsByUsername: async (username: string): Promise<AuditLog[]> => {
    const response = await api.get<AuditLog[]>(`/api/audit/logs/user/${username}`);
    return response.data;
  },
};

// Health check
export const healthService = {
  check: async () => {
    const response = await api.get('/api/health');
    return response.data;
  },
};

// AI services
export const aiService = {
  chat: async (message: string): Promise<{ response: string; timestamp: string }> => {
    const response = await api.post('/api/ai/chat', { message });
    return response.data;
  },
};

// Admin services
export const adminService = {
  getAllUsers: async (): Promise<UserResponse[]> => {
    const response = await api.get<UserResponse[]>('/api/admin/users');
    return response.data;
  },

  addRoleToUser: async (userId: number, roleName: string): Promise<UserResponse> => {
    const response = await api.post<UserResponse>(`/api/admin/users/${userId}/roles/${roleName}`);
    return response.data;
  },

  removeRoleFromUser: async (userId: number, roleName: string): Promise<UserResponse> => {
    const response = await api.delete<UserResponse>(`/api/admin/users/${userId}/roles/${roleName}`);
    return response.data;
  },
};

export default api;
