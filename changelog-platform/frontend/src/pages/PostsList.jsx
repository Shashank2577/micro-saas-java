import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { postApi, projectApi } from '../api/client';
import { usePostStore } from '../store';
import { Plus, Edit, Trash2, Eye } from 'lucide-react';

export function PostsList() {
  const { projectId } = useParams();
  const navigate = useNavigate();
  const posts = usePostStore((state) => state.posts);
  const setPosts = usePostStore((state) => state.setPosts);
  const [project, setProject] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadData();
  }, [projectId]);

  const loadData = async () => {
    try {
      const [projRes, postsRes] = await Promise.all([
        projectApi.get(projectId),
        postApi.list(projectId),
      ]);
      setProject(projRes.data);
      setPosts(postsRes.data);
    } catch (error) {
      console.error('Failed to load data:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleDeletePost = async (postId) => {
    if (window.confirm('Are you sure?')) {
      try {
        await postApi.delete(postId);
        setPosts(posts.filter((p) => p.id !== postId));
      } catch (error) {
        console.error('Failed to delete post:', error);
      }
    }
  };

  const handlePublish = async (postId) => {
    try {
      await postApi.publish(postId);
      await loadData();
    } catch (error) {
      console.error('Failed to publish:', error);
      alert('Failed to publish post');
    }
  };

  if (loading) {
    return <div className="text-center py-12">Loading...</div>;
  }

  const draftPosts = posts.filter((p) => p.status === 'DRAFT');
  const publishedPosts = posts.filter((p) => p.status === 'PUBLISHED');

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h2 className="text-3xl font-bold text-gray-900">Posts</h2>
          <p className="text-gray-600 mt-1">{project?.name}</p>
        </div>
        <button
          onClick={() => navigate(`/projects/${projectId}/posts/new`)}
          className="flex items-center gap-2 bg-blue-600 text-white px-4 py-2 rounded-lg hover:bg-blue-700"
        >
          <Plus size={20} />
          New Post
        </button>
      </div>

      {/* Draft Posts */}
      {draftPosts.length > 0 && (
        <div className="space-y-4">
          <h3 className="text-lg font-semibold text-gray-900">Drafts</h3>
          <div className="space-y-2">
            {draftPosts.map((post) => (
              <div
                key={post.id}
                className="bg-white rounded-lg shadow p-4 flex items-between justify-between hover:shadow-lg transition"
              >
                <div className="flex-1">
                  <h4 className="font-semibold text-gray-900">{post.title}</h4>
                  <p className="text-sm text-gray-600 mt-1">{post.summary}</p>
                </div>
                <div className="flex gap-2 ml-4">
                  <button
                    onClick={() => navigate(`/projects/${projectId}/posts/${post.id}/edit`)}
                    className="flex items-center gap-1 bg-blue-100 text-blue-600 px-3 py-2 rounded-lg hover:bg-blue-200 text-sm"
                  >
                    <Edit size={16} />
                    Edit
                  </button>
                  <button
                    onClick={() => handlePublish(post.id)}
                    className="flex items-center gap-1 bg-green-100 text-green-600 px-3 py-2 rounded-lg hover:bg-green-200 text-sm"
                  >
                    <Eye size={16} />
                    Publish
                  </button>
                  <button
                    onClick={() => handleDeletePost(post.id)}
                    className="flex items-center gap-1 bg-red-100 text-red-600 px-3 py-2 rounded-lg hover:bg-red-200 text-sm"
                  >
                    <Trash2 size={16} />
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Published Posts */}
      {publishedPosts.length > 0 && (
        <div className="space-y-4">
          <h3 className="text-lg font-semibold text-gray-900">Published</h3>
          <div className="space-y-2">
            {publishedPosts.map((post) => (
              <div
                key={post.id}
                className="bg-white rounded-lg shadow p-4 flex items-between justify-between hover:shadow-lg transition"
              >
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    <h4 className="font-semibold text-gray-900">{post.title}</h4>
                    <span className="inline-block px-2 py-1 bg-green-100 text-green-700 text-xs font-semibold rounded">
                      Published
                    </span>
                  </div>
                  <p className="text-sm text-gray-600 mt-1">{post.summary}</p>
                  <p className="text-xs text-gray-500 mt-2">
                    Published on{' '}
                    {new Date(post.publishedAt).toLocaleDateString('en-US', {
                      year: 'numeric',
                      month: 'short',
                      day: 'numeric',
                    })}
                  </p>
                </div>
                <div className="flex gap-2 ml-4">
                  <button
                    onClick={() => navigate(`/projects/${projectId}/posts/${post.id}/edit`)}
                    className="flex items-center gap-1 bg-blue-100 text-blue-600 px-3 py-2 rounded-lg hover:bg-blue-200 text-sm"
                  >
                    <Edit size={16} />
                    Edit
                  </button>
                  <a
                    href={`http://localhost:3001/changelog/${project?.slug}`}
                    target="_blank"
                    rel="noreferrer"
                    className="flex items-center gap-1 bg-gray-100 text-gray-600 px-3 py-2 rounded-lg hover:bg-gray-200 text-sm"
                  >
                    <Eye size={16} />
                    View
                  </a>
                  <button
                    onClick={() => handleDeletePost(post.id)}
                    className="flex items-center gap-1 bg-red-100 text-red-600 px-3 py-2 rounded-lg hover:bg-red-200 text-sm"
                  >
                    <Trash2 size={16} />
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {posts.length === 0 && (
        <div className="text-center py-12 bg-white rounded-lg">
          <p className="text-gray-600">No posts yet</p>
          <button
            onClick={() => navigate(`/projects/${projectId}/posts/new`)}
            className="mt-4 text-blue-600 hover:text-blue-700 font-semibold"
          >
            Create your first post
          </button>
        </div>
      )}
    </div>
  );
}
