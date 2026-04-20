import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Menu, X, LayoutDashboard, FolderKanban, Settings, CreditCard } from 'lucide-react';
import { useUiStore } from '../store';

export function Layout({ children }) {
  const sidebarOpen = useUiStore((state) => state.sidebarOpen);
  const setSidebarOpen = useUiStore((state) => state.setSidebarOpen);

  return (
    <div className="flex h-screen bg-gray-50">
      {/* Sidebar */}
      <aside
        className={`${
          sidebarOpen ? 'w-64' : 'w-0'
        } bg-gray-900 text-white transition-all duration-300 flex flex-col overflow-hidden`}
      >
        <div className="p-6 border-b border-gray-800">
          <h1 className="text-xl font-bold">SaaS OS</h1>
          <p className="text-sm text-gray-400">Admin Dashboard</p>
        </div>

        <nav className="flex-1 p-4 space-y-2">
          <Link to="/" className="flex items-center gap-3 px-4 py-2 rounded-lg hover:bg-gray-800 transition">
            <LayoutDashboard size={20} />
            Dashboard
          </Link>
          <Link to="/projects" className="flex items-center gap-3 px-4 py-2 rounded-lg hover:bg-gray-800 transition">
            <FolderKanban size={20} />
            Projects
          </Link>
          <Link to="/pricing" className="flex items-center gap-3 px-4 py-2 rounded-lg hover:bg-gray-800 transition">
            <CreditCard size={20} />
            Monetization
          </Link>
          <Link to="/settings" className="flex items-center gap-3 px-4 py-2 rounded-lg hover:bg-gray-800 transition">
            <Settings size={20} />
            Settings
          </Link>
        </nav>

        <div className="p-4 border-t border-gray-800">
          <p className="text-xs text-gray-500">SaaS OS v1.0</p>
        </div>
      </aside>

      {/* Main Content */}
      <div className="flex-1 flex flex-col">
        {/* Top Bar */}
        <header className="bg-white border-b border-gray-200 p-4 flex items-center justify-between">
          <button
            onClick={() => setSidebarOpen(!sidebarOpen)}
            className="p-2 hover:bg-gray-100 rounded-lg"
          >
            {sidebarOpen ? <X size={24} /> : <Menu size={24} />}
          </button>
          <div className="text-sm text-gray-600">
            Logged in as: <span className="font-semibold">Demo User</span>
          </div>
        </header>

        {/* Content */}
        <main className="flex-1 overflow-auto p-6">{children}</main>
      </div>
    </div>
  );
}
