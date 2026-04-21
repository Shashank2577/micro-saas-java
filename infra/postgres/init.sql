-- SaaS OS — PostgreSQL initialization
-- Creates the keycloak schema for Keycloak to use within the changelog database

CREATE SCHEMA IF NOT EXISTS keycloak;
GRANT ALL PRIVILEGES ON SCHEMA keycloak TO changelog;

-- pgvector extension for App 03 (AI Knowledge Base semantic search)
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
