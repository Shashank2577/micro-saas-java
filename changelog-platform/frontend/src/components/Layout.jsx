import { useState } from 'react';
import { Menu, X } from 'lucide-react';
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
        } bg-gray-900 text-white transition-all duration-300 flex flex-col`}
      >
        <div className="p-6 border-b border-gray-800">
          <h1 className="text-xl font-bold">Changelog</h1>
          <p className="text-sm text-gray-400">Admin Dashboard</p>
        </div>

        <nav className="flex-1 p-4 space-y-2">
          <a href="/" className="block px-4 py-2 rounded-lg hover:bg-gray-800 transition">
            📊 Dashboard
          </a>
          <a href="/projects" className="block px-4 py-2 rounded-lg hover:bg-gray-800 transition">
            📁 Projects
          </a>
          <a href="/settings" className="block px-4 py-2 rounded-lg hover:bg-gray-800 transition">
            ⚙️ Settings
          </a>
        </nav>

        <div className="p-4 border-t border-gray-800">
          <p className="text-xs text-gray-500">v0.0.1-SNAPSHOT</p>
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
