import axios from 'axios';

const API_BASE = 'http://localhost:8081/api/v1';

const client = axios.create({
  baseURL: API_BASE,
  headers: {
    'Content-Type': 'application/json',
  },
});

// For local development - no auth required
client.interceptors.request.use((config) => {
  // In production, add JWT token here
  return config;
});

export const projectApi = {
  list: () => client.get('/projects'),
  get: (id) => client.get(`/projects/${id}`),
  create: (data) => client.post('/projects', data),
  update: (id, data) => client.put(`/projects/${id}`, data),
  delete: (id) => client.delete(`/projects/${id}`),
};

export const postApi = {
  list: (projectId) => client.get(`/projects/${projectId}/posts`),
  get: (postId) => client.get(`/posts/${postId}`),
  create: (projectId, data) => client.post(`/projects/${projectId}/posts`, data),
  update: (postId, data) => client.put(`/posts/${postId}`, data),
  publish: (postId) => client.post(`/posts/${postId}/publish`),
  delete: (postId) => client.delete(`/posts/${postId}`),
};

export const analyticsApi = {
  getStats: (tenantId) => client.get(`/analytics/stats/${tenantId}`),
};

export const monetizationApi = {
  createCheckoutSession: (data) => client.post('/monetization/checkout/create-session', data),
  getSession: (sessionId) => client.get(`/monetization/checkout/session/${sessionId}`),
};

export const publicApi = {
  getChangelog: (slug) => client.get(`/changelog/${slug}`),
};

export default client;
