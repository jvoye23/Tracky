// Tracky Design System — Color Tokens
(function () {
  const LIGHT = {
    primary: '#475D92', onPrimary: '#FFFFFF',
    primaryContainer: '#D9E2FF', onPrimaryContainer: '#2F4578',
    secondary: '#575E71', onSecondary: '#FFFFFF',
    secondaryContainer: '#DCE2F9', onSecondaryContainer: '#404659',
    tertiary: '#725572', onTertiary: '#FFFFFF',
    tertiaryContainer: '#FDD7FA', onTertiaryContainer: '#593D59',
    error: '#BA1A1A', onError: '#FFFFFF',
    errorContainer: '#FFDAD6', onErrorContainer: '#93000A',
    bg: '#FAF8FF', onBg: '#1A1B20',
    surface: '#FAF8FF', onSurface: '#1A1B20',
    surfaceVariant: '#E1E2EC', onSurfaceVariant: '#44464F',
    surfaceLow: '#F4F3FA', surfaceMid: '#EEEDF4',
    surfaceHigh: '#E8E7EF', surfaceHighest: '#E2E2E9',
    outline: '#757780', outlineVariant: '#C5C6D0',
    success: '#1A6B3A', successContainer: '#C8F5D8', onSuccessContainer: '#003919',
  };
  const DARK = {
    primary: '#B0C6FF', onPrimary: '#152E60',
    primaryContainer: '#2F4578', onPrimaryContainer: '#D9E2FF',
    secondary: '#C0C6DC', onSecondary: '#2A3042',
    secondaryContainer: '#404659', onSecondaryContainer: '#DCE2F9',
    tertiary: '#E0BBDD', onTertiary: '#412742',
    tertiaryContainer: '#593D59', onTertiaryContainer: '#FDD7FA',
    error: '#FFB4AB', onError: '#690005',
    errorContainer: '#93000A', onErrorContainer: '#FFDAD6',
    bg: '#121318', onBg: '#E2E2E9',
    surface: '#121318', onSurface: '#E2E2E9',
    surfaceVariant: '#44464F', onSurfaceVariant: '#C5C6D0',
    surfaceLow: '#1A1B20', surfaceMid: '#1E1F25',
    surfaceHigh: '#282A2F', surfaceHighest: '#33343A',
    outline: '#8F9099', outlineVariant: '#44464F',
    success: '#6EDB96', successContainer: '#003919', onSuccessContainer: '#C8F5D8',
  };
  window.TRACKY_TOKENS = { LIGHT, DARK };

  window.fmtDuration = (secs) => {
    const h = String(Math.floor(secs / 3600)).padStart(2, '0');
    const m = String(Math.floor((secs % 3600) / 60)).padStart(2, '0');
    const s = String(secs % 60).padStart(2, '0');
    return `${h}:${m}:${s}`;
  };

  window.MOCK_PROJECTS = [
    {
      id: '1', title: 'Website Redesign',
      desc: 'Complete redesign of the company website with updated branding, modern UX patterns and improved performance.',
      totalSecs: 16330, status: 'active', lastActive: 'Apr 27, 2026', sessionCount: 4,
      createdAt: '2026-01-10',
      sessions: [
        { id: 's1', name: 'Discovery & Research', startDate: 'Jan 10, 2026', secs: 5400, running: false, done: false },
        { id: 's2', name: 'Wireframing', startDate: 'Feb 3, 2026', secs: 7200, running: false, done: true },
        { id: 's3', name: 'Visual Design', startDate: 'Mar 14, 2026', secs: 3600, running: true, done: false },
        { id: 's4', name: 'Developer Handoff', startDate: 'Apr 27, 2026', secs: 130, running: false, done: false },
      ],
    },
    {
      id: '2', title: 'Mobile App v2',
      desc: 'Second major version of the Tracky mobile application featuring improved performance and new feature set.',
      totalSecs: 43495, status: 'active', lastActive: 'Apr 28, 2026', sessionCount: 7,
      createdAt: '2025-11-15',
      sessions: [
        { id: 's5', name: 'Feature Planning', startDate: 'Nov 15, 2025', secs: 9000, running: false, done: true },
        { id: 's6', name: 'Architecture', startDate: 'Dec 4, 2025', secs: 12600, running: false, done: true },
        { id: 's7', name: 'UI Implementation', startDate: 'Feb 1, 2026', secs: 21895, running: false, done: false },
      ],
    },
    {
      id: '3', title: 'Backend API Overhaul',
      desc: 'REST API development for new platform microservices including auth, payments and notifications.',
      totalSecs: 29850, status: 'paused', lastActive: 'Mar 10, 2026', sessionCount: 5,
      createdAt: '2025-12-01',
      sessions: [
        { id: 's8', name: 'Schema Design', startDate: 'Dec 1, 2025', secs: 7200, running: false, done: true },
        { id: 's9', name: 'Auth Service', startDate: 'Jan 5, 2026', secs: 10800, running: false, done: true },
        { id: 's10', name: 'Payment Integration', startDate: 'Mar 10, 2026', secs: 11850, running: false, done: false },
      ],
    },
    {
      id: '4', title: 'Q2 Marketing Campaign',
      desc: 'Social media strategy, content creation and ad campaign for the Q2 product launch.',
      totalSecs: 8100, status: 'done', lastActive: 'Feb 28, 2026', sessionCount: 3,
      createdAt: '2026-02-01',
      sessions: [
        { id: 's11', name: 'Strategy', startDate: 'Feb 1, 2026', secs: 3600, running: false, done: true },
        { id: 's12', name: 'Content Creation', startDate: 'Feb 15, 2026', secs: 4500, running: false, done: true },
      ],
    },
  ];
})();
