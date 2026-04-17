-- ============================================
-- Sample data for development
-- ============================================

-- Create a demo tenant
INSERT INTO cc.tenants (id, name, slug, plan_tier)
VALUES ('550e8400-e29b-41d4-a716-446655440000', 'Demo Company', 'demo-company', 'startup')
ON CONFLICT (slug) DO NOTHING;

-- Create demo users (Keycloak IDs - replace with real ones in production)
INSERT INTO cc.users (id, tenant_id, email, name, role)
VALUES
    ('550e8400-e29b-41d4-a716-446655440001', '550e8400-e29b-41d4-a716-446655440000', 'admin@demo.com', 'Demo Admin', 'admin'),
    ('550e8400-e29b-41d4-a716-446655440002', '550e8400-e29b-41d4-a716-446655440000', 'editor@demo.com', 'Demo Editor', 'editor')
ON CONFLICT DO NOTHING;

-- Create demo project
INSERT INTO changelog_projects (id, tenant_id, name, slug, description, branding)
VALUES (
    '650e8400-e29b-41d4-a716-446655440000',
    '550e8400-e29b-41d4-a716-446655440000',
    'Demo Product Changelog',
    'demo-product',
    'Public changelog for our demo product',
    '{"primaryColor": "#4F46E5", "fontName": "Inter"}'::jsonb
)
ON CONFLICT DO NOTHING;

-- Create demo tags
INSERT INTO changelog_tags (project_id, name, color)
VALUES
    ('650e8400-e29b-41d4-a716-446655440000', 'New Feature', '#10B981'),
    ('650e8400-e29b-41d4-a716-446655440000', 'Improvement', '#3B82F6'),
    ('650e8400-e29b-41d4-a716-446655440000', 'Bug Fix', '#EF4444')
ON CONFLICT DO NOTHING;

-- Create demo posts
INSERT INTO changelog_posts (project_id, tenant_id, title, summary, content, status, published_at, author_id)
VALUES
    (
        '650e8400-e29b-41d4-a716-446655440000',
        '550e8400-e29b-41d4-a716-446655440000',
        'Welcome to our changelog!',
        'We are excited to launch our public changelog.',
        '# Welcome

This is our first public changelog post. We will be sharing all our product updates here.

## What to expect

- New feature announcements
- Improvements and fixes
- Behind-the-scenes updates

Stay tuned!',
        'published',
        now(),
        '550e8400-e29b-41d4-a716-446655440001'
    ),
    (
        '650e8400-e29b-41d4-a716-446655440000',
        '550e8400-e29b-41d4-a716-446655440000',
        'Coming soon: Dark mode',
        'We are working on a dark mode theme for the entire platform.',
        '# Dark mode is coming

We have heard your feedback and dark mode is in development. We expect to release it next month.

## What will be included

- Full dark theme support
- Automatic system preference detection
- Manual toggle option

Stay tuned for updates!',
        'draft',
        null,
        '550e8400-e29b-41d4-a716-446655440002'
    )
ON CONFLICT DO NOTHING;

-- Create widget config for demo project
INSERT INTO widget_configs (project_id, position, trigger_type, badge_label, primary_color)
VALUES (
    '650e8400-e29b-41d4-a716-446655440000',
    'bottom-right',
    'badge',
    'What''s New',
    '#4F46E5'
)
ON CONFLICT (project_id) DO NOTHING;
