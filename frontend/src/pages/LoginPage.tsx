// frontend/src/pages/LoginPage.tsx
import { useState, useEffect } from 'react';
import { LoginForm } from '../components/auth/LoginForm';
import { RegisterForm } from '../components/auth/RegisterForm';
import { useAuth } from '../hooks/useAuth';

interface LoginPageProps {
  onSuccess: () => void;
  oauthError?: string | null;
  onClearError?: () => void;
}

export function LoginPage({ onSuccess, oauthError, onClearError }: LoginPageProps) {
  const [isLogin, setIsLogin] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const { login, register } = useAuth();

  // Show OAuth2 error if present
  useEffect(() => {
    if (oauthError) {
      setError(oauthError);
      // Auto-clear after 5 seconds
      const timer = setTimeout(() => {
        setError(null);
        onClearError?.();
      }, 5000);
      return () => clearTimeout(timer);
    }
  }, [oauthError, onClearError]);

  const handleLogin = async (username: string, password: string) => {
    await login({ username, password });
    onSuccess();
  };

  const handleRegister = async (username: string, email: string, password: string) => {
    await register({ username, email, password });
    onSuccess();
  };

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col justify-center px-4 py-12">
      <div className="w-full max-w-md mx-auto">
        {/* OAuth2 Error Banner */}
        {error && (
          <div className="mb-4 p-4 bg-red-50 border border-red-200 rounded-lg">
            <div className="flex items-start">
              <div className="flex-shrink-0">
                <svg className="h-5 w-5 text-red-400" viewBox="0 0 20 20" fill="currentColor">
                  <path fillRule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clipRule="evenodd" />
                </svg>
              </div>
              <div className="ml-3">
                <p className="text-sm text-red-700">{error}</p>
              </div>
              <div className="ml-auto pl-3">
                <button
                  onClick={() => {
                    setError(null);
                    onClearError?.();
                  }}
                  className="text-red-400 hover:text-red-600"
                >
                  <svg className="h-4 w-4" viewBox="0 0 20 20" fill="currentColor">
                    <path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd" />
                  </svg>
                </button>
              </div>
            </div>
          </div>
        )}

        <div className="bg-white rounded-xl shadow-lg p-6 sm:p-8">
          {isLogin ? (
            <LoginForm
              onSubmit={handleLogin}
              onSwitchToRegister={() => setIsLogin(false)}
            />
          ) : (
            <RegisterForm
              onSubmit={handleRegister}
              onSwitchToLogin={() => setIsLogin(true)}
            />
          )}
        </div>
      </div>
    </div>
  );
}
