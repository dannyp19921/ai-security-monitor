// frontend/src/components/auth/MfaVerify.tsx
import { useState } from 'react';
import { Button } from '../ui/Button';
import { Input } from '../ui/Input';
import { mfaService } from '../../services/api';

interface MfaVerifyProps {
  mfaToken: string;
  onSuccess: (token: string) => void;
  onCancel: () => void;
}

type VerifyMode = 'totp' | 'backup';

export function MfaVerify({ mfaToken, onSuccess, onCancel }: MfaVerifyProps) {
  const [mode, setMode] = useState<VerifyMode>('totp');
  const [code, setCode] = useState('');
  const [backupCode, setBackupCode] = useState('');
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const handleVerifyTotp = async () => {
    if (code.length !== 6) {
      setError('Please enter a 6-digit code');
      return;
    }

    setIsLoading(true);
    setError('');

    try {
      const response = await mfaService.verifyCode(code, mfaToken);
      if (response.success && response.token) {
        // Store the new full token (not the MFA pending token)
        localStorage.setItem('token', response.token);
        onSuccess(response.token);
      } else {
        setError(response.message || 'Invalid code');
      }
    } catch (err) {
      setError('Invalid code. Please try again.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleVerifyBackup = async () => {
    if (backupCode.length < 8) {
      setError('Please enter a valid backup code');
      return;
    }

    setIsLoading(true);
    setError('');

    try {
      const response = await mfaService.verifyBackupCode(backupCode, mfaToken);
      if (response.success && response.token) {
        localStorage.setItem('token', response.token);
        onSuccess(response.token);
        
        // Warn if low on backup codes
        if (response.remainingBackupCodes !== undefined && response.remainingBackupCodes <= 2) {
          alert(`Warning: You only have ${response.remainingBackupCodes} backup codes remaining. Consider generating new ones.`);
        }
      } else {
        setError(response.message || 'Invalid backup code');
      }
    } catch (err) {
      setError('Invalid backup code. Please try again.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="text-center">
        <div className="text-4xl mb-2">🔐</div>
        <h2 className="text-xl font-bold text-gray-900">Two-Factor Authentication</h2>
        <p className="mt-2 text-gray-600">
          {mode === 'totp' 
            ? 'Enter the code from your authenticator app'
            : 'Enter one of your backup codes'
          }
        </p>
      </div>

      {error && (
        <div className="p-3 rounded-lg bg-red-50 border border-red-200">
          <p className="text-sm text-red-600">{error}</p>
        </div>
      )}

      {mode === 'totp' ? (
        <>
          <div className="flex justify-center">
            <Input
              label="Authentication Code"
              type="text"
              inputMode="numeric"
              pattern="[0-9]*"
              maxLength={6}
              value={code}
              onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
              placeholder="000000"
              className="text-center text-2xl tracking-widest w-48"
              autoFocus
            />
          </div>

          <Button 
            onClick={handleVerifyTotp}
            isLoading={isLoading}
            disabled={code.length !== 6}
            className="w-full"
          >
            Verify
          </Button>

          <div className="text-center">
            <button
              type="button"
              onClick={() => {
                setMode('backup');
                setError('');
              }}
              className="text-sm text-blue-600 hover:text-blue-800"
            >
              Use a backup code instead
            </button>
          </div>
        </>
      ) : (
        <>
          <Input
            label="Backup Code"
            type="text"
            value={backupCode}
            onChange={(e) => setBackupCode(e.target.value.toUpperCase())}
            placeholder="XXXX-XXXX"
            className="text-center text-lg tracking-wider"
            autoFocus
          />

          <Button 
            onClick={handleVerifyBackup}
            isLoading={isLoading}
            disabled={backupCode.length < 8}
            className="w-full"
          >
            Verify Backup Code
          </Button>

          <div className="text-center">
            <button
              type="button"
              onClick={() => {
                setMode('totp');
                setError('');
              }}
              className="text-sm text-blue-600 hover:text-blue-800"
            >
              Use authenticator app instead
            </button>
          </div>
        </>
      )}

      <div className="pt-4 border-t">
        <button
          type="button"
          onClick={onCancel}
          className="w-full text-sm text-gray-500 hover:text-gray-700"
        >
          ← Back to login
        </button>
      </div>
    </div>
  );
}
