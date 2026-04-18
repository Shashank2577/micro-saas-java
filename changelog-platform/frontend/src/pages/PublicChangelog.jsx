import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { publicApi } from '../api/client';
import ReactMarkdown from 'react-markdown';

export function PublicChangelog() {
  const { slug } = useParams();
  const [changelog, setChangelog] = useState(null);
  const [loading, setLoading] = useState(true);
  const [selectedTag, setSelectedTag] = useState(null);

  useEffect(() => {
    loadChangelog();
  }, [slug]);

  const loadChangelog = async () => {
    try {
      const response = await publicApi.getChangelog(slug || 'demo-product');
      setChangelog(response.data);
    } catch (error) {
      console.error('Failed to load changelog:', error);
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return <div className="text-center py-12">Loading...</div>;
  }

  if (!changelog) {
    return <div className="text-center py-12">Changelog not found</div>;
  }

  const allTags = [...new Set(changelog.posts.flatMap(p => p.tags || []))];
  const filteredPosts = selectedTag
    ? changelog.posts.filter(p => p.tags?.some(t => t.name === selectedTag))
    : changelog.posts;

  return (
    <div className="min-h-screen bg-gradient-to-b from-gray-50 to-white">
      {/* Header */}
      <header className="bg-white border-b border-gray-200 sticky top-0 z-10">
        <div className="max-w-4xl mx-auto px-6 py-8">
          <div
            style={{
              '--primary-color': changelog.branding?.primaryColor || '#4F46E5',
            }}
            className="space-y-2"
          >
            <h1 className="text-4xl font-bold" style={{ color: 'var(--primary-color)' }}>
              {changelog.projectName}
            </h1>
            <p className="text-gray-600">{changelog.description}</p>
            <div className="text-sm text-gray-500 mt-4">
              {changelog.totalPosts} updates
            </div>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="max-w-4xl mx-auto px-6 py-12">
        {/* Tags Filter */}
        {allTags.length > 0 && (
          <div className="mb-8">
            <div className="flex flex-wrap gap-2">
              <button
                onClick={() => setSelectedTag(null)}
                className={`px-4 py-2 rounded-full text-sm font-medium transition ${
                  !selectedTag
                    ? 'bg-blue-600 text-white'
                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                }`}
              >
                All
              </button>
              {allTags.map((tag) => (
                <button
                  key={tag}
                  onClick={() => setSelectedTag(tag)}
                  className={`px-4 py-2 rounded-full text-sm font-medium transition ${
                    selectedTag === tag
                      ? 'bg-blue-600 text-white'
                      : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                  }`}
                >
                  {tag}
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Posts List */}
        {filteredPosts.length === 0 ? (
          <div className="text-center py-12">
            <p className="text-gray-600">No updates yet</p>
          </div>
        ) : (
          <div className="space-y-8">
            {filteredPosts.map((post) => (
              <article
                key={post.id}
                className="pb-8 border-b border-gray-200 last:border-b-0"
              >
                <div className="space-y-4">
                  <div>
                    <h2 className="text-2xl font-bold text-gray-900 mb-2">
                      {post.title}
                    </h2>
                    <p className="text-gray-600 text-sm">
                      {new Date(post.publishedAt).toLocaleDateString('en-US', {
                        year: 'numeric',
                        month: 'long',
                        day: 'numeric',
                      })}
                    </p>
                  </div>

                  {post.summary && (
                    <p className="text-gray-700 font-medium">{post.summary}</p>
                  )}

                  <div className="prose prose-sm max-w-none">
                    <ReactMarkdown>{post.content}</ReactMarkdown>
                  </div>

                  {post.tags && post.tags.length > 0 && (
                    <div className="flex flex-wrap gap-2 pt-4">
                      {post.tags.map((tag) => (
                        <span
                          key={tag}
                          className="inline-block px-3 py-1 bg-blue-100 text-blue-700 text-xs font-semibold rounded-full"
                        >
                          {tag}
                        </span>
                      ))}
                    </div>
                  )}
                </div>
              </article>
            ))}
          </div>
        )}
      </main>

      {/* Footer */}
      <footer className="bg-gray-900 text-gray-300 py-8 border-t border-gray-800">
        <div className="max-w-4xl mx-auto px-6">
          <div className="text-sm">
            <p>Powered by Changelog Platform</p>
            <p className="text-gray-500 mt-2">
              Get updates delivered to your inbox.{' '}
              <a href="/subscribe" className="text-blue-400 hover:text-blue-300">
                Subscribe
              </a>
            </p>
          </div>
        </div>
      </footer>
    </div>
  );
}
