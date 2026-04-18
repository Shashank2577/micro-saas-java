import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { Layout } from './components/Layout';
import { Dashboard } from './pages/Dashboard';
import { Projects } from './pages/Projects';
import { PostsList } from './pages/PostsList';
import { PostEditor } from './pages/PostEditor';
import './index.css';

function App() {
  return (
    <BrowserRouter>
      <Layout>
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/projects" element={<Projects />} />
          <Route path="/projects/:projectId/posts" element={<PostsList />} />
          <Route path="/projects/:projectId/posts/new" element={<PostEditor />} />
          <Route path="/projects/:projectId/posts/:postId/edit" element={<PostEditor />} />
        </Routes>
      </Layout>
    </BrowserRouter>
  );
}

export default App;
