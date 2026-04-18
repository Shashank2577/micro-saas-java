import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { postApi, projectApi } from '../api/client';
import { usePostStore } from '../store';
import { Save, Eye } from 'lucide-react';

export function PostEditor() {
  const { projectId, postId } = useParams();
  const navigate = useNavigate();
  const [project, setProject] = useState(null);
  const [formData, setFormData] = useState({
    title: '',
    summary: '',
    content: '',
    status: 'DRAFT',
  });
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    loadData();
  }, [projectId, postId]);

  const loadData = async () => {
    try {
      const projResponse = await projectApi.get(projectId);
      setProject(projResponse.data);

      if (postId) {
        const postResponse = await postApi.get(postId);
        setFormData(postResponse.data);
      }
    } catch (error) {
      console.error('Failed to load data:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      if (postId) {
        await postApi.update(postId, formData);
      } else {
        await postApi.create(projectId, formData);
      }
      navigate(`/projects/${projectId}/posts`);
    } catch (error) {
      console.error('Failed to save post:', error);
      alert('Failed to save post');
    } finally {
      setSaving(false);
    }
  };

  const handlePublish = async () => {
    setSaving(true);
    try {
      if (postId) {
        await postApi.publish(postId);
        navigate(`/projects/${projectId}/posts`);
      } else {
        const newPost = await postApi.create(projectId, { ...formData, status: 'PUBLISHED' });
        navigate(`/projects/${projectId}/posts`);
      }
    } catch (error) {
      console.error('Failed to publish:', error);
      alert('Failed to publish post');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return <div className="text-center py-12">Loading...</div>;
  }

  return (
    <div className="space-y-6 max-w-4xl">
      <div className="flex justify-between items-center">
        <div>
          <h2 className="text-3xl font-bold text-gray-900">{postId ? 'Edit Post' : 'New Post'}</h2>
          <p className="text-gray-600 mt-1">{project?.name}</p>
        </div>
        <div className="flex gap-2">
          <button
            onClick={handleSave}
            disabled={saving}
            className="flex items-center gap-2 bg-gray-600 text-white px-4 py-2 rounded-lg hover:bg-gray-700 disabled:opacity-50"
          >
            <Save size={20} />
            Save Draft
          </button>
          <button
            onClick={handlePublish}
            disabled={saving}
            className="flex items-center gap-2 bg-green-600 text-white px-4 py-2 rounded-lg hover:bg-green-700 disabled:opacity-50"
          >
            <Eye size={20} />
            {postId ? 'Update' : 'Publish'}
          </button>
        </div>
      </div>

      <div className="bg-white rounded-lg shadow p-6 space-y-6">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">Title</label>
          <input
            type="text"
            value={formData.title}
            onChange={(e) => setFormData({ ...formData, title: e.target.value })}
            className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
            placeholder="e.g., v2.0 Launch"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">Summary</label>
          <input
            type="text"
            value={formData.summary}
            onChange={(e) => setFormData({ ...formData, summary: e.target.value })}
            className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
            placeholder="One-line summary"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">
            Content (Markdown)
          </label>
          <textarea
            value={formData.content}
            onChange={(e) => setFormData({ ...formData, content: e.target.value })}
            className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 font-mono text-sm"
            placeholder="# What's New&#10;&#10;- Feature 1&#10;- Feature 2"
            rows="12"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-2">Status</label>
          <select
            value={formData.status}
            onChange={(e) => setFormData({ ...formData, status: e.target.value })}
            className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
          >
            <option value="DRAFT">Draft</option>
            <option value="PUBLISHED">Published</option>
            <option value="SCHEDULED">Scheduled</option>
          </select>
        </div>
      </div>
    </div>
  );
}
