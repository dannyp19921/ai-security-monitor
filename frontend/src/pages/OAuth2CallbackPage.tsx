// frontend/src/pages/OAuth2CallbackPage.tsx
import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';

interface OAuth2CallbackPageProps {
  onLoginSuccess: (token: string) => void;
}

/**
 * OAuth2 Callback Page
 * 
 * This page handles the redirect from the backend after successful OAuth2 login.
 * The backend redirects here with the JWT token as a query parameter.
 * 
 * Flow:
 * 1. User clicks "Sign in with Google"
 * 2. Backend redirects to Google
 * 3. User authenticates with Google
 * 4. Google redirects back to backend
 * 5. Backend creates/finds user, generates JWT
 * 6. Backend redirects here with ?token=JWT
 * 7. We extract token, store it, redirect to dashboard
 */
export function OAuth2CallbackPage({ onLoginSuccess }: OAuth2CallbackPageProps) {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const token = searchParams.get('token');
    const errorParam = searchParams.get('error');

    if (errorParam) {
      setError(`OAuth2 login failed: ${errorParam}`);
      // Redirect to login after showing error
      setTimeout(() => navigate('/login'), 3000);
      return;
    }

    if (token) {
      // Store token and notify parent
      localStorage.setItem('token', token);
      onLoginSuccess(token);
      
      // Redirect to dashboard
      navigate('/dashboard', { replace: true });
    } else {
      setError('No token received from OAuth2 provider');
      setTimeout(() => navigate('/login'), 3000);
    }
  }, [searchParams, navigate, onLoginSuccess]);

  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <div className="bg-white p-8 rounded-xl shadow-lg max-w-md w-full">
          <div className="text-center">
            <div className="mx-auto flex items-center justify-center h-12 w-12 rounded-full bg-red-100 mb-4">
              <svg className="h-6 w-6 text-red-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </div>
            <h2 className="text-xl font-semibold text-gray-900 mb-2">Login Failed</h2>
            <p className="text-gray-500 mb-4">{error}</p>
            <p className="text-sm text-gray-400">Redirecting to login...</p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <div className="bg-white p-8 rounded-xl shadow-lg max-w-md w-full">
        <div className="text-center">
          <div className="mx-auto flex items-center justify-center h-12 w-12 rounded-full bg-blue-100 mb-4">
            <svg className="animate-spin h-6 w-6 text-blue-600" viewBox="0 0 24 24">
              <circle
                className="opacity-25"
                cx="12"
                cy="12"
                r="10"
                stroke="currentColor"
                strokeWidth="4"
                fill="none"
              />
              <path
                className="opacity-75"
                fill="currentColor"
                d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
              />
            </svg>
          </div>
          <h2 className="text-xl font-semibold text-gray-900 mb-2">Completing Login</h2>
          <p className="text-gray-500">Please wait while we complete your login...</p>
        </div>
      </div>
    </div>
  );
}

export default OAuth2CallbackPage;
