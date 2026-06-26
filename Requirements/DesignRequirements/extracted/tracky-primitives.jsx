// Tracky Primitives — PhoneFrame, TextField, Buttons, BottomSheet, StatusBadge

const { useState, useEffect, useRef, useCallback } = React;

/* ── Phone Frame — Google Pixel 9 ────────────────────────── */
function PhoneFrame({ children, c, isDark }) {
  // Pixel 9 palette: Obsidian (dark) / Porcelain (light)
  const bezel     = isDark ? '#1B1B1D' : '#E8E4DF';
  const bezelEdge = isDark ? '#111113' : '#D0CBC4';
  const rail      = isDark ? '#252527' : '#D6D1CB';
  const screenBg  = c.bg;

  return (
    <div style={{
      width: 384, height: 808, position: 'relative', flexShrink: 0,
      // Pixel 9 has noticeably squarer corners (~36 px) vs iPhone
      borderRadius: 44,
      background: bezel,
      boxShadow: isDark
        ? `0 40px 100px rgba(0,0,0,0.75), 0 0 0 1px rgba(255,255,255,0.07), inset 0 1px 0 rgba(255,255,255,0.05)`
        : `0 40px 100px rgba(0,0,0,0.30), 0 0 0 1px ${bezelEdge}, inset 0 1px 0 rgba(255,255,255,0.6)`,
    }}>

      {/* ── Volume down (left, two buttons) ── */}
      <div style={{ position:'absolute', left:-3, top:148, width:3, height:52, background:rail, borderRadius:'3px 0 0 3px' }}/>
      <div style={{ position:'absolute', left:-3, top:212, width:3, height:52, background:rail, borderRadius:'3px 0 0 3px' }}/>
      {/* ── Power button (right) ── */}
      <div style={{ position:'absolute', right:-3, top:176, width:3, height:72, background:rail, borderRadius:'0 3px 3px 0' }}/>

      {/* ── USB-C port indicator at bottom ── */}
      <div style={{ position:'absolute', bottom:10, left:'50%', transform:'translateX(-50%)', width:52, height:5, background:bezelEdge, borderRadius:3 }}/>
      {/* Speaker grilles */}
      {[-18,-9,0,9,18].map(x => (
        <div key={x} style={{ position:'absolute', bottom:6, left:`calc(50% + ${x}px - 1.5px)`, width:3, height:3, background:bezelEdge, borderRadius:9999 }}/>
      ))}

      {/* ── Screen glass ── */}
      <div style={{
        position: 'absolute',
        top: 7, left: 7, right: 7, bottom: 7,
        borderRadius: 38,
        background: screenBg,
        overflow: 'hidden',
        display: 'flex', flexDirection: 'column',
      }}>
        {/* ── Status bar — Pixel style ── */}
        <div style={{
          height: 40, background: screenBg, flexShrink: 0, zIndex: 10,
          display: 'flex', alignItems: 'center',
          padding: '0 20px',
          position: 'relative',
        }}>
          {/* Time left */}
          <span style={{ fontSize: 13, fontWeight: 600, color: c.onBg, fontFamily: 'Inter, sans-serif', letterSpacing: '-0.2px' }}>9:41</span>

          {/* Punch-hole camera — center */}
          <div style={{
            position: 'absolute', left: '50%', top: '50%',
            transform: 'translate(-50%, -50%)',
            width: 12, height: 12, borderRadius: '50%',
            background: '#000',
            boxShadow: `0 0 0 1.5px ${isDark ? '#333' : '#bbb'}`,
          }}/>

          {/* Right icons */}
          <div style={{ marginLeft: 'auto', display: 'flex', gap: 6, alignItems: 'center' }}>
            <svg width="17" height="12" viewBox="0 0 17 12" fill={c.onBg}>
              <rect x="0"    y="4" width="3" height="8" rx="1" opacity="0.3"/>
              <rect x="4.5"  y="2.5" width="3" height="9.5" rx="1" opacity="0.55"/>
              <rect x="9"    y="1" width="3" height="11" rx="1" opacity="0.8"/>
              <rect x="13.5" y="0" width="3" height="12" rx="1"/>
            </svg>
            <svg width="16" height="12" viewBox="0 0 16 12" fill="none" stroke={c.onBg} strokeWidth="1.5">
              <path d="M8 2.5C10.5 2.5 12.7 3.5 14.3 5.1"/>
              <path d="M1.7 5.1C3.3 3.5 5.5 2.5 8 2.5"/>
              <path d="M4.5 7.8C5.6 6.6 6.7 6 8 6s2.4.6 3.5 1.8"/>
              <circle cx="8" cy="10" r="1.2" fill={c.onBg}/>
            </svg>
            {/* Battery */}
            <div style={{ width: 23, height: 12, borderRadius: 3, border: `1.5px solid ${c.onBg}`, position: 'relative', display: 'flex', alignItems: 'center', padding: '1.5px 2px' }}>
              <div style={{ height: 7, width: '78%', background: c.onBg, borderRadius: 1.5 }}/>
              <div style={{ position:'absolute', right:-4, top:'50%', transform:'translateY(-50%)', width:3, height:5, background:c.onBg, borderRadius:'0 1.5px 1.5px 0', opacity: 0.5 }}/>
            </div>
          </div>
        </div>

        {/* Screen content */}
        <div style={{ flex: 1, overflow: 'hidden', position: 'relative', display: 'flex', flexDirection: 'column' }}>
          {children}
        </div>
      </div>
    </div>
  );
}

