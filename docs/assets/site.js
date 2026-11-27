(() => {
  'use strict';
  const REPO = 'assc0d3r/GLYPHCHEMY';
  const RAW_MANIFEST = `https://raw.githubusercontent.com/${REPO}/main/patches-list.json`;
  const LOCAL_MANIFEST = 'assets/patches-list.json';
  const BUILD_IDS = {
    '18.6.3.1002251336-release-arm64-v8a': 'coverage-186',
    '18.8.0.1022711984-beta-arm64-v8a': 'coverage-188',
    '18.5.4.993608717-release-arm64-v8a': 'coverage-185',
    '18.0.3.954559732-release-arm64-v8a': 'coverage-180'
  };
  const CATEGORIES = [
    ['typing', 'Typing & gestures'], ['visuals', 'Themes & visuals'], ['voice', 'Voice typing'],
    ['writing', 'Text & writing'], ['tools', 'Clipboard & tools'], ['layouts', 'Layouts'],
    ['settings', 'Settings & automation'], ['privacy', 'Privacy & cleanup']
  ];
  const CATEGORY_SETS = {
    typing: new Set(['Per-app profiles','Field adaptation','Swipe Left to Delete','Smart bar case swipe','Spacebar double-tap tab/undo','Punctuation spacing','English QWERTY Flick Case','Multilingual Suggestion','Long-Press Editing Shortcuts','Cursor trackpad mode','Swipe Down to Dismiss Keyboard','Latin Globe Key Ignore Interval','Keypress Vibration Intensity Control','Backspace Repeat Haptic Feedback']),
    visuals: new Set(['Gboard Theme Builder','Custom Theme','Theme Follows Dark Mode','Glide Trail Customization','Keyboard Icon Packs','Key Shape Selection','Rounded Keyboard Panel','Frosted Glass','Glass Panel','Glass Press Effect','Translucent Bottom Bar','Immersive Keyboard','G Logo on Spacebar','Emoji Key On Right','Change emoji size','AMOLED dark keyboard','AMOLED Gboard Settings']),
    voice: new Set(['Parakeet voice typing','Parakeet personal dictionary','Rambler voice typing','Rambler personal dictionary','Rambler contact names','Rambler custom instructions','Rambler cursor-aware editing','Rambler app context','Rambler stop after silence','Advanced Voice Typing','Use Bluetooth Microphone','Enable OCR / Scan Text','Rambler Productivity Tools','Rambler Waveform Animation']),
    writing: new Set(['AI Writing Tools','Grammar Checker','Inline Suggestions','Enable Inline Autofill Suggestions','Dictionaries & Predictions','Simple Calculator','Close Proactive Suggestions']),
    tools: new Set(['Web Clipboard','Clipboard Enhancements','Clipboard Custom Character Limit','Quick Insert','Top Toolbar Item Count','Toolbar Editing Buttons','Custom Symbols','Access Points menu style','Incognito Mode Toggle','FTP Server','Floating Web Search','Swipeable Custom Top Row']),
    layouts: new Set(['iOS QWERTY layout','iOS flick layout','Enable split keyboard','Enable accessibility layout','Zhuyin Slide Input','Zhuyin Bottom Row Key Sizes','Zhuyin Quick Traditional/Simplified Toggle','Emojis, stickers & GIFs Tab Order']),
    settings: new Set(['Keyboard Settings','Settings Homepage Override','Developer options','Backup & Restore','Change package name','Custom branding','Enter Key Tasker Event']),
    privacy: new Set(['Block Trackers','Block App Hijacking','Stop Remote Updates','Stop Background Sync','Disable Cloud Backup','Disable Play Services','Offline Only','Remove Permissions','Remove Extra Files','Remove Translate Limit','Add Gboard Signature Bypass','Key Mapper Watchdog'])
  };

  const grid = document.getElementById('patch-grid');
  const search = document.getElementById('patch-search');
  const statusFilter = document.getElementById('status-filter');
  const tabs = document.getElementById('category-tabs');
  const resultCount = document.getElementById('results-count');
  const errorBox = document.getElementById('catalogue-error');
  let patches = [];
  let activeCategory = 'all';

  function categoryFor(name) {
    for (const [key, set] of Object.entries(CATEGORY_SETS)) if (set.has(name)) return key;
    return 'tools';
  }
  function safeText(value) { return String(value ?? ''); }
  function make(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  }
  function updateStats(data) {
    const enabled = data.filter(p => p.use !== false).length;
    document.getElementById('stat-patches').textContent = data.length;
    document.getElementById('all-count').textContent = data.length;
    document.getElementById('manifest-version').textContent = `INVENTORY ${window.__glyphchemyManifestVersion || 'CURRENT'} · ${enabled} ENABLED BY DEFAULT`;
    for (const [build, id] of Object.entries(BUILD_IDS)) {
      const count = data.filter(p => (p.compatiblePackages?.['com.google.android.inputmethod.latin'] || []).includes(build)).length;
      document.getElementById(id).textContent = `${count} entries`;
    }
    for (const tab of tabs.querySelectorAll('[data-category]')) {
      const key = tab.dataset.category;
      if (key === 'all') continue;
      const count = data.filter(p => categoryFor(p.name) === key).length;
      const label = CATEGORIES.find(c => c[0] === key)?.[1] || key;
      tab.textContent = `${label} ${count}`;
    }
  }
  function patchCard(patch) {
    const card = make('article', 'patch-card');
    const top = make('div', 'patch-card-top');
    const category = CATEGORIES.find(c => c[0] === categoryFor(patch.name))?.[1] || 'Patch';
    top.append(make('span', 'patch-category', category.toUpperCase()));
    top.append(make('span', `patch-state ${patch.use === false ? 'disabled' : 'enabled'}`, patch.use === false ? 'DISABLED BY DEFAULT' : 'ENABLED BY DEFAULT'));
    card.append(top, make('h3', '', safeText(patch.name)));
    card.append(make('p', '', safeText(patch.description).replace(/\s+/g, ' ').trim()));
    const chips = make('div', 'patch-meta');
    const versions = patch.compatiblePackages?.['com.google.android.inputmethod.latin'] || [];
    const versionText = versions.length ? `${versions.length} target build${versions.length === 1 ? '' : 's'}` : 'No build listed';
    chips.append(make('span', 'meta-chip', versionText));
    if (patch.dependencies?.length) chips.append(make('span', 'meta-chip', `${patch.dependencies.length} dependenc${patch.dependencies.length === 1 ? 'y' : 'ies'}`));
    if (patch.options?.length) chips.append(make('span', 'meta-chip', `${patch.options.length} configurable option${patch.options.length === 1 ? '' : 's'}`));
    card.append(chips);
    const details = make('details');
    const summary = make('summary', '', 'Details & compatibility +');
    const body = make('div');
    body.append(make('p', '', safeText(patch.description).replace(/\s+/g, ' ').trim()));
    if (patch.dependencies?.length) body.append(make('p', '', `Dependencies: ${patch.dependencies.join(', ')}`));
    if (versions.length) body.append(make('p', '', `Compatible builds: ${versions.join(', ')}`));
    details.append(summary, body);
    card.append(details);
    details.addEventListener('toggle', () => { summary.textContent = details.open ? 'Hide details −' : 'Details & compatibility +'; });
    return card;
  }
  function render() {
    const query = search.value.trim().toLowerCase();
    const status = statusFilter.value;
    const filtered = patches.filter(p => {
      const catMatch = activeCategory === 'all' || categoryFor(p.name) === activeCategory;
      const enabled = p.use !== false;
      const statusMatch = status === 'all' || (status === 'enabled' ? enabled : !enabled);
      const haystack = [p.name, p.description, ...(p.dependencies || [])].join(' ').toLowerCase();
      return catMatch && statusMatch && (!query || haystack.includes(query));
    });
    grid.replaceChildren();
    if (!filtered.length) {
      grid.append(make('div', 'empty-state', 'No patches match those filters. Try another category or search term.'));
    } else {
      const fragment = document.createDocumentFragment();
      filtered.forEach(p => fragment.append(patchCard(p)));
      grid.append(fragment);
    }
    resultCount.textContent = `Showing ${filtered.length} of ${patches.length} manifest entries`;
  }
  async function getManifest() {
    const sources = [LOCAL_MANIFEST, RAW_MANIFEST];
    let lastError;
    for (const url of sources) {
      try {
        const response = await fetch(url, { cache: 'no-cache' });
        if (!response.ok) throw new Error(`${response.status} ${response.statusText}`);
        const manifest = await response.json();
        if (!Array.isArray(manifest.patches)) throw new Error('Patch array missing from manifest');
        window.__glyphchemyManifestVersion = manifest.version || 'CURRENT';
        return manifest.patches;
      } catch (error) { lastError = error; }
    }
    throw lastError || new Error('Manifest unavailable');
  }

  tabs.addEventListener('click', event => {
    const button = event.target.closest('[data-category]');
    if (!button) return;
    activeCategory = button.dataset.category;
    tabs.querySelectorAll('[data-category]').forEach(tab => tab.classList.toggle('is-active', tab === button));
    render();
  });
  search.addEventListener('input', render);
  statusFilter.addEventListener('change', render);
  document.addEventListener('keydown', event => {
    if (event.key === '/' && !['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement.tagName)) {
      event.preventDefault(); search.focus();
    }
    if (event.key === 'Escape' && document.activeElement === search) search.blur();
  });

  const menuToggle = document.querySelector('.menu-toggle');
  const nav = document.getElementById('primary-nav');
  menuToggle.addEventListener('click', () => {
    const open = menuToggle.getAttribute('aria-expanded') !== 'true';
    menuToggle.setAttribute('aria-expanded', String(open));
    menuToggle.setAttribute('aria-label', open ? 'Close navigation' : 'Open navigation');
    nav.classList.toggle('is-open', open);
  });
  nav.addEventListener('click', event => {
    if (event.target.closest('a')) {
      nav.classList.remove('is-open');
      menuToggle.setAttribute('aria-expanded', 'false');
      menuToggle.setAttribute('aria-label', 'Open navigation');
    }
  });

  getManifest().then(data => {
    patches = data;
    updateStats(data);
    errorBox.hidden = true;
    render();
  }).catch(() => {
    grid.replaceChildren(make('div', 'empty-state', 'The patch list could not be loaded. The rest of the site is ready to use.'));
    resultCount.textContent = 'Patch inventory unavailable';
    errorBox.hidden = false;
  });
})();
