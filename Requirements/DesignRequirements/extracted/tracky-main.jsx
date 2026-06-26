// Tracky Main Screens — Overview, Detail, EditText

/* ── Project Card ────────────────────────────────────────── */
function ProjectCard({ project: p, c, onClick }) {
  const [pressed, setPressed] = useState(false);
  const statusLine = { active: c.primary, paused: c.secondary, done: c.tertiary };
  return (
    <div
      onClick={onClick}
      onMouseDown={() => setPressed(true)}
      onMouseUp={() => setPressed(false)}
      onMouseLeave={() => setPressed(false)}
      style={{
        background: c.surfaceLow, borderRadius: 14,
        boxShadow: pressed ? '0 1px 2px rgba(0,0,0,0.06)' : '0 1px 2px rgba(0,0,0,0.05), 0 2px 8px rgba(0,0,0,0.06)',
        cursor: 'pointer', overflow: 'hidden',
        transform: pressed ? 'scale(0.984)' : 'scale(1)',
        transition: 'box-shadow 0.15s, transform 0.1s',
        position: 'relative', borderLeft: `3px solid ${statusLine[p.status] || c.primary}`,
      }}>
      {pressed && <div style={{ position: 'absolute', inset: 0, background: c.primary, opacity: 0.06, pointerEvents: 'none' }} />}
      <div style={{ padding: '14px 14px 10px' }}>
        <div style={{ display: 'flex', alignItems: 'flex-start', gap: 8, marginBottom: 6 }}>
          <span style={{ flex: 1, fontSize: 15, fontWeight: 600, color: c.onSurface, fontFamily: 'Inter, sans-serif', lineHeight: 1.3 }}>{p.title}</span>
          <StatusBadge status={p.status} c={c} />
        </div>
        <p style={{ fontSize: 13, color: c.onSurfaceVariant, fontFamily: 'Inter, sans-serif', lineHeight: 1.5, margin: 0, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{p.desc}</p>
      </div>
      <div style={{ height: 1, background: c.outlineVariant, margin: '0 14px' }} />
      <div style={{ display: 'flex', padding: '9px 14px 12px', gap: 0 }}>
        {[
          { label: 'Duration', value: fmtDuration(p.totalSecs), mono: true },
          { label: 'Sessions', value: p.sessionCount },
          { label: 'Last active', value: p.lastActive },
        ].map((m, i) => (
          <div key={i} style={{ flex: i === 2 ? 1.4 : 1, display: 'flex', flexDirection: 'column', gap: 2, borderLeft: i > 0 ? `1px solid ${c.outlineVariant}` : 'none', paddingLeft: i > 0 ? 10 : 0, marginLeft: i > 0 ? 10 : 0 }}>
            <span style={{ fontSize: 10, fontWeight: 700, letterSpacing: '0.06em', color: c.outline, textTransform: 'uppercase', fontFamily: 'Inter, sans-serif' }}>{m.label}</span>
            <span style={{ fontSize: 12, fontWeight: m.mono ? 500 : 400, color: c.onSurfaceVariant, fontFamily: m.mono ? 'RobotoMono, monospace' : 'Inter, sans-serif', letterSpacing: m.mono ? '0.03em' : 0 }}>{m.value}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

/* ── Overview Screen ─────────────────────────────────────── */
function OverviewScreen({ c, navigate, userName, variation }) {
  const [projects, setProjects] = useState(JSON.parse(JSON.stringify(window.MOCK_PROJECTS)));
  const [searchActive, setSearchActive] = useState(false);
  const [query, setQuery] = useState('');
  const [filterStatus, setFilterStatus] = useState('all');
  const [sortBy, setSortBy] = useState('lastModified');
  const [showSortMenu, setShowSortMenu] = useState(false);
  const [showUserMenu, setShowUserMenu] = useState(false);
  const [showSheet, setShowSheet] = useState(false);
  const [newTitle, setNewTitle] = useState('');
  const [newDesc, setNewDesc] = useState('');
  const [appBarVisible, setAppBarVisible] = useState(true);
  const [fabExpanded, setFabExpanded] = useState(true);
  const lastScrollY = useRef(0);
  const scrollRef = useRef(null);
  const initials = (userName || 'Alex Johnson').split(' ').map(w => w[0]).join('').slice(0, 2).toUpperCase();

  const handleScroll = (e) => {
    const y = e.target.scrollTop;
    const going = y > lastScrollY.current;
    if (Math.abs(y - lastScrollY.current) > 8) {
      setAppBarVisible(!going);
      setFabExpanded(!going);
      if (going) { setShowSortMenu(false); setShowUserMenu(false); }
    }
    lastScrollY.current = y;
  };

  const filtered = projects
    .filter(p => filterStatus === 'all' || p.status === filterStatus)
    .filter(p => !query || p.title.toLowerCase().includes(query.toLowerCase()))
    .sort((a, b) => sortBy === 'created' ? a.createdAt.localeCompare(b.createdAt) : b.lastActive.localeCompare(a.lastActive));

  const filterLabels = [
    { key: 'all', label: 'All' },
    { key: 'active', label: 'Active' },
    { key: 'paused', label: 'Paused' },
    { key: 'done', label: 'Done' },
  ];

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', background: c.bg, position: 'relative', overflow: 'hidden' }}>
      {/* App Bar */}
      <div style={{
        background: variation === 'B' ? c.primaryContainer : c.bg,
        borderBottom: `1px solid ${c.outlineVariant}`,
        flexShrink: 0, zIndex: 10,
        transform: appBarVisible ? 'translateY(0)' : 'translateY(-100%)',
        transition: 'transform 0.25s ease',
        position: 'relative',
      }}>
        {searchActive ? (
          /* Search expanded */
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '10px 12px' }}>
            <IconBtn icon={<Icon name="arrowBack" size={20} color={c.onSurface} />} onClick={() => { setSearchActive(false); setQuery(''); }} c={c} />
            <input
              autoFocus
              value={query}
              onChange={e => setQuery(e.target.value)}
              placeholder="Search projects..."
              style={{
                flex: 1, border: 'none', background: c.surfaceMid, borderRadius: 9999,
                padding: '9px 16px', fontSize: 14, color: c.onSurface,
                fontFamily: 'Inter, sans-serif', outline: 'none',
              }}
            />
            {query && <IconBtn icon={<Icon name="x" size={18} color={c.onSurfaceVariant} />} onClick={() => setQuery('')} c={c} size={36} />}
          </div>
        ) : (
          /* Normal app bar */
          <div style={{ display: 'flex', alignItems: 'center', padding: '10px 12px 10px 16px', gap: 4 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, flex: 1 }}>
              <div style={{ width: 28, height: 28, borderRadius: 8, background: c.primaryContainer, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <Icon name="timer" size={16} color={c.primary} />
              </div>
              <span style={{ fontSize: 18, fontWeight: 700, color: variation === 'B' ? c.onPrimaryContainer : c.primary, fontFamily: 'Inter, sans-serif', letterSpacing: -0.3 }}>Tracky</span>
            </div>
            <IconBtn icon={<Icon name="search" size={20} color={variation === 'B' ? c.onPrimaryContainer : c.onSurface} />} onClick={() => setSearchActive(true)} c={c} />

            {/* User avatar */}
            <div style={{ position: 'relative' }}>
              <button
                onClick={() => { setShowUserMenu(v => !v); setShowSortMenu(false); }}
                style={{
                  width: 36, height: 36, borderRadius: 9999, border: 'none', cursor: 'pointer',
                  background: c.primary, display: 'flex', alignItems: 'center', justifyContent: 'center',
                  fontSize: 13, fontWeight: 700, color: c.onPrimary, fontFamily: 'Inter, sans-serif',
                  boxShadow: `0 0 0 2px ${variation === 'B' ? c.primaryContainer : c.bg}, 0 0 0 3px ${c.primary}`,
                }}>
                {initials}
              </button>

              {/* User dropdown */}
              {showUserMenu && (
                <div
                  onClick={e => e.stopPropagation()}
                  style={{
                    position: 'absolute', top: 44, right: 0, width: 200,
                    background: c.surfaceLow, borderRadius: 14,
                    boxShadow: '0 8px 32px rgba(0,0,0,0.18)', zIndex: 100,
                    overflow: 'hidden', border: `1px solid ${c.outlineVariant}`,
                  }}>
                  {/* Profile header */}
                  <div style={{ padding: '14px 16px 10px', borderBottom: `1px solid ${c.outlineVariant}` }}>
                    <div style={{ fontSize: 14, fontWeight: 600, color: c.onSurface, fontFamily: 'Inter, sans-serif' }}>{userName || 'Alex Johnson'}</div>
                    <div style={{ fontSize: 12, color: c.onSurfaceVariant, fontFamily: 'Inter, sans-serif', marginTop: 2 }}>alex@example.com</div>
                  </div>
                  {[
                    { icon: 'user', label: 'Profile', action: () => setShowUserMenu(false) },
                    { icon: 'settings', label: 'Account settings', action: () => setShowUserMenu(false) },
                    { icon: 'info', label: 'App version 2.4.1', action: null, muted: true },
                  ].map((item, i) => (
                    <button key={i} onClick={item.action || undefined} style={{
                      width: '100%', padding: '10px 16px', background: 'none', border: 'none',
                      display: 'flex', alignItems: 'center', gap: 10, cursor: item.action ? 'pointer' : 'default',
                      borderBottom: `1px solid ${c.outlineVariant}`,
                    }}>
                      <Icon name={item.icon} size={16} color={item.muted ? c.outline : c.onSurfaceVariant} />
                      <span style={{ fontSize: 13, color: item.muted ? c.outline : c.onSurface, fontFamily: 'Inter, sans-serif' }}>{item.label}</span>
                    </button>
                  ))}
                  {/* Theme toggle */}
                  <div style={{ padding: '8px 16px', display: 'flex', alignItems: 'center', gap: 10, borderBottom: `1px solid ${c.outlineVariant}` }}>
                    <Icon name="moon" size={16} color={c.onSurfaceVariant} />
                    <span style={{ fontSize: 13, color: c.onSurface, fontFamily: 'Inter, sans-serif', flex: 1 }}>Dark mode</span>
                    <Toggle checked={c.bg === '#121318'} onChange={() => {}} c={c} />
                  </div>
                  {/* Logout */}
                  <button onClick={() => navigate('login')} style={{
                    width: '100%', padding: '10px 16px', background: 'none', border: 'none',
                    display: 'flex', alignItems: 'center', gap: 10, cursor: 'pointer',
                  }}>
                    <Icon name="logout" size={16} color={c.error} />
                    <span style={{ fontSize: 13, color: c.error, fontFamily: 'Inter, sans-serif', fontWeight: 500 }}>Log out</span>
                  </button>
                </div>
              )}
            </div>
          </div>
        )}

        {/* Filter row */}
        {!searchActive && (
          <div style={{ display: 'flex', alignItems: 'center', padding: '4px 12px 8px', gap: 6, overflowX: 'auto' }}>
            {filterLabels.map(f => (
              <button key={f.key} onClick={() => setFilterStatus(f.key)} style={{
                padding: '5px 14px', borderRadius: 9999, border: 'none', cursor: 'pointer', flexShrink: 0,
                background: filterStatus === f.key ? c.primary : c.surfaceMid,
                color: filterStatus === f.key ? c.onPrimary : c.onSurfaceVariant,
                fontSize: 12, fontWeight: 600, fontFamily: 'Inter, sans-serif', transition: 'all 0.15s',
              }}>{f.label}</button>
            ))}
            <div style={{ flex: 1 }} />
            <div style={{ position: 'relative', flexShrink: 0 }}>
              <button onClick={() => { setShowSortMenu(v => !v); setShowUserMenu(false); }} style={{
                display: 'flex', alignItems: 'center', gap: 5, padding: '5px 12px',
                background: showSortMenu ? c.primaryContainer : c.surfaceMid,
                color: showSortMenu ? c.onPrimaryContainer : c.onSurfaceVariant,
                border: 'none', borderRadius: 9999, cursor: 'pointer',
                fontSize: 12, fontWeight: 600, fontFamily: 'Inter, sans-serif',
              }}>
                <Icon name="sort" size={13} color={showSortMenu ? c.onPrimaryContainer : c.onSurfaceVariant} />
                Sort
              </button>
              {showSortMenu && (
                <div style={{
                  position: 'absolute', top: 36, right: 0, width: 180,
                  background: c.surfaceLow, borderRadius: 12,
                  boxShadow: '0 8px 24px rgba(0,0,0,0.15)', zIndex: 50,
                  border: `1px solid ${c.outlineVariant}`, overflow: 'hidden',
                }}>
                  {[
                    { key: 'lastModified', label: 'Last modified' },
                    { key: 'created', label: 'Date created' },
                  ].map(s => (
                    <button key={s.key} onClick={() => { setSortBy(s.key); setShowSortMenu(false); }} style={{
                      width: '100%', padding: '10px 14px', background: sortBy === s.key ? c.primaryContainer : 'none',
                      border: 'none', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 10,
                    }}>
                      {sortBy === s.key && <Icon name="check" size={14} color={c.primary} />}
                      <span style={{ fontSize: 13, color: c.onSurface, fontFamily: 'Inter, sans-serif', paddingLeft: sortBy !== s.key ? 22 : 0 }}>{s.label}</span>
                    </button>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}
      </div>

      {/* Overlay to close menus */}
      {(showUserMenu || showSortMenu) && (
        <div onClick={() => { setShowUserMenu(false); setShowSortMenu(false); }} style={{ position: 'absolute', inset: 0, zIndex: 9 }} />
      )}

      {/* Project list */}
      <div ref={scrollRef} onScroll={handleScroll} style={{ flex: 1, overflowY: 'auto', padding: '12px 14px 80px', display: 'flex', flexDirection: 'column', gap: 10 }}>
        {filtered.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '48px 0', color: c.onSurfaceVariant, fontFamily: 'Inter, sans-serif', fontSize: 14 }}>No projects found</div>
        ) : (
          <>
            <div style={{ fontSize: 12, fontWeight: 500, color: c.onSurfaceVariant, fontFamily: 'Inter, sans-serif', padding: '2px 4px 4px' }}>
              My current open projects ({filtered.length})
            </div>
            {filtered.map(p => (
              <ProjectCard key={p.id} project={p} c={c} onClick={() => navigate('detail', p)} />
            ))}
          </>
        )}
      </div>

      {/* FAB */}
      <button
        onClick={() => setShowSheet(true)}
        style={{
          position: 'absolute', right: 16, bottom: 20, height: 52,
          borderRadius: 16, border: 'none', cursor: 'pointer',
          background: c.tertiaryContainer,
          display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8,
          padding: fabExpanded ? '0 20px 0 16px' : '0',
          width: fabExpanded ? 'auto' : 52,
          boxShadow: '0 4px 16px rgba(0,0,0,0.18)', zIndex: 5,
          transition: 'all 0.25s ease',
          overflow: 'hidden', whiteSpace: 'nowrap',
        }}>
        <Icon name="plus" size={22} color={c.onTertiaryContainer} />
        {fabExpanded && <span style={{ fontSize: 14, fontWeight: 600, color: c.onTertiaryContainer, fontFamily: 'Inter, sans-serif' }}>New project</span>}
      </button>

      {/* Create project sheet */}
      <BottomSheet open={showSheet} onClose={() => setShowSheet(false)} title="New project" c={c}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <TextField label="Project title" value={newTitle} onChange={setNewTitle} leadingIcon={<Icon name="edit" size={18} color={c.onSurfaceVariant} />} c={c} />
          <TextField label="Description (optional)" value={newDesc} onChange={setNewDesc} c={c} />
          <PrimaryBtn
            label="Create project"
            onClick={() => {
              if (!newTitle.trim()) return;
              const np = {
                id: String(Date.now()), title: newTitle, desc: newDesc || 'No description.',
                totalSecs: 0, status: 'active', lastActive: 'Apr 28, 2026',
                sessionCount: 0, createdAt: '2026-04-28', sessions: [],
              };
              setProjects(ps => [np, ...ps]);
              setShowSheet(false); setNewTitle(''); setNewDesc('');
              navigate('detail', np);
            }}
            c={c}
            disabled={!newTitle.trim()}
          />
        </div>
      </BottomSheet>
    </div>
  );
}

/* ── Session Card ────────────────────────────────────────── */
function SessionCard({ session: s, elapsed, onToggle, onDelete, onCheck, c }) {
  const isRunning = s.running;
  const duration = isRunning ? fmtDuration(elapsed) : fmtDuration(s.secs);
  return (
    <div style={{
      background: isRunning ? `${c.primaryContainer}66` : c.surfaceLow,
      borderRadius: 12, padding: '12px 14px',
      border: `1px solid ${isRunning ? c.primary + '55' : c.outlineVariant}`,
      transition: 'background 0.3s, border-color 0.3s',
      animation: isRunning ? 'sessionPulse 2.5s ease-in-out infinite' : 'none',
      position: 'relative', overflow: 'hidden',
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        {/* Checkbox */}
        <Checkbox checked={s.done} onChange={() => onCheck(s.id)} c={c} />

        {/* Play/Pause */}
        <button
          onClick={() => onToggle(s.id)}
          style={{
            width: 38, height: 38, borderRadius: 9999, border: 'none', cursor: 'pointer', flexShrink: 0,
            background: isRunning ? c.errorContainer : c.primaryContainer,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            transition: 'background 0.2s',
          }}>
          <Icon name={isRunning ? 'pause' : 'play'} size={16} color={isRunning ? c.onErrorContainer : c.onPrimaryContainer} />
        </button>

        {/* Info */}
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ fontSize: 13, fontWeight: 600, color: s.done ? c.onSurfaceVariant : c.onSurface, fontFamily: 'Inter, sans-serif', marginBottom: 2, textDecoration: s.done ? 'line-through' : 'none', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{s.name}</div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <Icon name="calendar" size={11} color={c.outline} />
            <span style={{ fontSize: 11, color: c.outline, fontFamily: 'Inter, sans-serif' }}>{s.startDate}</span>
          </div>
        </div>

        {/* Duration */}
        <div style={{ textAlign: 'right', flexShrink: 0 }}>
          <div style={{ fontFamily: 'RobotoMono, monospace', fontSize: 13, fontWeight: 600, color: isRunning ? c.primary : c.onSurfaceVariant, letterSpacing: '0.03em' }}>
            {duration}
            {isRunning && <span style={{ color: c.error, marginLeft: 4, fontSize: 10 }}>●</span>}
          </div>
        </div>

        {/* Delete */}
        <IconBtn icon={<Icon name="trash" size={15} color={c.error} />} onClick={() => onDelete(s.id)} c={c} size={32} />
      </div>
    </div>
  );
}

/* ── Detail Screen ───────────────────────────────────────── */
function DetailScreen({ c, navigate, project, onBack }) {
  const [proj, setProj] = useState(project || window.MOCK_PROJECTS[0]);
  const [elapsed, setElapsed] = useState(0);
  const [showSheet, setShowSheet] = useState(false);
  const [newSessionName, setNewSessionName] = useState('');
  const [editField, setEditField] = useState(null); // 'title' | 'desc' | null
  const intervalRef = useRef(null);
  const running = proj.sessions.some(s => s.running);

  useEffect(() => {
    if (running) {
      intervalRef.current = setInterval(() => setElapsed(e => e + 1), 1000);
    } else {
      clearInterval(intervalRef.current);
      setElapsed(0);
    }
    return () => clearInterval(intervalRef.current);
  }, [running]);

  const toggleSession = (id) => {
    setProj(p => ({
      ...p,
      sessions: p.sessions.map(s =>
        s.id === id ? { ...s, running: !s.running } : s.running ? { ...s, running: false } : s
      ),
    }));
    setElapsed(0);
  };

  const deleteSession = (id) => setProj(p => ({ ...p, sessions: p.sessions.filter(s => s.id !== id) }));
  const checkSession = (id) => setProj(p => ({ ...p, sessions: p.sessions.map(s => s.id === id ? { ...s, done: !s.done } : s) }));

  const addSession = () => {
    if (!newSessionName.trim()) return;
    setProj(p => ({
      ...p,
      sessions: [...p.sessions, { id: String(Date.now()), name: newSessionName, startDate: 'Apr 28, 2026', secs: 0, running: false, done: false }],
    }));
    setShowSheet(false);
    setNewSessionName('');
  };

  const totalSecs = proj.sessions.reduce((acc, s) => acc + s.secs + (s.running ? elapsed : 0), 0);

  if (editField) {
    return (
      <EditTextScreen
        c={c}
        field={editField}
        value={editField === 'title' ? proj.title : proj.desc}
        onSave={(val) => {
          setProj(p => ({ ...p, [editField === 'title' ? 'title' : 'desc']: val }));
          setEditField(null);
        }}
        onBack={() => setEditField(null)}
      />
    );
  }

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', background: c.bg, position: 'relative' }}>
      {/* Top bar */}
      <div style={{ display: 'flex', alignItems: 'center', padding: '8px 8px 8px 4px', borderBottom: `1px solid ${c.outlineVariant}`, flexShrink: 0 }}>
        <IconBtn icon={<Icon name="arrowBack" size={22} color={c.onSurface} />} onClick={() => navigate('overview')} c={c} />
        <span style={{ flex: 1, fontSize: 15, fontWeight: 600, color: c.onSurface, fontFamily: 'Inter, sans-serif', marginLeft: 4, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{proj.title}</span>
        <IconBtn icon={<Icon name="edit" size={18} color={c.primary} />} onClick={() => setEditField('title')} c={c} />
      </div>

      <div style={{ flex: 1, overflowY: 'auto', padding: '16px 16px 80px' }}>
        {/* Project header */}
        <div style={{ marginBottom: 16 }}>
          <button onClick={() => setEditField('title')} style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 0, textAlign: 'left', width: '100%' }}>
            <h1 style={{ fontSize: 24, fontWeight: 700, color: c.onSurface, fontFamily: 'Inter, sans-serif', margin: 0, lineHeight: 1.2, marginBottom: 8 }}>{proj.title}</h1>
          </button>
          <button onClick={() => setEditField('desc')} style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 0, textAlign: 'left', width: '100%' }}>
            <p style={{ fontSize: 14, color: c.onSurfaceVariant, fontFamily: 'Inter, sans-serif', margin: 0, lineHeight: 1.6 }}>{proj.desc}</p>
          </button>
        </div>

        {/* Total duration chip */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 20, padding: '10px 14px', background: c.primaryContainer, borderRadius: 12 }}>
          <div style={{ width: 32, height: 32, borderRadius: 9999, background: c.primary, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
            <Icon name="timer" size={16} color={c.onPrimary} />
          </div>
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: 11, fontWeight: 700, color: c.onPrimaryContainer, letterSpacing: '0.06em', textTransform: 'uppercase', fontFamily: 'Inter, sans-serif', opacity: 0.7 }}>Total duration</div>
            <div style={{ fontFamily: 'RobotoMono, monospace', fontSize: 20, fontWeight: 600, color: c.onPrimaryContainer, letterSpacing: '0.04em' }}>{fmtDuration(totalSecs)}</div>
          </div>
          <StatusBadge status={proj.status} c={c} />
        </div>

        {/* Sessions header */}
        <div style={{ display: 'flex', alignItems: 'center', marginBottom: 10 }}>
          <span style={{ flex: 1, fontSize: 17, fontWeight: 700, color: c.onSurface, fontFamily: 'Inter, sans-serif' }}>Sessions</span>
          <span style={{ fontSize: 12, color: c.onSurfaceVariant, fontFamily: 'Inter, sans-serif', marginRight: 6 }}>{proj.sessions.length}</span>
          <IconBtn icon={<Icon name="plus" size={18} color={c.primary} />} onClick={() => setShowSheet(true)} c={c} size={34} bgColor={`${c.primary}14`} />
        </div>

        {/* Session list */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {proj.sessions.length === 0 && (
            <div style={{ textAlign: 'center', padding: '32px 0', color: c.onSurfaceVariant, fontFamily: 'Inter, sans-serif', fontSize: 13 }}>No sessions yet — add one below</div>
          )}
          {proj.sessions.map(s => (
            <SessionCard key={s.id} session={s} elapsed={elapsed} onToggle={toggleSession} onDelete={deleteSession} onCheck={checkSession} c={c} />
          ))}
        </div>
      </div>

      {/* FAB */}
      <button onClick={() => setShowSheet(true)} style={{
        position: 'absolute', right: 16, bottom: 20, width: 52, height: 52, borderRadius: 16,
        border: 'none', cursor: 'pointer', background: c.tertiaryContainer,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        boxShadow: '0 4px 16px rgba(0,0,0,0.18)', zIndex: 5,
      }}>
        <Icon name="plus" size={22} color={c.onTertiaryContainer} />
      </button>

      {/* Add session sheet */}
      <BottomSheet open={showSheet} onClose={() => setShowSheet(false)} title="Add session" c={c}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <TextField label="Session name" value={newSessionName} onChange={setNewSessionName} leadingIcon={<Icon name="timer" size={18} color={c.onSurfaceVariant} />} c={c} autoFocus />
          <PrimaryBtn label="Create session" onClick={addSession} c={c} disabled={!newSessionName.trim()} />
        </div>
      </BottomSheet>
    </div>
  );
}

/* ── Edit Text Screen ────────────────────────────────────── */
function EditTextScreen({ c, field, value, onSave, onBack }) {
  const [text, setText] = useState(value);
  const maxLen = field === 'title' ? 80 : 400;
  const label = field === 'title' ? 'Project title' : 'Description';
  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', background: c.bg }}>
      <div style={{ display: 'flex', alignItems: 'center', padding: '8px 12px 8px 4px', borderBottom: `1px solid ${c.outlineVariant}`, flexShrink: 0 }}>
        <IconBtn icon={<Icon name="x" size={20} color={c.onSurface} />} onClick={onBack} c={c} />
        <span style={{ flex: 1, fontSize: 15, fontWeight: 600, color: c.onSurface, fontFamily: 'Inter, sans-serif', marginLeft: 4 }}>{label}</span>
        <button
          onClick={() => onSave(text)}
          disabled={!text.trim()}
          style={{
            padding: '6px 16px', borderRadius: 9999, border: 'none', cursor: 'pointer',
            background: text.trim() ? c.primary : c.surfaceHigh,
            color: text.trim() ? c.onPrimary : c.onSurfaceVariant,
            fontSize: 13, fontWeight: 600, fontFamily: 'Inter, sans-serif',
          }}>Save</button>
      </div>
      <div style={{ flex: 1, padding: '20px 20px', display: 'flex', flexDirection: 'column' }}>
        <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: '0.06em', textTransform: 'uppercase', color: c.primary, fontFamily: 'Inter, sans-serif', marginBottom: 10 }}>{label}</div>
        <textarea
          autoFocus
          value={text}
          onChange={e => setText(e.target.value.slice(0, maxLen))}
          maxLength={maxLen}
          style={{
            flex: 1, background: 'transparent', border: 'none', outline: 'none', resize: 'none',
            fontSize: field === 'title' ? 22 : 15, fontWeight: field === 'title' ? 700 : 400,
            color: c.onSurface, fontFamily: 'Inter, sans-serif', lineHeight: 1.5,
          }}
        />
        <div style={{ fontSize: 11, color: c.outline, fontFamily: 'Inter, sans-serif', textAlign: 'right' }}>{text.length} / {maxLen}</div>
      </div>
    </div>
  );
}

Object.assign(window, { OverviewScreen, DetailScreen, EditTextScreen, ProjectCard, SessionCard });
