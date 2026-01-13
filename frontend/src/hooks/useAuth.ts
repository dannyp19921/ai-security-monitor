// frontend/src/hooks/useAuth.ts 
import { useState, useEffect, useCallback } from 'react';
import type { AuthResponse, LoginRequest, RegisterRequest } from '../types';
import { authService } from '../services/api';

interface AuthState {
  isAuthenticated: boolean;
  username: string | null;
  roles: string[];
  isLoading: boolean;
}

export function useAuth() {
  const [state, setState] = useState<AuthState>({
    isAuthenticated: false,
    username: null,
    roles: [],
    isLoading: true,
  });

  useEffect(() => {
    const token = authService.getToken();
    if (token) {
      try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        setState({
          isAuthenticated: true,
          username: payload.sub,
          roles: payload.roles || [],
          isLoading: false,
        });
      } catch {
        authService.logout();
        setState({
          isAuthenticated: false,
          username: null,
          roles: [],
          isLoading: false,
        });
      }
    } else {
      setState(prev => ({ ...prev, isLoading: false }));
    }
  }, []);

  const login = useCallback(async (data: LoginRequest): Promise<AuthResponse> => {
    const response = await authService.login(data);
    setState({
      isAuthenticated: true,
      username: response.username,
      roles: response.roles,
      isLoading: false,
    });
    return response;
  }, []);

  const register = useCallback(async (data: RegisterRequest): Promise<AuthResponse> => {
    const response = await authService.register(data);
    setState({
      isAuthenticated: true,
      username: response.username,
      roles: response.roles,
      isLoading: false,
    });
    return response;
  }, []);

  const logout = useCallback(() => {
    authService.logout();
    setState({
      isAuthenticated: false,
      username: null,
      roles: [],
      isLoading: false,
    });
  }, []);

  const hasRole = useCallback((role: string) => {
    return state.roles.includes(role);
  }, [state.roles]);

  return {
    ...state,
    login,
    register,
    logout,
    hasRole,
  };
}