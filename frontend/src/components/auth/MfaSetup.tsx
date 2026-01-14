// frontend/src/components/auth/MfaSetup.tsx
import { useState, useEffect } from 'react';
import { QRCodeSVG } from 'qrcode.react';
import { Button } from '../ui/Button';
import { Input } from '../ui/Input';
import { mfaService } from '../../services/api';
import type { MfaSetupResponse } from '../../types';

interface MfaSetupProps {
  onComplete: (backupCodes: string[]) => void;
  onCancel: () => void;
}

type SetupStep = 'loading' | 'scan' | 'verify' | 'backup' | 'error';

export function MfaSetup({ onComplete, onCancel }: MfaSetupProps) {
  const [step, setStep] = useState<SetupStep>('loading');
  const [setupData, setSetupData] = useState<MfaSetupResponse | null>(null);
  const [verificationCode, setVerificationCode] = useState('');
  const [backupCodes, setBackupCodes] = useState<string[]>([]);
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [copiedBackup, setCopiedBackup] = useState(false);

  useEffect(() => {
    initiateSetup();
  }, []);

  const initiateSetup = async () => {
    try {
      setStep('loading');
      const data = await mfaService.initiateSetup();
      setSetupData(data);
      setStep('scan');
    } catch (err) {
      setError('Failed to initiate MFA setup. Please try again.');
      setStep('error');
    }
  };

  const handleVerify = async () => {
    if (verificationCode.length !== 6) {
      setError('Please enter a 6-digit code');
      return;
    }

    setIsLoading(true);
    setError('');

    try {
      const response = await mfaService.completeSetup({
        secret: setupData!.secret,
        code: verificationCode,
      });
      setBackupCodes(response.backupCodes);
      setStep('backup');
    } catch (err) {
      setError('Invalid code. Please check your authenticator app and try again.');
    } finally {
      setIsLoading(false);
    }
  };

  const copyBackupCodes = () => {
    const codesText = backupCodes.join('\n');
    navigator.clipboard.writeText(codesText);
    setCopiedBackup(true);
    setTimeout(() => setCopiedBackup(false), 2000);
  };

  const handleComplete = () => {
    onComplete(backupCodes);
  };

  // Loading state
  if (step === 'loading') {
    return (
      <div className="text-center py-8">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto"></div>
        <p className="mt-4 text-gray-600">Setting up two-factor authentication...</p>
      </div>
    );
  }

  // Error state
  if (step === 'error') {
    return (
      <div className="text-center py-8">
        <div className="text-red-500 text-5xl mb-4">⚠️</div>
        <p className="text-red-600 mb-4">{error}</p>
        <div className="space-x-4">
          <Button onClick={initiateSetup}>Try Again</Button>
          <Button variant="secondary" onClick={onCancel}>Cancel</Button>
        </div>
      </div>
    );
  }

  // Step 1: Scan QR Code
  if (step === 'scan') {
    return (
      <div className="space-y-6">
        <div className="text-center">
          <h2 className="text-xl font-bold text-gray-900">Set Up Two-Factor Authentication</h2>
          <p className="mt-2 text-gray-600">Scan this QR code with your authenticator app</p>
        </div>

        {/* QR Code */}
        <div className="flex justify-center">
          <div className="p-4 bg-white rounded-lg shadow-inner border">
            <QRCodeSVG
              value={setupData!.qrCodeUri}
              size={200}
              level="M"
            />
          </div>
        </div>

        {/* Manual entry option */}
        <div className="text-center">
          <p className="text-sm text-gray-500 mb-2">Can't scan? Enter this code manually:</p>
          <code className="px-3 py-2 bg-gray-100 rounded text-sm font-mono select-all">
            {setupData!.secret}
          </code>
        </div>

        {/* Supported apps */}
        <div className="text-center text-sm text-gray-500">
          <p>Supported apps: Google Authenticator, Authy, 1Password, etc.</p>
        </div>

        <div className="flex space-x-4">
          <Button variant="secondary" onClick={onCancel} className="flex-1">
            Cancel
          </Button>
          <Button onClick={() => setStep('verify')} className="flex-1">
            Next: Verify Code
          </Button>
        </div>
      </div>
    );
  }

  // Step 2: Verify Code
  if (step === 'verify') {
    return (
      <div className="space-y-6">
        <div className="text-center">
          <h2 className="text-xl font-bold text-gray-900">Verify Setup</h2>
          <p className="mt-2 text-gray-600">Enter the 6-digit code from your authenticator app</p>
        </div>

        {error && (
          <div className="p-3 rounded-lg bg-red-50 border border-red-200">
            <p className="text-sm text-red-600">{error}</p>
          </div>
        )}

        <div className="flex justify-center">
          <Input
            label="Verification Code"
            type="text"
            inputMode="numeric"
            pattern="[0-9]*"
            maxLength={6}
            value={verificationCode}
            onChange={(e) => setVerificationCode(e.target.value.replace(/\D/g, ''))}
            placeholder="000000"
            className="text-center text-2xl tracking-widest w-48"
            autoFocus
          />
        </div>

        <div className="flex space-x-4">
          <Button variant="secondary" onClick={() => setStep('scan')} className="flex-1">
            Back
          </Button>
          <Button 
            onClick={handleVerify} 
            isLoading={isLoading}
            disabled={verificationCode.length !== 6}
            className="flex-1"
          >
            Verify & Enable
          </Button>
        </div>
      </div>
    );
  }

  // Step 3: Backup Codes
  if (step === 'backup') {
    return (
      <div className="space-y-6">
        <div className="text-center">
          <div className="text-green-500 text-5xl mb-2">✓</div>
          <h2 className="text-xl font-bold text-gray-900">Two-Factor Authentication Enabled!</h2>
          <p className="mt-2 text-gray-600">Save these backup codes in a safe place</p>
        </div>

        <div className="p-4 bg-yellow-50 border border-yellow-200 rounded-lg">
          <p className="text-sm text-yellow-800 font-medium mb-2">⚠️ Important:</p>
          <p className="text-sm text-yellow-700">
            These codes can be used to access your account if you lose your authenticator device. 
            Each code can only be used once. Store them securely!
          </p>
        </div>

        <div className="grid grid-cols-2 gap-2 p-4 bg-gray-50 rounded-lg font-mono text-sm">
          {backupCodes.map((code, index) => (
            <div key={index} className="px-3 py-2 bg-white rounded border text-center">
              {code}
            </div>
          ))}
        </div>

        <Button 
          variant="secondary" 
          onClick={copyBackupCodes}
          className="w-full"
        >
          {copiedBackup ? '✓ Copied!' : 'Copy All Codes'}
        </Button>

        <Button onClick={handleComplete} className="w-full">
          I've Saved My Backup Codes
        </Button>
      </div>
    );
  }

  return null;
}
