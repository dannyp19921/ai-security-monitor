// frontend/src/components/admin/AdminPanel.tsx 
import { useState, useEffect } from 'react';
import { adminService } from '../../services/api';
import { Button } from '../ui/Button';
import type { UserResponse } from '../../types';

export function AdminPanel() {
  const [users, setUsers] = useState<UserResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');

  const fetchUsers = async () => {
    try {
      const data = await adminService.getAllUsers();
      setUsers(data);
      setError('');
    } catch (err) {
      setError('Failed to load users. You may not have admin access.');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchUsers();
  }, []);

  const handleAddRole = async (userId: number, roleName: string) => {
    try {
      await adminService.addRoleToUser(userId, roleName);
      fetchUsers();
    } catch (err) {
      setError('Failed to add role');
    }
  };

  const handleRemoveRole = async (userId: number, roleName: string) => {
    try {
      await adminService.removeRoleFromUser(userId, roleName);
      fetchUsers();
    } catch (err) {
      setError('Failed to remove role');
    }
  };

  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleString();
  };

  if (isLoading) {
    return (
      <div className="bg-white rounded-xl shadow-sm p-6">
        <div className="flex justify-center py-8">
          <svg className="animate-spin h-8 w-8 text-blue-600" viewBox="0 0 24 24">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
          </svg>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="bg-white rounded-xl shadow-sm p-6">
        <h2 className="text-lg font-semibold text-gray-900 mb-4">Admin Panel</h2>
        <div className="p-4 bg-red-50 rounded-lg">
          <p className="text-red-600">{error}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="bg-white rounded-xl shadow-sm p-6">
      <h2 className="text-lg font-semibold text-gray-900 mb-4">Admin Panel - User Management</h2>
      
      <div className="overflow-x-auto">
        {/* Mobile View */}
        <div className="sm:hidden space-y-4">
          {users.map((user) => (
            <div key={user.id} className="border rounded-lg p-4 space-y-3">
              <div>
                <p className="font-medium text-gray-900">{user.username}</p>
                <p className="text-sm text-gray-500">{user.email}</p>
              </div>
              <div className="flex flex-wrap gap-2">
                {user.roles.map((role) => (
                  <span
                    key={role}
                    className="inline-flex items-center gap-1 px-2 py-1 bg-blue-100 text-blue-800 rounded-full text-xs font-medium"
                  >
                    {role}
                    {role !== 'USER' && (
                      <button
                        onClick={() => handleRemoveRole(user.id, role)}
                        className="text-blue-600 hover:text-red-600"
                      >
                        ×
                      </button>
                    )}
                  </span>
                ))}
              </div>
              {!user.roles.includes('ADMIN') && (
                <Button
                  variant="secondary"
                  onClick={() => handleAddRole(user.id, 'ADMIN')}
                  className="!w-auto !py-1 text-sm"
                >
                  Make Admin
                </Button>
              )}
            </div>
          ))}
        </div>

        {/* Desktop View */}
        <table className="hidden sm:table w-full">
          <thead>
            <tr className="border-b">
              <th className="text-left py-3 px-4 text-sm font-medium text-gray-500">User</th>
              <th className="text-left py-3 px-4 text-sm font-medium text-gray-500">Email</th>
              <th className="text-left py-3 px-4 text-sm font-medium text-gray-500">Roles</th>
              <th className="text-left py-3 px-4 text-sm font-medium text-gray-500">Created</th>
              <th className="text-left py-3 px-4 text-sm font-medium text-gray-500">Actions</th>
            </tr>
          </thead>
          <tbody>
            {users.map((user) => (
              <tr key={user.id} className="border-b hover:bg-gray-50">
                <td className="py-3 px-4 text-sm font-medium text-gray-900">{user.username}</td>
                <td className="py-3 px-4 text-sm text-gray-600">{user.email}</td>
                <td className="py-3 px-4">
                  <div className="flex flex-wrap gap-1">
                    {user.roles.map((role) => (
                      <span
                        key={role}
                        className="inline-flex items-center gap-1 px-2 py-1 bg-blue-100 text-blue-800 rounded-full text-xs font-medium"
                      >
                        {role}
                        {role !== 'USER' && (
                          <button
                            onClick={() => handleRemoveRole(user.id, role)}
                            className="text-blue-600 hover:text-red-600"
                          >
                            ×
                          </button>
                        )}
                      </span>
                    ))}
                  </div>
                </td>
                <td className="py-3 px-4 text-sm text-gray-600">{formatDate(user.createdAt)}</td>
                <td className="py-3 px-4">
                  {!user.roles.includes('ADMIN') && (
                    <Button
                      variant="secondary"
                      onClick={() => handleAddRole(user.id, 'ADMIN')}
                      className="!w-auto !py-1 text-sm"
                    >
                      Make Admin
                    </Button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}