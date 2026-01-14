// frontend/src/components/auth/LoginForm.tsx
import { useState } from 'react';
import type { FormEvent } from 'react';
import { Button } from '../ui/Button';
import { Input } from '../ui/Input';
import { SocialLogin } from './SocialLogin';

interface LoginFormProps {
  onSubmit: (username: string, password: string) => Promise<void>;
  onSwitchToRegister: () => void;
}

export function LoginForm({ onSubmit, onSwitchToRegister }: LoginFormProps) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setIsLoading(true);

    try {
      await onSubmit(username, password);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed. Please try again.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-6">
      <div className="text-center">
        <h1 className="text-2xl font-bold text-gray-900">Welcome Back</h1>
        <p className="mt-2 text-gray-600">Sign in to your account</p>
      </div>

      {error && (
        <div className="p-3 rounded-lg bg-red-50 border border-red-200" role="alert">
          <p className="text-sm text-red-600">{error}</p>
        </div>
      )}

      <div className="space-y-4">
        <Input
          label="Username"
          type="text"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          required
          autoComplete="username"
          placeholder="Enter your username"
        />

        <Input
          label="Password"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
          autoComplete="current-password"
          placeholder="Enter your password"
        />
      </div>

      <Button type="submit" isLoading={isLoading}>
        Sign In
      </Button>

      {/* OAuth2 Social Login */}
      <div className="mt-6">
        <SocialLogin />
      </div>

      <p className="text-center text-sm text-gray-600">
        Don't have an account?{' '}
        <button
          type="button"
          onClick={onSwitchToRegister}
          className="text-blue-600 hover:text-blue-800 font-medium"
        >
          Register here
        </button>
      </p>
    </form>
  );
}
