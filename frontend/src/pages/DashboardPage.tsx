// frontend/src/pages/DashboardPage.tsx
import { useState, useEffect } from 'react';
import { useAuth } from '../hooks/useAuth';
import { auditService } from '../services/api';
import { Button } from '../components/ui/Button';
import { AiChat } from '../components/chat/AiChat';
import { AdminPanel } from '../components/admin/AdminPanel';
import { MfaSettings } from '../components/auth/MfaSettings';
import type { AuditLog } from '../types';

interface DashboardPageProps {
  onLogout: () => void;
}

type TabType = 'dashboard' | 'security';

export function DashboardPage({ onLogout }: DashboardPageProps) {
  const { username, roles, logout, hasRole } = useAuth();
  const [auditLogs, setAuditLogs] = useState<AuditLog[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [activeTab, setActiveTab] = useState<TabType>('dashboard');

  useEffect(() => {
    const fetchLogs = async () => {
      try {
        const logs = await auditService.getRecentLogs();
        setAuditLogs(logs);
      } catch (err) {
        setError('Failed to load audit logs');
      } finally {
        setIsLoading(false);
      }
    };

    fetchLogs();
  }, []);

  const handleLogout = () => {
    logout();
    onLogout();
  };

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleString();
  };

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <header className="bg-white shadow-sm">
        <div className="max-w-7xl mx-auto px-4 py-4 flex justify-between items-center">
          <h1 className="text-xl font-bold text-gray-900">AI Security Monitor</h1>
          <div className="flex items-center gap-4">
            <span className="text-sm text-gray-600 hidden sm:block">
              {username}
              {hasRole('ADMIN') && (
                <span className="ml-2 px-2 py-1 bg-purple-100 text-purple-800 rounded-full text-xs font-medium">
                  ADMIN
                </span>
              )}
            </span>
            <Button variant="secondary" onClick={handleLogout} className="!w-auto !py-2">
              Logout
            </Button>
          </div>
        </div>
      </header>

      {/* Tab Navigation */}
      <div className="bg-white border-b">
        <div className="max-w-7xl mx-auto px-4">
          <nav className="flex space-x-8">
            <button
              onClick={() => setActiveTab('dashboard')}
              className={`py-4 px-1 border-b-2 font-medium text-sm ${
                activeTab === 'dashboard'
                  ? 'border-blue-500 text-blue-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
              }`}
            >
              Dashboard
            </button>
            <button
              onClick={() => setActiveTab('security')}
              className={`py-4 px-1 border-b-2 font-medium text-sm ${
                activeTab === 'security'
                  ? 'border-blue-500 text-blue-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
              }`}
            >
              🔐 Security Settings
            </button>
          </nav>
        </div>
      </div>

      {/* Main Content */}
      <main className="max-w-7xl mx-auto px-4 py-6 space-y-6">
        {activeTab === 'dashboard' ? (
          <>
            {/* Top Row: User Info + AI Chat */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              {/* User Info Card */}
              <div className="bg-white rounded-xl shadow-sm p-6">
                <h2 className="text-lg font-semibold text-gray-900 mb-4">User Profile</h2>
                <div className="space-y-3">
                  <div>
                    <span className="text-sm text-gray-500">Username</span>
                    <p className="font-medium text-gray-900">{username}</p>
                  </div>
                  <div>
                    <span className="text-sm text-gray-500">Roles</span>
                    <div className="flex gap-2 mt-1">
                      {roles.map((role) => (
                        <span
                          key={role}
                          className={`px-3 py-1 rounded-full text-sm font-medium ${
                            role === 'ADMIN' 
                              ? 'bg-purple-100 text-purple-800' 
                              : 'bg-blue-100 text-blue-800'
                          }`}
                        >
                          {role}
                        </span>
                      ))}
                    </div>
                  </div>
                </div>
              </div>

              {/* AI Chat */}
              <AiChat />
            </div>

            {/* Admin Panel - Only visible for admins */}
            {hasRole('ADMIN') && <AdminPanel />}

            {/* Audit Logs Card */}
            <div className="bg-white rounded-xl shadow-sm p-6">
              <h2 className="text-lg font-semibold text-gray-900 mb-4">Security Audit Log</h2>
              
              {isLoading ? (
                <div className="flex justify-center py-8">
                  <svg className="animate-spin h-8 w-8 text-blue-600" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                  </svg>
                </div>
              ) : error ? (
                <div className="p-4 bg-red-50 rounded-lg">
                  <p className="text-red-600">{error}</p>
                </div>
              ) : auditLogs.length === 0 ? (
                <div className="text-center py-8 text-gray-500">
                  No audit logs found
                </div>
              ) : (
                <div className="overflow-x-auto">
                  {/* Mobile View */}
                  <div className="sm:hidden space-y-4">
                    {auditLogs.map((log) => (
                      <div key={log.id} className="border rounded-lg p-4 space-y-2">
                        <div className="flex justify-between items-start">
                          <span className={`px-2 py-1 rounded text-xs font-medium ${
                            log.success ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'
                          }`}>
                            {log.action}
                          </span>
                          <span className="text-xs text-gray-500">{formatDate(log.timestamp)}</span>
                        </div>
                        <p className="text-sm text-gray-900">{log.username}</p>
                        {log.ipAddress && (
                          <p className="text-xs text-gray-500">IP: {log.ipAddress}</p>
                        )}
                      </div>
                    ))}
                  </div>

                  {/* Desktop View */}
                  <table className="hidden sm:table w-full">
                    <thead>
                      <tr className="border-b">
                        <th className="text-left py-3 px-4 text-sm font-medium text-gray-500">Time</th>
                        <th className="text-left py-3 px-4 text-sm font-medium text-gray-500">Action</th>
                        <th className="text-left py-3 px-4 text-sm font-medium text-gray-500">User</th>
                        <th className="text-left py-3 px-4 text-sm font-medium text-gray-500">IP Address</th>
                        <th className="text-left py-3 px-4 text-sm font-medium text-gray-500">Status</th>
                      </tr>
                    </thead>
                    <tbody>
                      {auditLogs.map((log) => (
                        <tr key={log.id} className="border-b hover:bg-gray-50">
                          <td className="py-3 px-4 text-sm text-gray-600">{formatDate(log.timestamp)}</td>
                          <td className="py-3 px-4 text-sm font-medium text-gray-900">{log.action}</td>
                          <td className="py-3 px-4 text-sm text-gray-600">{log.username}</td>
                          <td className="py-3 px-4 text-sm text-gray-600">{log.ipAddress || '-'}</td>
                          <td className="py-3 px-4">
                            <span className={`px-2 py-1 rounded text-xs font-medium ${
                              log.success ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'
                            }`}>
                              {log.success ? 'Success' : 'Failed'}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </>
        ) : (
          /* Security Settings Tab */
          <div className="space-y-6">
            <div>
              <h2 className="text-2xl font-bold text-gray-900">Security Settings</h2>
              <p className="mt-1 text-gray-500">Manage your account security preferences</p>
            </div>
            
            <MfaSettings />
          </div>
        )}
      </main>
    </div>
  );
}