/* ── TextField (filled pill style) ───────────────────────── */
function TextField({ label, value, onChange, type = 'text', leadingIcon, error, hint, c, autoFocus = false }) {
  const [focused, setFocused] = useState(false);
  const [showPass, setShowPass] = useState(false);
  const isPass = type === 'password';
  const hasValue = value && value.length > 0;
  const elevated = focused || hasValue;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
      <div style={{
        position: 'relative',
        background: error ? `${c.errorContainer}55` : c.surfaceMid,
        borderRadius: 12,
        border: `1.5px solid ${error ? c.error : focused ? c.primary : c.outlineVariant}`,
        transition: 'border-color 0.15s, background 0.15s',
        display: 'flex', alignItems: 'center',
        padding: '0 16px',
        height: 56,
        gap: 10,
      }}>
        {leadingIcon && (
          <span style={{ color: error ? c.error : focused ? c.primary : c.onSurfaceVariant, display: 'flex', flexShrink: 0, transition: 'color 0.15s' }}>
            {leadingIcon}
          </span>
        )}
        <div style={{ flex: 1, position: 'relative', height: '100%', display: 'flex', alignItems: 'center' }}>
          <label style={{
            position: 'absolute',
            top: elevated ? 8 : '50%',
            transform: elevated ? 'none' : 'translateY(-50%)',
            fontSize: elevated ? 11 : 15,
            fontWeight: elevated ? 600 : 400,
            color: error ? c.error : focused ? c.primary : c.onSurfaceVariant,
            transition: 'all 0.15s',
            pointerEvents: 'none',
            fontFamily: 'Inter, sans-serif',
            letterSpacing: elevated ? '0.04em' : 0,
            textTransform: elevated ? 'uppercase' : 'none',
            lineHeight: 1,
          }}>{label}</label>
          <input
            autoFocus={autoFocus}
            type={isPass && !showPass ? 'password' : 'text'}
            value={value}
            onChange={e => onChange && onChange(e.target.value)}
            onFocus={() => setFocused(true)}
            onBlur={() => setFocused(false)}
            style={{
              width: '100%', border: 'none', background: 'transparent', outline: 'none',
              fontSize: 15, color: c.onSurface, fontFamily: 'Inter, sans-serif',
              fontWeight: 400, paddingTop: elevated ? 16 : 0, transition: 'padding 0.15s',
            }}
          />
        </div>
        {isPass && (
          <button
            type="button"
            onClick={() => setShowPass(s => !s)}
            style={{ background: 'none', border: 'none', cursor: 'pointer', color: c.onSurfaceVariant, display: 'flex', padding: 4, flexShrink: 0 }}>
            <Icon name={showPass ? 'eyeOff' : 'eye'} size={18} color={c.onSurfaceVariant} />
          </button>
        )}
      </div>
      {(error || hint) && (
        <span style={{ fontSize: 11, color: error ? c.error : c.onSurfaceVariant, paddingLeft: 20, fontFamily: 'Inter, sans-serif', fontWeight: error ? 500 : 400 }}>
          {error || hint}
        </span>
      )}
    </div>
  );
}

