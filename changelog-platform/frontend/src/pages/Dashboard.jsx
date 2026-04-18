import { useEffect, useState } from 'react';
import { projectApi } from '../api/client';

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
        const response = await projectApi.list();
        setStats({
          totalProjects: response.data.length,
          totalPosts: 0,
          publishedPosts: 0,
        });
      } catch (error) {
        console.error('Failed to load stats:', error);
      } finally {
        setLoading(false);
      }
    };

    loadStats();
  }, []);

  if (loading) {
    return <div className="text-center py-12">Loading...</div>;
  }

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-3xl font-bold text-gray-900">Dashboard</h2>
        <p className="text-gray-600 mt-2">Welcome to your changelog platform</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="bg-white rounded-lg shadow p-6">
          <div className="text-3xl font-bold text-blue-600">{stats.totalProjects}</div>
          <div className="text-gray-600 mt-2">Total Projects</div>
        </div>

        <div className="bg-white rounded-lg shadow p-6">
          <div className="text-3xl font-bold text-green-600">{stats.totalPosts}</div>
          <div className="text-gray-600 mt-2">Total Posts</div>
        </div>

        <div className="bg-white rounded-lg shadow p-6">
          <div className="text-3xl font-bold text-purple-600">{stats.publishedPosts}</div>
          <div className="text-gray-600 mt-2">Published Posts</div>
        </div>
      </div>

      <div className="bg-white rounded-lg shadow p-6">
        <h3 className="text-lg font-semibold text-gray-900 mb-4">Quick Start</h3>
        <ol className="space-y-3 text-gray-700">
          <li className="flex items-start">
            <span className="bg-blue-600 text-white rounded-full w-6 h-6 flex items-center justify-center text-sm mr-3 mt-0.5">
              1
            </span>
            <span>Create a new project to get started</span>
          </li>
          <li className="flex items-start">
            <span className="bg-blue-600 text-white rounded-full w-6 h-6 flex items-center justify-center text-sm mr-3 mt-0.5">
              2
            </span>
            <span>Add release notes and updates as posts</span>
          </li>
          <li className="flex items-start">
            <span className="bg-blue-600 text-white rounded-full w-6 h-6 flex items-center justify-center text-sm mr-3 mt-0.5">
              3
            </span>
            <span>Publish posts to your public changelog page</span>
          </li>
        </ol>
      </div>
    </div>
  );
}
