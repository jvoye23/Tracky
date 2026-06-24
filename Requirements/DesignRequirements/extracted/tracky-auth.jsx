// Tracky Auth Screens — Login, Register, ForgotPassword, EmailSent

/* ── Login Screen ────────────────────────────────────────── */
function LoginScreen({ c, navigate }) {
  const [email, setEmail] = useState('');
  const [pass, setPass] = useState('');
  const [emailErr, setEmailErr] = useState('');
  const [passErr, setPassErr] = useState('');

  const validate = () => {
    let ok = true;
    if (!email.includes('@')) { setEmailErr('Enter a valid email address'); ok = false; } else setEmailErr('');
    if (pass.length < 6) { setPassErr('Password must be at least 6 characters'); ok = false; } else setPassErr('');
    return ok;
  };

  const handleLogin = () => { if (validate()) navigate('overview'); };

  return (
    <div style={{ flex: 1, overflowY: 'auto', background: c.bg, display: 'flex', flexDirection: 'column' }}>
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'center', padding: '24px 28px 32px' }}>
        {/* Wordmark */}
        <div style={{ marginBottom: 36 }}>
          <Wordmark c={c} size="lg" />
        </div>

        {/* Heading */}
        <div style={{ marginBottom: 28, textAlign: 'center' }}>
          <div style={{ fontSize: 24, fontWeight: 700, color: c.onBg, fontFamily: 'Inter, sans-serif', marginBottom: 6 }}>Welcome back</div>
          <div style={{ fontSize: 14, color: c.onSurfaceVariant, fontFamily: 'Inter, sans-serif' }}>Log in to your account to continue</div>
        </div>

        {/* Social login */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginBottom: 20 }}>
          <OutlinedBtn label="Continue with Google" c={c} icon={<GoogleLogo s={18} />} onClick={() => navigate('overview')} />
          <OutlinedBtn
            label="Continue with Apple"
            c={c}
            icon={<AppleLogo s={18} color={c.onSurface} />}
            onClick={() => navigate('overview')}
          />
        </div>

        <DividerLabel label="or continue with email" c={c} />

        {/* Email form */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginTop: 20 }}>
          <TextField
            label="Email"
            value={email}
            onChange={v => { setEmail(v); setEmailErr(''); }}
            leadingIcon={<Icon name="mail" size={18} color={c.onSurfaceVariant} />}
            error={emailErr}
            c={c}
          />
          <TextField
            label="Password"
            value={pass}
            onChange={v => { setPass(v); setPassErr(''); }}
            type="password"
            leadingIcon={<Icon name="lock" size={18} color={c.onSurfaceVariant} />}
            error={passErr}
            c={c}
          />
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <TextBtn label="Forgot password?" onClick={() => navigate('forgot')} c={c} small />
          </div>
        </div>

        {/* CTA */}
        <div style={{ marginTop: 24, display: 'flex', flexDirection: 'column', gap: 12 }}>
          <PrimaryBtn label="Log in" onClick={handleLogin} c={c} />
          <div style={{ textAlign: 'center' }}>
            <span style={{ fontSize: 14, color: c.onSurfaceVariant, fontFamily: 'Inter, sans-serif' }}>Don't have an account? </span>
            <TextBtn label="Sign up" onClick={() => navigate('register')} c={c} />
          </div>
        </div>
      </div>
    </div>
  );
}

