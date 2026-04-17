(function () {
  'use strict';

  // ─── Config ────────────────────────────────────────────────────────────────
  var slug = null;
  var position = 'bottom-right';
  var apiBase = '';
  var pollInterval = null;

  function parseConfig() {
    var scripts = document.getElementsByTagName('script');
    var last = scripts[scripts.length - 1];
    slug = last.getAttribute('data-slug') || last.getAttribute('data-changelog') || '';
    position = last.getAttribute('data-position') || 'bottom-right';
    // Allow overriding the API base via data attribute, otherwise infer from script src
    apiBase = last.getAttribute('data-api') || inferApiBase(last.src);
  }

  function inferApiBase(src) {
    // e.g. src = https://cdn.example.com/widget/widget.js
    // → https://your-host.com (use same origin for local dev)
    if (!src) return '';
    var u = document.createElement('a');
    u.href = src;
    // Strip pathname to get origin
    return u.origin;
  }

  // ─── Inject Styles ─────────────────────────────────────────────────────────
  function injectStyles() {
    var css = [
      '.changelog-widget-* { box-sizing: border-box; }',
      '.changelog-widget-bell {',
      '  position: fixed;',
      '  ' + (position === 'bottom-left' ? 'left: 24px;' : 'right: 24px;') + '',
      '  bottom: 24px;',
      '  width: 48px; height: 48px;',
      '  border-radius: 50%;',
      '  background: #2563eb;',
      '  border: none; cursor: pointer;',
      '  display: flex; align-items: center; justify-content: center;',
      '  box-shadow: 0 4px 16px rgba(0,0,0,0.18);',
      '  z-index: 2147483647;',
      '  transition: transform 0.15s ease, background 0.15s ease;',
      '  padding: 0;',
      '}',
      '.changelog-widget-bell:hover { background: #1d4ed8; transform: scale(1.08); }',
      '.changelog-widget-bell.open { background: #1e40af; }',
      '.changelog-widget-bell svg { width: 22px; height: 22px; fill: white; }',
      '.changelog-widget-badge {',
      '  position: absolute;',
      '  top: -4px; ' + (position === 'bottom-left' ? 'left: -4px;' : 'right: -4px;') + '',
      '  min-width: 18px; height: 18px;',
      '  border-radius: 9px;',
      '  background: #ef4444;',
      '  color: white;',
      '  font-size: 11px; font-weight: 700;',
      '  display: flex; align-items: center; justify-content: center;',
      '  padding: 0 4px;',
      '  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;',
      '  line-height: 1;',
      '  pointer-events: none;',
      '}',
      '.changelog-widget-overlay {',
      '  position: fixed; inset: 0;',
      '  background: rgba(0,0,0,0.35);',
      '  z-index: 2147483646;',
      '  opacity: 0;',
      '  transition: opacity 0.25s ease;',
      '  pointer-events: none;',
      '}',
      '.changelog-widget-overlay.visible { opacity: 1; pointer-events: auto; }',
      '.changelog-widget-panel {',
      '  position: fixed;',
      '  ' + (position === 'bottom-left' ? 'left: 0;' : 'right: 0;') + '',
      '  top: 0; bottom: 0;',
      '  width: 380px; max-width: 100vw;',
      '  background: #fff;',
      '  box-shadow: -4px 0 24px rgba(0,0,0,0.12);',
      '  z-index: 2147483647;',
      '  display: flex; flex-direction: column;',
      '  transform: translateX(' + (position === 'bottom-left' ? '-100%' : '100%') + ');',
      '  transition: transform 0.28s cubic-bezier(0.4, 0, 0.2, 1);',
      '  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;',
      '}',
      '.changelog-widget-panel.open { transform: translateX(0); }',
      '.changelog-widget-header {',
      '  padding: 20px 20px 16px;',
      '  border-bottom: 1px solid #e5e7eb;',
      '  display: flex; align-items: center; justify-content: space-between;',
      '  flex-shrink: 0;',
      '}',
      '.changelog-widget-header h3 {',
      '  margin: 0; font-size: 16px; font-weight: 700; color: #111827;',
      '}',
      '.changelog-widget-close {',
      '  background: none; border: none; cursor: pointer;',
      '  padding: 4px; border-radius: 6px;',
      '  color: #6b7280; display: flex; align-items: center;',
      '  transition: background 0.15s;',
      '}',
      '.changelog-widget-close:hover { background: #f3f4f6; }',
      '.changelog-widget-close svg { width: 18px; height: 18px; }',
      '.changelog-widget-body { flex: 1; overflow-y: auto; padding: 8px 0; }',
      '.changelog-widget-empty {',
      '  padding: 48px 20px; text-align: center; color: #9ca3af; font-size: 14px;',
      '}',
      '.changelog-widget-entry {',
      '  display: block; padding: 14px 20px; cursor: pointer;',
      '  text-decoration: none; border-bottom: 1px solid #f3f4f6;',
      '  transition: background 0.12s;',
      '  position: relative;',
      '}',
      '.changelog-widget-entry:hover { background: #f9fafb; }',
      '.changelog-widget-entry.unread::before {',
      '  content: "";',
      '  position: absolute; top: 18px; ' + (position === 'bottom-left' ? 'right: 12px;' : 'left: 8px;') + '',
      '  width: 8px; height: 8px; border-radius: 50%;',
      '  background: #2563eb;',
      '}',
      '.changelog-widget-entry-meta {',
      '  display: flex; align-items: center; gap: 8px; margin-bottom: 5px;',
      '}',
      '.changelog-widget-tag {',
      '  display: inline-block; padding: 2px 8px; border-radius: 4px;',
      '  font-size: 11px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.04em;',
      '  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;',
      '}',
      '.changelog-widget-tag-new { background: #dbeafe; color: #1d4ed8; }',
      '.changelog-widget-tag-improvement { background: #d1fae5; color: #065f46; }',
      '.changelog-widget-tag-bugfix { background: #fee2e2; color: #991b1b; }',
      '.changelog-widget-tag-feature { background: #ede9fe; color: #5b21b6; }',
      '.changelog-widget-tag-breaking { background: #fef3c7; color: #92400e; }',
      '.changelog-widget-tag-default { background: #f3f4f6; color: #374151; }',
      '.changelog-widget-date {',
      '  font-size: 12px; color: #9ca3af;',
      '  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;',
      '}',
      '.changelog-widget-entry-title {',
      '  font-size: 14px; font-weight: 600; color: #111827; margin: 0 0 4px;',
      '  line-height: 1.4;',
      '}',
      '.changelog-widget-entry-desc {',
      '  font-size: 13px; color: #6b7280; margin: 0; line-height: 1.5;',
      '  overflow: hidden; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;',
      '}',
      '.changelog-widget-loading {',
      '  padding: 32px 20px; text-align: center; color: #9ca3af; font-size: 13px;',
      '}',
      '.changelog-widget-footer {',
      '  padding: 12px 20px; border-top: 1px solid #e5e7eb;',
      '  font-size: 12px; color: #9ca3af; text-align: center; flex-shrink: 0;',
      '}',
      '.changelog-widget-footer a {',
      '  color: #2563eb; text-decoration: none;',
      '}',
      '.changelog-widget-footer a:hover { text-decoration: underline; }',
      '@media (max-width: 440px) {',
      '  .changelog-widget-panel { width: 100vw; }',
      '}',
    ].join('\n');

    var tag = document.createElement('style');
    tag.id = 'changelog-widget-styles';
    tag.textContent = css.replace(/\.changelog-widget-\*/g, '.changelog-widget');
    document.head.appendChild(tag);
  }

  // ─── Icons ─────────────────────────────────────────────────────────────────
  var bellSVG = '<svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zm-1-13h2v6h-2zm0 8h2v2h-2z"/></svg>';
  var closeSVG = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>';

  // ─── State ─────────────────────────────────────────────────────────────────
  var entries = [];
  var isOpen = false;

  // ─── LocalStorage ───────────────────────────────────────────────────────────
  function readKey() {
    return 'changelog_read_' + slug;
  }

  function getReadIds() {
    try {
      return JSON.parse(localStorage.getItem(readKey()) || '[]');
    } catch (e) { return []; }
  }

  function markRead(id) {
    var ids = getReadIds();
    if (ids.indexOf(id) === -1) {
      ids.push(id);
      try { localStorage.setItem(readKey(), JSON.stringify(ids)); } catch (e) {}
    }
  }

  function isUnread(id) {
    return getReadIds().indexOf(id) === -1;
  }

  // ─── DOM Elements ───────────────────────────────────────────────────────────
  var bellBtn, overlay, panel, panelBody;

  function buildDOM() {
    // Bell button
    bellBtn = document.createElement('button');
    bellBtn.className = 'changelog-widget-bell';
    bellBtn.setAttribute('aria-label', 'Open changelog');
    bellBtn.innerHTML = bellSVG;
    bellBtn.addEventListener('click', togglePanel);
    document.body.appendChild(bellBtn);

    // Overlay
    overlay = document.createElement('div');
    overlay.className = 'changelog-widget-overlay';
    overlay.addEventListener('click', closePanel);
    document.body.appendChild(overlay);

    // Panel
    panel = document.createElement('div');
    panel.className = 'changelog-widget-panel';
    panel.setAttribute('role', 'dialog');
    panel.setAttribute('aria-label', 'Changelog');
    panel.innerHTML = [
      '<div class="changelog-widget-header">',
      '  <h3>What\'s New</h3>',
      '  <button class="changelog-widget-close" aria-label="Close">' + closeSVG + '</button>',
      '</div>',
      '<div class="changelog-widget-body"></div>',
      '<div class="changelog-widget-footer">',
      '  Powered by <a href="#" target="_blank">Changelog</a>',
      '</div>',
    ].join('');
    panel.querySelector('.changelog-widget-close').addEventListener('click', closePanel);
    document.body.appendChild(panel);
    panelBody = panel.querySelector('.changelog-widget-body');
  }

  // ─── Panel Open/Close ───────────────────────────────────────────────────────
  function openPanel() {
    isOpen = true;
    bellBtn.classList.add('open');
    overlay.classList.add('visible');
    panel.classList.add('open');
    // Trap scroll
    document.body.style.overflow = 'hidden';
  }

  function closePanel() {
    isOpen = false;
    bellBtn.classList.remove('open');
    overlay.classList.remove('visible');
    panel.classList.remove('open');
    document.body.style.overflow = '';
  }

  function togglePanel() {
    if (isOpen) closePanel(); else openPanel();
  }

  // ─── Render ────────────────────────────────────────────────────────────────
  function getTagClass(type) {
    var map = {
      'new': 'changelog-widget-tag-new',
      'improvement': 'changelog-widget-tag-improvement',
      'bugfix': 'changelog-widget-tag-bugfix',
      'feature': 'changelog-widget-tag-feature',
      'breaking': 'changelog-widget-tag-breaking',
    };
    return map[(type || '').toLowerCase()] || 'changelog-widget-tag-default';
  }

  function formatDate(dateStr) {
    if (!dateStr) return '';
    try {
      var d = new Date(dateStr);
      return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
    } catch (e) { return dateStr; }
  }

  function getEntryUrl(entry) {
    // Prefer a direct URL if provided, otherwise construct from slug
    return entry.url || (apiBase + '/changelog/' + slug + '/posts/' + entry.id);
  }

  function render() {
    panelBody.innerHTML = '';

    if (entries.length === 0) {
      panelBody.innerHTML = '<div class="changelog-widget-empty">No updates yet.</div>';
      updateBadge(0);
      return;
    }

    entries.forEach(function (entry) {
      var unread = isUnread(entry.id);
      var tagClass = getTagClass(entry.type);
      var tagLabel = entry.type || 'Update';
      var url = getEntryUrl(entry);

      var a = document.createElement('a');
      a.className = 'changelog-widget-entry' + (unread ? ' unread' : '');
      a.href = url;
      a.target = '_blank';
      a.rel = 'noopener noreferrer';
      a.innerHTML = [
        '<div class="changelog-widget-entry-meta">',
        '  <span class="changelog-widget-tag ' + tagClass + '">' + tagLabel + '</span>',
        '  <span class="changelog-widget-date">' + formatDate(entry.publishedAt || entry.createdAt) + '</span>',
        '</div>',
        '<p class="changelog-widget-entry-title">' + (entry.title || '') + '</p>',
        '<p class="changelog-widget-entry-desc">' + (entry.description || entry.excerpt || '') + '</p>',
      ].join('');

      a.addEventListener('click', function (e) {
        if (unread) {
          markRead(entry.id);
          updateBadge();
          // Re-render to remove unread dot immediately
          setTimeout(function () { renderEntry(a, entry); }, 0);
        }
      });

      panelBody.appendChild(a);
    });

    var unreadCount = entries.filter(function (e) { return isUnread(e.id); }).length;
    updateBadge(unreadCount);
  }

  function renderEntry(el, entry) {
    el.classList.remove('unread');
  }

  function updateBadge(count) {
    // Remove existing badge
    var existing = bellBtn.querySelector('.changelog-widget-badge');
    if (existing) existing.remove();

    if (typeof count === 'undefined') {
      count = entries.filter(function (e) { return isUnread(e.id); }).length;
    }

    if (count > 0) {
      var badge = document.createElement('span');
      badge.className = 'changelog-widget-badge';
      badge.textContent = count > 99 ? '99+' : count;
      bellBtn.appendChild(badge);
    }
  }

  // ─── Fetch ─────────────────────────────────────────────────────────────────
  function fetchEntries(callback) {
    var url = apiBase + '/changelog/' + slug + '/posts?status=published&limit=10';
    var xhr = new XMLHttpRequest();
    xhr.open('GET', url, true);
    xhr.setRequestHeader('Accept', 'application/json');
    xhr.onreadystatechange = function () {
      if (xhr.readyState === 4) {
        if (xhr.status === 200) {
          try {
            var data = JSON.parse(xhr.responseText);
            entries = Array.isArray(data) ? data : (data.posts || data.entries || []);
          } catch (e) {
            entries = [];
          }
        } else {
          entries = [];
        }
        if (callback) callback();
      }
    };
    xhr.send();
  }

  function loadAndRender() {
    fetchEntries(function () {
      render();
    });
  }

  // ─── Polling ───────────────────────────────────────────────────────────────
  function startPolling() {
    if (pollInterval) clearInterval(pollInterval);
    pollInterval = setInterval(loadAndRender, 60000);
  }

  // ─── Init ──────────────────────────────────────────────────────────────────
  function init() {
    if (!slug) return;
    parseConfig();
    injectStyles();
    buildDOM();
    loadAndRender();
    startPolling();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

})();
