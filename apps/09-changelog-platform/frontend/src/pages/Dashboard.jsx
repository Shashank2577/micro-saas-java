import { useEffect, useState } from 'react';
import { analyticsApi } from '../api/client';

const DEV_TENANT_ID = '550e8400-e29b-41d4-a716-446655440000';

export function Dashboard() {
  const [stats, setStats] = useState({
    totalProjects: 0,
    totalPosts: 0,
    publishedPosts: 0,
  });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const loadStats = async () => {
      try {
        const response = await analyticsApi.getStats(DEV_TENANT_ID);
        setStats(response.data);
      } catch (error) {
        console.error('Failed to load stats:', error);
      } finally {
        setLoading(false);
      }
    };

    loadStats();
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center py-24">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
      </div>
    );
  }

  return (
    <div className="space-y-8 animate-in fade-in duration-700 slide-in-from-bottom-4">
      <div className="border-b border-gray-200 pb-5">
        <h2 className="text-3xl font-extrabold text-gray-900 tracking-tight font-sans">
          Platform Overview
        </h2>
        <p className="text-lg text-gray-500 mt-2">
          Real-time metrics for your SaaS Operating System.
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-8 transition-all hover:shadow-md hover:border-blue-100 group">
          <div className="flex items-center justify-between">
            <span className="text-sm font-semibold text-blue-600 uppercase tracking-wider">Inventory</span>
            <svg className="h-6 w-6 text-blue-400 group-hover:text-blue-600 transition-colors" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
            </svg>
          </div>
          <div className="text-4xl font-black text-gray-900 mt-4 tracking-tighter font-mono">{stats.totalProjects}</div>
          <div className="text-base font-medium text-gray-500 mt-1">Total Projects</div>
        </div>

        <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-8 transition-all hover:shadow-md hover:border-green-100 group">
          <div className="flex items-center justify-between">
            <span className="text-sm font-semibold text-green-600 uppercase tracking-wider">Activity</span>
            <svg className="h-6 w-6 text-green-400 group-hover:text-green-600 transition-colors" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
            </svg>
          </div>
          <div className="text-4xl font-black text-gray-900 mt-4 tracking-tighter font-mono">{stats.totalPosts}</div>
          <div className="text-base font-medium text-gray-500 mt-1">Total Posts</div>
        </div>

        <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-8 transition-all hover:shadow-md hover:border-purple-100 group">
          <div className="flex items-center justify-between">
            <span className="text-sm font-semibold text-purple-600 uppercase tracking-wider">Reach</span>
            <svg className="h-6 w-6 text-purple-400 group-hover:text-purple-600 transition-colors" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
            </svg>
          </div>
          <div className="text-4xl font-black text-gray-900 mt-4 tracking-tighter font-mono">{stats.publishedPosts}</div>
          <div className="text-base font-medium text-gray-500 mt-1">Published Posts</div>
        </div>
      </div>

      <div className="bg-white rounded-2xl border border-gray-200 p-10 overflow-hidden relative">
        <div className="absolute top-0 right-0 -mt-4 -mr-4 h-32 w-32 bg-blue-50 rounded-full blur-3xl opacity-50"></div>
        <h3 className="text-xl font-bold text-gray-900 mb-8 flex items-center">
          <svg className="h-6 w-6 mr-2 text-blue-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" />
          </svg>
          Quick Start Guide
        </h3>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
          <div className="space-y-3">
            <div className="h-10 w-10 bg-blue-600 text-white rounded-xl flex items-center justify-center font-bold text-lg shadow-blue-200 shadow-lg">1</div>
            <h4 className="font-bold text-gray-900">Define Product</h4>
            <p className="text-sm text-gray-600 leading-relaxed">Create a project to represent your product or service and customize its branding.</p>
          </div>
          <div className="space-y-3">
            <div className="h-10 w-10 bg-blue-600 text-white rounded-xl flex items-center justify-center font-bold text-lg shadow-blue-200 shadow-lg">2</div>
            <h4 className="font-bold text-gray-900">Draft Updates</h4>
            <p className="text-sm text-gray-600 leading-relaxed">Write release notes, bug fixes, or performance improvements as posts.</p>
          </div>
          <div className="space-y-3">
            <div className="h-10 w-10 bg-blue-600 text-white rounded-xl flex items-center justify-center font-bold text-lg shadow-blue-200 shadow-lg">3</div>
            <h4 className="font-bold text-gray-900">Ship Value</h4>
            <p className="text-sm text-gray-600 leading-relaxed">Publish your posts to your unique public changelog and notify your subscribers.</p>
          </div>
        </div>
      </div>
    </div>
  );
}