/* ── Buttons ─────────────────────────────────────────────── */
function PrimaryBtn({ label, onClick, c, fullWidth = true, disabled = false, icon }) {
  const [pressed, setPressed] = useState(false);
  return (
    <button
      onClick={onClick}
      onMouseDown={() => setPressed(true)}
      onMouseUp={() => setPressed(false)}
      onMouseLeave={() => setPressed(false)}
      disabled={disabled}
      style={{
        width: fullWidth ? '100%' : 'auto',
        padding: '0 24px', height: 52,
        background: disabled ? c.surfaceHigh : pressed ? c.onPrimaryContainer : c.primary,
        color: disabled ? c.onSurfaceVariant : c.onPrimary,
        border: 'none', borderRadius: 9999,
        fontSize: 15, fontWeight: 600, fontFamily: 'Inter, sans-serif',
        cursor: disabled ? 'default' : 'pointer',
        transition: 'all 0.12s',
        transform: pressed ? 'scale(0.97)' : 'scale(1)',
        display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8,
      }}>
      {icon && <span style={{ display: 'flex' }}>{icon}</span>}
      {label}
    </button>
  );
}

function OutlinedBtn({ label, onClick, c, fullWidth = true, icon }) {
  const [pressed, setPressed] = useState(false);
  return (
    <button
      onClick={onClick}
      onMouseDown={() => setPressed(true)}
      onMouseUp={() => setPressed(false)}
      onMouseLeave={() => setPressed(false)}
      style={{
        width: fullWidth ? '100%' : 'auto',
        padding: '0 20px', height: 50,
        background: pressed ? `${c.primary}0F` : 'transparent',
        color: c.onSurface,
        border: `1.5px solid ${c.outlineVariant}`, borderRadius: 9999,
        fontSize: 14, fontWeight: 500, fontFamily: 'Inter, sans-serif',
        cursor: 'pointer', transition: 'all 0.12s',
        display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 10,
      }}>
      {icon && <span style={{ display: 'flex' }}>{icon}</span>}
      {label}
    </button>
  );
}

function TextBtn({ label, onClick, c, small = false }) {
  return (
    <button onClick={onClick} style={{
      background: 'none', border: 'none', color: c.primary,
      fontSize: small ? 13 : 14, fontWeight: 500,
      cursor: 'pointer', fontFamily: 'Inter, sans-serif', padding: '4px 0',
      textDecoration: 'none',
    }}>{label}</button>
  );
}

function IconBtn({ icon, onClick, c, size = 40, bgColor, color }) {
  const [pressed, setPressed] = useState(false);
  return (
    <button
      onClick={onClick}
      onMouseDown={() => setPressed(true)}
      onMouseUp={() => setPressed(false)}
      onMouseLeave={() => setPressed(false)}
      style={{
        width: size, height: size, borderRadius: 9999,
        background: bgColor || (pressed ? `${c.primary}14` : 'transparent'),
        border: 'none', cursor: 'pointer',
        display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0,
        transition: 'background 0.12s',
      }}>
      {icon}
    </button>
  );
}

/* ── Status Badge ────────────────────────────────────────── */
function StatusBadge({ status, c }) {
  const config = {
    active: { bg: c.primaryContainer, fg: c.onPrimaryContainer, label: 'Active' },
    paused: { bg: c.secondaryContainer, fg: c.onSecondaryContainer, label: 'Paused' },
    done:   { bg: c.tertiaryContainer, fg: c.onTertiaryContainer, label: 'Done' },
  };
  const cfg = config[status] || config.active;
  return (
    <span style={{
      fontSize: 11, fontWeight: 700, letterSpacing: '0.05em',
      padding: '3px 10px', borderRadius: 9999,
      background: cfg.bg, color: cfg.fg,
      fontFamily: 'Inter, sans-serif', flexShrink: 0, textTransform: 'uppercase',
    }}>{cfg.label}</span>
  );
}

