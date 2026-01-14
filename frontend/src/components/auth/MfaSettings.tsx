// frontend/src/components/auth/MfaSettings.tsx
import { useState, useEffect } from 'react';
import { Button } from '../ui/Button';
import { Input } from '../ui/Input';
import { mfaService } from '../../services/api';
import { MfaSetup } from './MfaSetup';
import type { MfaStatusResponse } from '../../types';

export function MfaSettings() {
  const [status, setStatus] = useState<MfaStatusResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [showSetup, setShowSetup] = useState(false);
  const [showDisable, setShowDisable] = useState(false);
  const [showRegenerate, setShowRegenerate] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  // Disable form state
  const [disablePassword, setDisablePassword] = useState('');
  const [disableCode, setDisableCode] = useState('');
  const [isDisabling, setIsDisabling] = useState(false);

  // Regenerate form state
  const [regenerateCode, setRegenerateCode] = useState('');
  const [isRegenerating, setIsRegenerating] = useState(false);
  const [newBackupCodes, setNewBackupCodes] = useState<string[]>([]);

  useEffect(() => {
    loadStatus();
  }, []);

  const loadStatus = async () => {
    try {
      const data = await mfaService.getStatus();
      setStatus(data);
    } catch (err) {
      setError('Failed to load MFA status');
    } finally {
      setIsLoading(false);
    }
  };

  const handleSetupComplete = (_backupCodes: string[]) => {
    setShowSetup(false);
    setSuccess('Two-factor authentication has been enabled!');
    loadStatus();
  };

  const handleDisable = async () => {
    setIsDisabling(true);
    setError('');

    try {
      await mfaService.disable(disablePassword, disableCode);
      setShowDisable(false);
      setDisablePassword('');
      setDisableCode('');
      setSuccess('Two-factor authentication has been disabled.');
      loadStatus();
    } catch (err) {
      setError('Failed to disable MFA. Please check your password and code.');
    } finally {
      setIsDisabling(false);
    }
  };

  const handleRegenerate = async () => {
    setIsRegenerating(true);
    setError('');

    try {
      const response = await mfaService.regenerateBackupCodes(regenerateCode);
      setNewBackupCodes(response.backupCodes);
      setRegenerateCode('');
      setSuccess('New backup codes have been generated.');
    } catch (err) {
      setError('Failed to regenerate backup codes. Please check your code.');
    } finally {
      setIsRegenerating(false);
    }
  };

  const copyBackupCodes = () => {
    navigator.clipboard.writeText(newBackupCodes.join('\n'));
  };

  if (isLoading) {
    return (
      <div className="p-6 bg-white rounded-lg shadow">
        <div className="animate-pulse flex space-x-4">
          <div className="flex-1 space-y-4 py-1">
            <div className="h-4 bg-gray-200 rounded w-3/4"></div>
            <div className="h-4 bg-gray-200 rounded w-1/2"></div>
          </div>
        </div>
      </div>
    );
  }

  // Show MFA Setup flow
  if (showSetup) {
    return (
      <div className="p-6 bg-white rounded-lg shadow">
        <MfaSetup 
          onComplete={handleSetupComplete}
          onCancel={() => setShowSetup(false)}
        />
      </div>
    );
  }

  // Show new backup codes after regeneration
  if (newBackupCodes.length > 0) {
    return (
      <div className="p-6 bg-white rounded-lg shadow space-y-4">
        <h3 className="text-lg font-semibold text-gray-900">New Backup Codes</h3>
        
        <div className="p-4 bg-yellow-50 border border-yellow-200 rounded-lg">
          <p className="text-sm text-yellow-800">
            ⚠️ Save these codes! Your old backup codes are now invalid.
          </p>
        </div>

        <div className="grid grid-cols-2 gap-2 p-4 bg-gray-50 rounded-lg font-mono text-sm">
          {newBackupCodes.map((code, index) => (
            <div key={index} className="px-3 py-2 bg-white rounded border text-center">
              {code}
            </div>
          ))}
        </div>

        <div className="flex space-x-4">
          <Button variant="secondary" onClick={copyBackupCodes} className="flex-1">
            Copy Codes
          </Button>
          <Button onClick={() => setNewBackupCodes([])} className="flex-1">
            Done
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="p-6 bg-white rounded-lg shadow space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-lg font-semibold text-gray-900">Two-Factor Authentication</h3>
          <p className="text-sm text-gray-500">
            Add an extra layer of security to your account
          </p>
        </div>
        <div className={`px-3 py-1 rounded-full text-sm font-medium ${
          status?.mfaEnabled 
            ? 'bg-green-100 text-green-800' 
            : 'bg-gray-100 text-gray-800'
        }`}>
          {status?.mfaEnabled ? 'Enabled' : 'Disabled'}
        </div>
      </div>

      {error && (
        <div className="p-3 rounded-lg bg-red-50 border border-red-200">
          <p className="text-sm text-red-600">{error}</p>
        </div>
      )}

      {success && (
        <div className="p-3 rounded-lg bg-green-50 border border-green-200">
          <p className="text-sm text-green-600">{success}</p>
        </div>
      )}

      {status?.mfaEnabled ? (
        <>
          {/* MFA Status Info */}
          <div className="p-4 bg-gray-50 rounded-lg space-y-2">
            <div className="flex justify-between text-sm">
              <span className="text-gray-500">Enabled since:</span>
              <span className="text-gray-900">
                {status.mfaEnabledAt 
                  ? new Date(status.mfaEnabledAt).toLocaleDateString()
                  : 'Unknown'
                }
              </span>
            </div>
            <div className="flex justify-between text-sm">
              <span className="text-gray-500">Backup codes remaining:</span>
              <span className={`font-medium ${
                (status.backupCodesRemaining ?? 0) <= 2 
                  ? 'text-red-600' 
                  : 'text-gray-900'
              }`}>
                {status.backupCodesRemaining ?? 'Unknown'}
              </span>
            </div>
          </div>

          {/* Regenerate Backup Codes */}
          {showRegenerate ? (
            <div className="p-4 border rounded-lg space-y-4">
              <h4 className="font-medium text-gray-900">Generate New Backup Codes</h4>
              <p className="text-sm text-gray-500">
                Enter your authenticator code to generate new backup codes. 
                This will invalidate your existing codes.
              </p>
              <Input
                label="Authenticator Code"
                type="text"
                inputMode="numeric"
                maxLength={6}
                value={regenerateCode}
                onChange={(e) => setRegenerateCode(e.target.value.replace(/\D/g, ''))}
                placeholder="Enter 6-digit code"
              />
              <div className="flex space-x-4">
                <Button 
                  variant="secondary" 
                  onClick={() => setShowRegenerate(false)}
                  className="flex-1"
                >
                  Cancel
                </Button>
                <Button 
                  onClick={handleRegenerate}
                  isLoading={isRegenerating}
                  disabled={regenerateCode.length !== 6}
                  className="flex-1"
                >
                  Generate
                </Button>
              </div>
            </div>
          ) : (
            <Button 
              variant="secondary" 
              onClick={() => setShowRegenerate(true)}
              className="w-full"
            >
              Regenerate Backup Codes
            </Button>
          )}

          {/* Disable MFA */}
          {showDisable ? (
            <div className="p-4 border border-red-200 rounded-lg space-y-4">
              <h4 className="font-medium text-red-600">Disable Two-Factor Authentication</h4>
              <p className="text-sm text-gray-500">
                Enter your password and authenticator code to disable 2FA.
              </p>
              <Input
                label="Password"
                type="password"
                value={disablePassword}
                onChange={(e) => setDisablePassword(e.target.value)}
                placeholder="Your password"
              />
              <Input
                label="Verification Code"
                type="text"
                inputMode="numeric"
                maxLength={6}
                value={disableCode}
                onChange={(e) => setDisableCode(e.target.value.replace(/\D/g, ''))}
                placeholder="6-digit code or backup code"
              />
              <div className="flex space-x-4">
                <Button 
                  variant="secondary" 
                  onClick={() => setShowDisable(false)}
                  className="flex-1"
                >
                  Cancel
                </Button>
                <Button 
                  variant="danger"
                  onClick={handleDisable}
                  isLoading={isDisabling}
                  className="flex-1"
                >
                  Disable 2FA
                </Button>
              </div>
            </div>
          ) : (
            <button
              onClick={() => setShowDisable(true)}
              className="text-sm text-red-600 hover:text-red-800"
            >
              Disable two-factor authentication
            </button>
          )}
        </>
      ) : (
        <>
          <p className="text-sm text-gray-600">
            Protect your account by requiring a verification code in addition to your password 
            when signing in.
          </p>
          <Button onClick={() => setShowSetup(true)}>
            Enable Two-Factor Authentication
          </Button>
        </>
      )}
    </div>
  );
}
