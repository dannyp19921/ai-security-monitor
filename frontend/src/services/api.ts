// frontend/src/services/api.ts
import axios from 'axios';
import type { AuthResponse, LoginRequest, RegisterRequest, AuditLog, UserResponse } from '../types';

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
    localStorage.setItem('token', response.data.token);
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