/* ── Bottom Sheet ────────────────────────────────────────── */
function BottomSheet({ open, onClose, title, children, c }) {
  if (!open) return null;
  return (
    <div
      onClick={onClose}
      style={{
        position: 'absolute', inset: 0, background: 'rgba(0,0,0,0.5)',
        display: 'flex', flexDirection: 'column', justifyContent: 'flex-end', zIndex: 200,
      }}>
      <div
        onClick={e => e.stopPropagation()}
        style={{
          background: c.surfaceLow, borderRadius: '24px 24px 0 0',
          padding: '0 20px 32px', boxShadow: '0 -4px 24px rgba(0,0,0,0.15)',
          maxHeight: '80%', overflowY: 'auto',
        }}>
        <div style={{ width: 36, height: 4, background: c.outlineVariant, borderRadius: 99, margin: '12px auto 20px' }} />
        {title && (
          <div style={{ fontSize: 16, fontWeight: 600, color: c.primary, marginBottom: 20, fontFamily: 'Inter, sans-serif' }}>{title}</div>
        )}
        {children}
      </div>
    </div>
  );
}

/* ── Divider with label ──────────────────────────────────── */
function DividerLabel({ label, c }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
      <div style={{ flex: 1, height: 1, background: c.outlineVariant }} />
      <span style={{ fontSize: 12, color: c.onSurfaceVariant, fontFamily: 'Inter, sans-serif', fontWeight: 500, whiteSpace: 'nowrap' }}>{label}</span>
      <div style={{ flex: 1, height: 1, background: c.outlineVariant }} />
    </div>
  );
}

/* ── Wordmark ────────────────────────────────────────────── */
function Wordmark({ c, size = 'md' }) {
  const iconSize = size === 'lg' ? 44 : 32;
  const fontSize = size === 'lg' ? 28 : 20;
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 10, justifyContent: 'center' }}>
      <div style={{
        width: iconSize, height: iconSize, borderRadius: 12,
        background: c.primaryContainer,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
      }}>
        <Icon name="timer" size={iconSize * 0.55} color={c.primary} />
      </div>
      <span style={{ fontSize, fontWeight: 700, color: c.primary, letterSpacing: -0.5, fontFamily: 'Inter, sans-serif' }}>Tracky</span>
    </div>
  );
}

/* ── Checkbox ────────────────────────────────────────────── */
function Checkbox({ checked, onChange, c }) {
  return (
    <button
      onClick={() => onChange && onChange(!checked)}
      style={{
        width: 22, height: 22, borderRadius: 6, flexShrink: 0,
        background: checked ? c.primary : 'transparent',
        border: `2px solid ${checked ? c.primary : c.outline}`,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        cursor: 'pointer', transition: 'all 0.15s', padding: 0,
      }}>
      {checked && <Icon name="check" size={13} color={c.onPrimary} />}
    </button>
  );
}

/* ── Toggle Switch ───────────────────────────────────────── */
function Toggle({ checked, onChange, c }) {
  return (
    <button onClick={() => onChange(!checked)} style={{
      width: 46, height: 26, borderRadius: 13, border: 'none', cursor: 'pointer',
      background: checked ? c.primary : c.surfaceVariant,
      position: 'relative', transition: 'background 0.2s', padding: 0,
    }}>
      <div style={{
        width: 20, height: 20, borderRadius: 10, background: '#fff',
        position: 'absolute', top: 3, left: checked ? 23 : 3, transition: 'left 0.2s',
        boxShadow: '0 1px 3px rgba(0,0,0,0.3)',
      }}/>
    </button>
  );
}

Object.assign(window, { PhoneFrame, TextField, PrimaryBtn, OutlinedBtn, TextBtn, IconBtn, StatusBadge, BottomSheet, DividerLabel, Wordmark, Checkbox, Toggle });
