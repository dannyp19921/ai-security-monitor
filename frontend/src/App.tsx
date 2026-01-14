// frontend/src/App.tsx
import { useState, useEffect } from 'react';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { authService } from './services/api';

function App() {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [oauthError, setOauthError] = useState<string | null>(null);

  useEffect(() => {
    // Check for OAuth2 callback
    const handleOAuth2Callback = () => {
      const path = window.location.pathname;
      const params = new URLSearchParams(window.location.search);
      
      // Handle OAuth2 callback from backend
      if (path === '/oauth2/callback') {
        const token = params.get('token');
        const error = params.get('error');
        
        if (error) {
          setOauthError(`OAuth2 login failed: ${error}`);
          // Clear URL and show login page
          window.history.replaceState({}, '', '/');
          setIsLoading(false);
          return;
        }
        
        if (token) {
          // Store token
          localStorage.setItem('token', token);
          // Clear URL and redirect to dashboard
          window.history.replaceState({}, '', '/');
          setIsAuthenticated(true);
          setIsLoading(false);
          return;
        }
      }
      
      // Normal auth check
      setIsAuthenticated(authService.isAuthenticated());
      setIsLoading(false);
    };
    
    handleOAuth2Callback();
  }, []);

  if (isLoading) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="text-center">
          <svg className="animate-spin h-10 w-10 text-blue-600 mx-auto mb-4" viewBox="0 0 24 24">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
          </svg>
          <p className="text-gray-500">Loading...</p>
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return (
      <LoginPage 
        onSuccess={() => setIsAuthenticated(true)} 
        oauthError={oauthError}
        onClearError={() => setOauthError(null)}
      />
    );
  }

  return <DashboardPage onLogout={() => setIsAuthenticated(false)} />;
}

export default App;