/* ── Register Screen ─────────────────────────────────────── */
function RegisterScreen({ c, navigate }) {
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [pass, setPass] = useState('');
  const [passConfirm, setPassConfirm] = useState('');
  const [agreed, setAgreed] = useState(false);
  const [errors, setErrors] = useState({});

  const validate = () => {
    const e = {};
    if (name.trim().length < 2) e.name = 'Name must be at least 2 characters';
    if (!email.includes('@')) e.email = 'Enter a valid email address';
    if (pass.length < 8) e.pass = 'Password must be at least 8 characters';
    if (pass !== passConfirm) e.passConfirm = 'Passwords do not match';
    if (!agreed) e.terms = 'You must agree to the terms';
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const handleRegister = () => { if (validate()) navigate('emailSent'); };
  const canSubmit = name.length > 0 && email.includes('@') && pass.length >= 8 && passConfirm === pass && agreed;

  return (
    <div style={{ flex: 1, overflowY: 'auto', background: c.bg }}>
      <div style={{ padding: '20px 28px 40px' }}>
        {/* Wordmark */}
        <div style={{ marginBottom: 28 }}>
          <Wordmark c={c} size="md" />
        </div>

        <div style={{ marginBottom: 24, textAlign: 'center' }}>
          <div style={{ fontSize: 24, fontWeight: 700, color: c.onBg, fontFamily: 'Inter, sans-serif', marginBottom: 6 }}>Create account</div>
          <div style={{ fontSize: 14, color: c.onSurfaceVariant, fontFamily: 'Inter, sans-serif' }}>Start tracking your projects today</div>
        </div>

        {/* Social */}
        <div style={{ display: 'flex', gap: 10, marginBottom: 20 }}>
          <OutlinedBtn label="Google" c={c} icon={<GoogleLogo s={18} />} onClick={() => navigate('overview')} />
          <OutlinedBtn label="Apple" c={c} icon={<AppleLogo s={18} color={c.onSurface} />} onClick={() => navigate('overview')} />
        </div>

        <DividerLabel label="or sign up with email" c={c} />

        <div style={{ display: 'flex', flexDirection: 'column', gap: 12, marginTop: 20 }}>
          <TextField
            label="Full name"
            value={name}
            onChange={v => { setName(v); setErrors(e => ({ ...e, name: '' })); }}
            leadingIcon={<Icon name="user" size={18} color={c.onSurfaceVariant} />}
            error={errors.name}
            c={c}
          />
          <TextField
            label="Email"
            value={email}
            onChange={v => { setEmail(v); setErrors(e => ({ ...e, email: '' })); }}
            leadingIcon={<Icon name="mail" size={18} color={c.onSurfaceVariant} />}
            error={errors.email}
            c={c}
          />
          <TextField
            label="Password"
            value={pass}
            onChange={v => { setPass(v); setErrors(e => ({ ...e, pass: '' })); }}
            type="password"
            leadingIcon={<Icon name="lock" size={18} color={c.onSurfaceVariant} />}
            error={errors.pass}
            hint={!errors.pass ? 'Minimum 8 characters' : undefined}
            c={c}
          />
          <TextField
            label="Confirm password"
            value={passConfirm}
            onChange={v => { setPassConfirm(v); setErrors(e => ({ ...e, passConfirm: '' })); }}
            type="password"
            leadingIcon={<Icon name="lock" size={18} color={c.onSurfaceVariant} />}
            error={errors.passConfirm}
            c={c}
          />
        </div>

        {/* Terms */}
        <div style={{ marginTop: 16, display: 'flex', alignItems: 'flex-start', gap: 10 }}>
          <Checkbox checked={agreed} onChange={setAgreed} c={c} />
          <div style={{ fontSize: 13, color: c.onSurfaceVariant, fontFamily: 'Inter, sans-serif', lineHeight: 1.5, paddingTop: 1 }}>
            I agree to the{' '}
            <span style={{ color: c.primary, fontWeight: 500 }}>Terms of Service</span>
            {' '}and{' '}
            <span style={{ color: c.primary, fontWeight: 500 }}>Privacy Policy</span>
          </div>
        </div>
        {errors.terms && <div style={{ fontSize: 11, color: c.error, marginTop: 4, paddingLeft: 4, fontFamily: 'Inter, sans-serif' }}>{errors.terms}</div>}

        <div style={{ marginTop: 24, display: 'flex', flexDirection: 'column', gap: 12 }}>
          <PrimaryBtn label="Create account" onClick={handleRegister} c={c} disabled={!canSubmit} />
          <div style={{ textAlign: 'center' }}>
            <span style={{ fontSize: 14, color: c.onSurfaceVariant, fontFamily: 'Inter, sans-serif' }}>Already have an account? </span>
            <TextBtn label="Log in" onClick={() => navigate('login')} c={c} />
          </div>
        </div>
      </div>
    </div>
  );
}

/* ── Forgot Password ─────────────────────────────────────── */
function ForgotPasswordScreen({ c, navigate }) {
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const [loading, setLoading] = useState(false);

  const handleSubmit = () => {
    if (!email.includes('@')) return;
    setLoading(true);
    setTimeout(() => { setLoading(false); setSent(true); }, 1200);
  };

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', background: c.bg }}>
      {/* Top bar */}
      <div style={{ display: 'flex', alignItems: 'center', padding: '8px 8px 8px 4px', borderBottom: `1px solid ${c.outlineVariant}`, flexShrink: 0 }}>
        <IconBtn icon={<Icon name="arrowBack" size={22} color={c.onSurface} />} onClick={() => navigate('login')} c={c} />
        <span style={{ fontSize: 17, fontWeight: 600, color: c.onSurface, fontFamily: 'Inter, sans-serif', marginLeft: 4 }}>Reset password</span>
      </div>

      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'center', padding: '0 28px 32px' }}>
        {!sent ? (
          <>
            {/* Icon */}
            <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 28 }}>
              <div style={{ width: 72, height: 72, borderRadius: 24, background: c.primaryContainer, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <Icon name="lock" size={32} color={c.primary} />
              </div>
            </div>

            <div style={{ textAlign: 'center', marginBottom: 32 }}>
              <div style={{ fontSize: 22, fontWeight: 700, color: c.onBg, fontFamily: 'Inter, sans-serif', marginBottom: 8 }}>Forgot your password?</div>
              <div style={{ fontSize: 14, color: c.onSurfaceVariant, fontFamily: 'Inter, sans-serif', lineHeight: 1.6 }}>
                Enter your email and we'll send you a link to reset your password.
              </div>
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
              <TextField
                label="Email"
                value={email}
                onChange={setEmail}
                leadingIcon={<Icon name="mail" size={18} color={c.onSurfaceVariant} />}
                c={c}
              />
              <PrimaryBtn
                label={loading ? '...' : 'Send reset link'}
                onClick={handleSubmit}
                c={c}
                disabled={!email.includes('@') || loading}
              />
            </div>

            <div style={{ textAlign: 'center', marginTop: 20 }}>
              <TextBtn label="Back to log in" onClick={() => navigate('login')} c={c} />
            </div>
          </>
        ) : (
          /* Success state */
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 20, textAlign: 'center' }}>
            <div style={{ width: 88, height: 88, borderRadius: 28, background: c.primaryContainer, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Icon name="checkCircle" size={44} color={c.primary} />
            </div>
            <div>
              <div style={{ fontSize: 22, fontWeight: 700, color: c.onBg, fontFamily: 'Inter, sans-serif', marginBottom: 10 }}>Check your email</div>
              <div style={{ fontSize: 14, color: c.onSurfaceVariant, fontFamily: 'Inter, sans-serif', lineHeight: 1.7 }}>
                We sent a reset link to <span style={{ color: c.primary, fontWeight: 500 }}>{email}</span>.
                Check your inbox and follow the instructions.
              </div>
            </div>
            <div style={{ width: '100%', display: 'flex', flexDirection: 'column', gap: 10, marginTop: 8 }}>
              <PrimaryBtn label="Back to log in" onClick={() => navigate('login')} c={c} />
              <TextBtn label="Resend email" onClick={() => setSent(false)} c={c} />
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

/* ── Email Verification Screen ───────────────────────────── */
function EmailSentScreen({ c, navigate }) {
  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center', padding: '32px 28px', background: c.bg, gap: 0, textAlign: 'center' }}>
      <div style={{ width: 96, height: 96, borderRadius: 30, background: c.primaryContainer, display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 28 }}>
        <Icon name="mail" size={44} color={c.primary} />
      </div>
      <div style={{ fontSize: 24, fontWeight: 700, color: c.onBg, fontFamily: 'Inter, sans-serif', marginBottom: 12 }}>Verify your email</div>
      <div style={{ fontSize: 14, color: c.onSurfaceVariant, fontFamily: 'Inter, sans-serif', lineHeight: 1.7, marginBottom: 32, maxWidth: 280 }}>
        We sent a verification link to your inbox. Please verify your account before logging in.
      </div>
      <div style={{ width: '100%', display: 'flex', flexDirection: 'column', gap: 10 }}>
        <PrimaryBtn label="Open email app" onClick={() => {}} c={c} />
        <TextBtn label="Resend verification email" onClick={() => {}} c={c} />
        <TextBtn label="Back to log in" onClick={() => navigate('login')} c={c} small />
      </div>
    </div>
  );
}

Object.assign(window, { LoginScreen, RegisterScreen, ForgotPasswordScreen, EmailSentScreen });
