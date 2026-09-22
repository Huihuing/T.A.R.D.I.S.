import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
    CheckCircle2,
    KeyRound,
    Link2,
    Mail,
    MonitorSmartphone,
    ShieldCheck,
    Unlink,
    UserCog,
    WalletCards
} from 'lucide-react';
import { API_URL } from '../config';
import {
    authFetch,
    getAuthHeaders,
    getStoredToken,
    logoutSession
} from '../auth';

type AccountSettings = {
    username: string;
    name: string;
    email: string;
    emailVerified: boolean;
    pinConfigured: boolean;
    passwordLoginEnabled: boolean;
    googleConnected: boolean;
    socialProvider: string;
};

type GoogleCredentialResponse = {
    credential?: string;
};

type SecurityActivity = {
    id: number;
    message: string;
    createdAt: string;
};

type ActiveSession = {
    id: number;
    createdAt: string;
    expiresAt: string;
    current: boolean;
};

export default function Settings() {
    const navigate = useNavigate();
    const googleButtonRef = useRef<HTMLDivElement>(null);
    const googleClientId =
        import.meta.env.VITE_GOOGLE_CLIENT_ID as string | undefined;

    const [settings, setSettings] =
        useState<AccountSettings | null>(null);
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(true);

    const [currentPassword, setCurrentPassword] = useState('');
    const [newPassword, setNewPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [passwordLoading, setPasswordLoading] = useState(false);

    const [currentPin, setCurrentPin] = useState('');
    const [newPin, setNewPin] = useState('');
    const [confirmPin, setConfirmPin] = useState('');
    const [pinLoading, setPinLoading] = useState(false);
    const [googleLoading, setGoogleLoading] = useState(false);
    const [recoveryMode, setRecoveryMode] = useState<'PIN' | 'PASSWORD' | null>(null);
    const [securityCode, setSecurityCode] = useState('');
    const [recoveryPin, setRecoveryPin] = useState('');
    const [confirmRecoveryPin, setConfirmRecoveryPin] = useState('');
    const [recoveryPassword, setRecoveryPassword] = useState('');
    const [confirmRecoveryPassword, setConfirmRecoveryPassword] = useState('');
    const [securityCodeSending, setSecurityCodeSending] = useState(false);
    const [recoveryLoading, setRecoveryLoading] = useState(false);
    const [securityActivity, setSecurityActivity] =
        useState<SecurityActivity[]>([]);
    const [securityActivityLoading, setSecurityActivityLoading] =
        useState(false);
    const [sessions, setSessions] = useState<ActiveSession[]>([]);
    const [sessionsLoading, setSessionsLoading] = useState(false);
    const [sessionsRevoking, setSessionsRevoking] = useState(false);

    const loadSessions = async () => {
        setSessionsLoading(true);
        try {
            const res = await authFetch(
                `${API_URL}/api/auth/sessions`,
                {
                    headers: getAuthHeaders(false),
                    credentials: 'include'
                }
            );
            if (!res.ok) {
                setSessions([]);
                return;
            }
            const data = await res.json().catch(() => ([]));
            setSessions(Array.isArray(data) ? data : []);
        } finally {
            setSessionsLoading(false);
        }
    };

    const revokeOtherSessions = async () => {
        if (!window.confirm(
            '현재 브라우저를 제외한 다른 로그인 세션을 모두 종료할까요?'
        )) {
            return;
        }

        setSessionsRevoking(true);
        try {
            const res = await authFetch(
                `${API_URL}/api/auth/sessions/revoke-others`,
                {
                    method: 'POST',
                    headers: getAuthHeaders(false),
                    credentials: 'include'
                }
            );
            const data = await res.json().catch(() => ({}));
            if (!res.ok) {
                alert(data.message || '다른 로그인 세션 종료에 실패했습니다.');
                return;
            }

            alert(data.message || '다른 로그인 세션을 종료했습니다.');
            await loadSessions();
        } catch {
            alert('로그인 세션 정리 중 서버 오류가 발생했습니다.');
        } finally {
            setSessionsRevoking(false);
        }
    };

    const loadSecurityActivity = async () => {
        setSecurityActivityLoading(true);
        try {
            const res = await authFetch(
                `${API_URL}/api/notifications/security`,
                { headers: getAuthHeaders(false) }
            );
            if (!res.ok) return;
            const data = await res.json().catch(() => ([]));
            setSecurityActivity(Array.isArray(data) ? data : []);
        } finally {
            setSecurityActivityLoading(false);
        }
    };

    const loadSettings = async () => {
        try {
            const res = await authFetch(
                `${API_URL}/api/account/settings`,
                { headers: getAuthHeaders(false) }
            );
            const data = await res.json().catch(() => ({}));

            if (res.status === 401) {
                navigate('/login', { replace: true });
                return;
            }
            if (!res.ok) {
                setError(
                    data.message
                    || '계정 설정을 불러오지 못했습니다.'
                );
                return;
            }

            setSettings(data);
            setError('');
            loadSecurityActivity();
            loadSessions();
        } catch {
            setError('계정 설정을 불러오는 중 오류가 발생했습니다.');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        if (!getStoredToken()) {
            navigate('/login', { replace: true });
            return;
        }
        loadSettings();
    }, [navigate]);

    const handlePasswordChange = async (e: React.FormEvent) => {
        e.preventDefault();

        if (newPassword.length < 8 || newPassword.length > 64) {
            alert('새 비밀번호는 8~64자로 입력해주세요.');
            return;
        }
        if (newPassword !== confirmPassword) {
            alert('새 비밀번호 확인 값이 일치하지 않습니다.');
            return;
        }

        setPasswordLoading(true);
        try {
            const res = await authFetch(
                `${API_URL}/api/account/password/change`,
                {
                    method: 'POST',
                    headers: getAuthHeaders(),
                    body: JSON.stringify({
                        currentPassword,
                        newPassword
                    })
                }
            );
            const data = await res.json().catch(() => ({}));

            if (!res.ok) {
                alert(data.message || '비밀번호 변경에 실패했습니다.');
                return;
            }

            alert(
                data.message
                || '비밀번호가 변경되었습니다. 다시 로그인해주세요.'
            );
            await logoutSession();
            navigate('/login', { replace: true });
        } catch {
            alert('비밀번호 변경 중 서버 오류가 발생했습니다.');
        } finally {
            setPasswordLoading(false);
        }
    };

    const handlePinChange = async (e: React.FormEvent) => {
        e.preventDefault();

        if (!/^\d{4}$/.test(newPin)) {
            alert('새 PIN은 숫자 4자리로 입력해주세요.');
            return;
        }
        if (newPin !== confirmPin) {
            alert('새 PIN 확인 값이 일치하지 않습니다.');
            return;
        }

        setPinLoading(true);
        try {
            const res = await authFetch(
                `${API_URL}/api/account/pin/change`,
                {
                    method: 'POST',
                    headers: getAuthHeaders(),
                    body: JSON.stringify({
                        currentPin,
                        newPin
                    })
                }
            );
            const data = await res.json().catch(() => ({}));

            if (!res.ok) {
                alert(data.message || 'PIN 변경에 실패했습니다.');
                return;
            }

            alert(data.message || '송금 PIN이 변경되었습니다.');
            setCurrentPin('');
            setNewPin('');
            setConfirmPin('');
            await loadSettings();
        } catch {
            alert('PIN 변경 중 서버 오류가 발생했습니다.');
        } finally {
            setPinLoading(false);
        }
    };

    const sendSecurityCode = async () => {
        setSecurityCodeSending(true);
        try {
            const res = await authFetch(
                `${API_URL}/api/account/security-code/send`,
                {
                    method: 'POST',
                    headers: getAuthHeaders(false)
                }
            );
            const data = await res.json().catch(() => ({}));
            if (!res.ok) {
                alert(data.message || '보안 인증번호 발송에 실패했습니다.');
                return;
            }
            alert(data.message || '계정 이메일로 인증번호를 전송했습니다.');
        } catch {
            alert('보안 인증번호 발송 중 서버 오류가 발생했습니다.');
        } finally {
            setSecurityCodeSending(false);
        }
    };

    const handlePinReset = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!/^\d{6}$/.test(securityCode)) {
            alert('이메일 인증번호 6자리를 입력해주세요.');
            return;
        }
        if (!/^\d{4}$/.test(recoveryPin)) {
            alert('새 PIN은 숫자 4자리로 입력해주세요.');
            return;
        }
        if (recoveryPin !== confirmRecoveryPin) {
            alert('새 PIN 확인 값이 일치하지 않습니다.');
            return;
        }

        setRecoveryLoading(true);
        try {
            const res = await authFetch(`${API_URL}/api/account/pin/reset`, {
                method: 'POST',
                headers: getAuthHeaders(),
                body: JSON.stringify({
                    code: securityCode,
                    newPin: recoveryPin
                })
            });
            const data = await res.json().catch(() => ({}));
            if (!res.ok) {
                alert(data.message || 'PIN 재설정에 실패했습니다.');
                return;
            }
            alert(data.message || '송금 PIN을 재설정했습니다.');
            setRecoveryMode(null);
            setSecurityCode('');
            setRecoveryPin('');
            setConfirmRecoveryPin('');
            await loadSettings();
        } catch {
            alert('PIN 재설정 중 서버 오류가 발생했습니다.');
        } finally {
            setRecoveryLoading(false);
        }
    };

    const handlePasswordEnable = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!/^\d{6}$/.test(securityCode)) {
            alert('이메일 인증번호 6자리를 입력해주세요.');
            return;
        }
        if (recoveryPassword.length < 8 || recoveryPassword.length > 64) {
            alert('새 비밀번호는 8~64자로 입력해주세요.');
            return;
        }
        if (recoveryPassword !== confirmRecoveryPassword) {
            alert('새 비밀번호 확인 값이 일치하지 않습니다.');
            return;
        }

        setRecoveryLoading(true);
        try {
            const res = await authFetch(`${API_URL}/api/account/password/enable`, {
                method: 'POST',
                headers: getAuthHeaders(),
                body: JSON.stringify({
                    code: securityCode,
                    newPassword: recoveryPassword
                })
            });
            const data = await res.json().catch(() => ({}));
            if (!res.ok) {
                alert(data.message || '일반 비밀번호 추가에 실패했습니다.');
                return;
            }
            alert(data.message || '일반 비밀번호 로그인이 추가되었습니다.');
            setRecoveryMode(null);
            setSecurityCode('');
            setRecoveryPassword('');
            setConfirmRecoveryPassword('');
            await loadSettings();
        } catch {
            alert('일반 비밀번호 추가 중 서버 오류가 발생했습니다.');
        } finally {
            setRecoveryLoading(false);
        }
    };
    const linkGoogle = async (
        response: GoogleCredentialResponse
    ) => {
        if (!response.credential) {
            alert('Google 계정 정보를 받지 못했습니다.');
            return;
        }

        setGoogleLoading(true);
        try {
            const res = await authFetch(
                `${API_URL}/api/account/google/link`,
                {
                    method: 'POST',
                    headers: getAuthHeaders(),
                    body: JSON.stringify({
                        credential: response.credential
                    })
                }
            );
            const data = await res.json().catch(() => ({}));

            if (!res.ok) {
                alert(data.message || 'Google 연결에 실패했습니다.');
                return;
            }

            alert(data.message || 'Google 계정이 연결되었습니다.');
            await loadSettings();
        } catch {
            alert('Google 연결 중 서버 오류가 발생했습니다.');
        } finally {
            setGoogleLoading(false);
        }
    };

    useEffect(() => {
        if (
            !settings
            || settings.googleConnected
            || !googleClientId
            || !googleButtonRef.current
        ) {
            return;
        }

        const initializeGoogle = () => {
            const googleWindow = (
                window as Window & { google?: any }
            ).google;
            if (!googleWindow || !googleButtonRef.current) return;

            googleWindow.accounts.id.initialize({
                client_id: googleClientId,
                callback: linkGoogle
            });

            googleButtonRef.current.innerHTML = '';
            googleWindow.accounts.id.renderButton(
                googleButtonRef.current,
                {
                    theme: 'outline',
                    size: 'large',
                    text: 'continue_with',
                    shape: 'pill'
                }
            );
        };

        const googleWindow = (
            window as Window & { google?: any }
        ).google;
        if (googleWindow) {
            initializeGoogle();
            return;
        }

        const scriptId = 'google-identity-services';
        let script =
            document.getElementById(
                scriptId
            ) as HTMLScriptElement | null;

        if (!script) {
            script = document.createElement('script');
            script.id = scriptId;
            script.src =
                'https://accounts.google.com/gsi/client';
            script.async = true;
            script.defer = true;
            document.head.appendChild(script);
        }

        script.addEventListener('load', initializeGoogle);
        return () => {
            script?.removeEventListener('load', initializeGoogle);
        };
    }, [settings?.googleConnected, googleClientId]);

    const unlinkGoogle = async () => {
        if (!window.confirm(
            'Google 계정 연결을 해제할까요? 일반 비밀번호 로그인은 계속 사용할 수 있습니다.'
        )) {
            return;
        }

        setGoogleLoading(true);
        try {
            const res = await authFetch(
                `${API_URL}/api/account/google/link`,
                {
                    method: 'DELETE',
                    headers: getAuthHeaders(false)
                }
            );
            const data = await res.json().catch(() => ({}));

            if (!res.ok) {
                alert(data.message || 'Google 연결 해제에 실패했습니다.');
                return;
            }

            alert(data.message || 'Google 연결을 해제했습니다.');
            await loadSettings();
        } catch {
            alert('Google 연결 해제 중 서버 오류가 발생했습니다.');
        } finally {
            setGoogleLoading(false);
        }
    };

    const formatSessionTime = (value: string) => {
        const normalized = /[zZ]|[+-]\d{2}:\d{2}$/.test(value)
            ? value
            : `${value}Z`;
        const date = new Date(normalized);
        if (Number.isNaN(date.getTime())) return value;
        return new Intl.DateTimeFormat('ko-KR', {
            timeZone: 'Asia/Seoul',
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
            hour12: false
        }).format(date);
    };

    const formatSecurityTime = (value: string) => {
        const normalized = /[zZ]|[+-]\d{2}:\d{2}$/.test(value)
            ? value
            : `${value}+09:00`;
        const date = new Date(normalized);
        if (Number.isNaN(date.getTime())) return value;
        return new Intl.DateTimeFormat('ko-KR', {
            timeZone: 'Asia/Seoul',
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
            hour12: false
        }).format(date);
    };

    if (loading) {
        return (
            <div className="min-h-screen bg-[#0b1120] text-slate-400 flex items-center justify-center">
                계정 설정을 불러오는 중...
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-[#0b1120] text-slate-200 p-4 sm:p-6 md:p-8">
            <div className="max-w-5xl mx-auto">
                <div className="flex items-center gap-3 border-b border-slate-800 pb-5 mb-7">
                    <div className="w-12 h-12 rounded-2xl bg-sky-500/10 border border-sky-500/30 flex items-center justify-center">
                        <UserCog className="w-7 h-7 text-sky-400" />
                    </div>
                    <div>
                        <h1 className="text-3xl font-black text-white">
                            계정 설정
                        </h1>
                        <p className="text-sm text-slate-400">
                            로그인 방식과 송금 보안 정보를 관리합니다.
                        </p>
                    </div>
                </div>

                {error && (
                    <div className="mb-6 bg-rose-500/10 border border-rose-500/30 text-rose-300 rounded-2xl p-5">
                        {error}
                    </div>
                )}

                {settings && (
                    <div className="space-y-6">
                        <section className="bg-slate-800/50 border border-slate-700/60 rounded-3xl p-6">
                            <h2 className="text-xl font-black text-white flex items-center gap-2">
                                <ShieldCheck className="w-5 h-5 text-emerald-400" />
                                계정 상태
                            </h2>
                            <div className="grid sm:grid-cols-2 gap-4 mt-5">
                                <Info
                                    label="아이디"
                                    value={settings.username}
                                />
                                <Info
                                    label="이름"
                                    value={settings.name}
                                />
                                <Info
                                    label="이메일"
                                    value={settings.email}
                                />
                                <Info
                                    label="이메일 인증"
                                    value={
                                        settings.emailVerified
                                            ? '인증 완료'
                                            : '미인증'
                                    }
                                    good={settings.emailVerified}
                                />
                                <Info
                                    label="일반 비밀번호 로그인"
                                    value={
                                        settings.passwordLoginEnabled
                                            ? '사용 가능'
                                            : 'Google 전용'
                                    }
                                    good={
                                        settings.passwordLoginEnabled
                                    }
                                />
                                <Info
                                    label="송금 PIN"
                                    value={
                                        settings.pinConfigured
                                            ? '설정됨'
                                            : '설정 필요'
                                    }
                                    good={settings.pinConfigured}
                                />
                            </div>
                        </section>

                        <div className="grid lg:grid-cols-2 gap-6">
                            <section className="bg-slate-800/50 border border-slate-700/60 rounded-3xl p-6">
                                <h2 className="text-xl font-black text-white flex items-center gap-2 mb-2">
                                    <KeyRound className="w-5 h-5 text-sky-400" />
                                    비밀번호 변경
                                </h2>

                                {settings.passwordLoginEnabled ? (
                                    <>
                                        <p className="text-sm text-slate-400 mb-5">
                                            변경 후 모든 로그인 유지 세션이 해제되며 다시 로그인해야 합니다.
                                        </p>
                                        <form
                                            onSubmit={handlePasswordChange}
                                            className="space-y-3"
                                        >
                                            <PasswordInput
                                                value={currentPassword}
                                                onChange={setCurrentPassword}
                                                placeholder="현재 비밀번호"
                                            />
                                            <PasswordInput
                                                value={newPassword}
                                                onChange={setNewPassword}
                                                placeholder="새 비밀번호 (8~64자)"
                                            />
                                            <PasswordInput
                                                value={confirmPassword}
                                                onChange={setConfirmPassword}
                                                placeholder="새 비밀번호 확인"
                                            />
                                            <button
                                                type="submit"
                                                disabled={passwordLoading}
                                                className="w-full bg-sky-600 hover:bg-sky-500 rounded-xl py-3 font-bold disabled:opacity-50"
                                            >
                                                {passwordLoading
                                                    ? '변경 중...'
                                                    : '비밀번호 변경'}
                                            </button>
                                        </form>
                                    </>
                                ) : (
                                    <div className="mt-4 bg-slate-900/60 border border-slate-700 rounded-2xl p-4">
                                        <p className="text-sm text-slate-400">
                                            현재 Google 로그인 전용 계정입니다. 계정 이메일 인증 후 일반 비밀번호 로그인을 추가할 수 있습니다.
                                        </p>
                                        {recoveryMode !== 'PASSWORD' ? (
                                            <button
                                                type="button"
                                                onClick={() => {
                                                    setRecoveryMode('PASSWORD');
                                                    setSecurityCode('');
                                                }}
                                                className="mt-4 w-full rounded-xl bg-sky-500/10 border border-sky-500/20 py-3 text-sm font-bold text-sky-300 hover:bg-sky-500/20"
                                            >
                                                일반 비밀번호 추가
                                            </button>
                                        ) : (
                                            <form onSubmit={handlePasswordEnable} className="mt-4 space-y-3">
                                                <SecurityCodeInput
                                                    value={securityCode}
                                                    onChange={setSecurityCode}
                                                />
                                                <button
                                                    type="button"
                                                    disabled={securityCodeSending}
                                                    onClick={sendSecurityCode}
                                                    className="w-full rounded-xl border border-slate-700 bg-slate-800 py-2.5 text-sm font-bold text-slate-300 hover:bg-slate-700 disabled:opacity-50"
                                                >
                                                    {securityCodeSending ? '발송 중...' : '이메일 인증번호 보내기'}
                                                </button>
                                                <PasswordInput
                                                    value={recoveryPassword}
                                                    onChange={setRecoveryPassword}
                                                    placeholder="새 비밀번호 (8~64자)"
                                                />
                                                <PasswordInput
                                                    value={confirmRecoveryPassword}
                                                    onChange={setConfirmRecoveryPassword}
                                                    placeholder="새 비밀번호 확인"
                                                />
                                                <div className="flex gap-2">
                                                    <button
                                                        type="button"
                                                        disabled={recoveryLoading}
                                                        onClick={() => setRecoveryMode(null)}
                                                        className="flex-1 rounded-xl border border-slate-700 bg-slate-800 py-3 font-bold text-slate-400 hover:text-white disabled:opacity-50"
                                                    >
                                                        취소
                                                    </button>
                                                    <button
                                                        type="submit"
                                                        disabled={recoveryLoading}
                                                        className="flex-1 rounded-xl bg-sky-600 py-3 font-bold text-white hover:bg-sky-500 disabled:opacity-50"
                                                    >
                                                        {recoveryLoading ? '추가 중...' : '비밀번호 추가'}
                                                    </button>
                                                </div>
                                            </form>
                                        )}
                                    </div>
                                )}
                            </section>

                            <section className="bg-slate-800/50 border border-slate-700/60 rounded-3xl p-6">
                                <h2 className="text-xl font-black text-white flex items-center gap-2 mb-2">
                                    <WalletCards className="w-5 h-5 text-indigo-400" />
                                    송금 PIN 변경
                                </h2>
                                <p className="text-sm text-slate-400 mb-5">
                                    송금 승인에 사용하는 숫자 4자리 PIN입니다.
                                </p>

                                <form
                                    onSubmit={handlePinChange}
                                    className="space-y-3"
                                >
                                    {settings.pinConfigured && (
                                        <PinInput
                                            value={currentPin}
                                            onChange={setCurrentPin}
                                            placeholder="현재 PIN"
                                        />
                                    )}
                                    <PinInput
                                        value={newPin}
                                        onChange={setNewPin}
                                        placeholder="새 PIN"
                                    />
                                    <PinInput
                                        value={confirmPin}
                                        onChange={setConfirmPin}
                                        placeholder="새 PIN 확인"
                                    />
                                    <button
                                        type="submit"
                                        disabled={pinLoading}
                                        className="w-full bg-indigo-600 hover:bg-indigo-500 rounded-xl py-3 font-bold disabled:opacity-50"
                                    >
                                        {pinLoading
                                            ? '변경 중...'
                                            : settings.pinConfigured
                                                ? 'PIN 변경'
                                                : 'PIN 설정'}
                                    </button>
                                </form>

                                {settings.pinConfigured && (
                                    <div className="mt-4 border-t border-slate-700 pt-4">
                                        {recoveryMode !== 'PIN' ? (
                                            <button
                                                type="button"
                                                onClick={() => {
                                                    setRecoveryMode('PIN');
                                                    setSecurityCode('');
                                                }}
                                                className="w-full rounded-xl bg-amber-500/10 border border-amber-500/20 py-2.5 text-sm font-bold text-amber-300 hover:bg-amber-500/20"
                                            >
                                                PIN을 잊으셨나요? 이메일로 재설정
                                            </button>
                                        ) : (
                                            <form onSubmit={handlePinReset} className="space-y-3">
                                                <p className="text-xs leading-relaxed text-slate-500">
                                                    {settings.email}로 받은 6자리 인증번호와 새 PIN을 입력하세요.
                                                </p>
                                                <SecurityCodeInput
                                                    value={securityCode}
                                                    onChange={setSecurityCode}
                                                />
                                                <button
                                                    type="button"
                                                    disabled={securityCodeSending}
                                                    onClick={sendSecurityCode}
                                                    className="w-full rounded-xl border border-slate-700 bg-slate-900 py-2.5 text-sm font-bold text-slate-300 hover:bg-slate-800 disabled:opacity-50"
                                                >
                                                    {securityCodeSending ? '발송 중...' : '이메일 인증번호 보내기'}
                                                </button>
                                                <PinInput
                                                    value={recoveryPin}
                                                    onChange={setRecoveryPin}
                                                    placeholder="새 PIN"
                                                />
                                                <PinInput
                                                    value={confirmRecoveryPin}
                                                    onChange={setConfirmRecoveryPin}
                                                    placeholder="새 PIN 확인"
                                                />
                                                <div className="flex gap-2">
                                                    <button
                                                        type="button"
                                                        disabled={recoveryLoading}
                                                        onClick={() => setRecoveryMode(null)}
                                                        className="flex-1 rounded-xl border border-slate-700 bg-slate-900 py-3 font-bold text-slate-400 hover:text-white disabled:opacity-50"
                                                    >
                                                        취소
                                                    </button>
                                                    <button
                                                        type="submit"
                                                        disabled={recoveryLoading}
                                                        className="flex-1 rounded-xl bg-amber-500 py-3 font-black text-slate-950 hover:bg-amber-400 disabled:opacity-50"
                                                    >
                                                        {recoveryLoading ? '재설정 중...' : 'PIN 재설정'}
                                                    </button>
                                                </div>
                                            </form>
                                        )}
                                    </div>
                                )}
                            </section>
                        </div>

                        <section className="bg-slate-800/50 border border-slate-700/60 rounded-3xl p-6">
                            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
                                <div>
                                    <h2 className="text-xl font-black text-white flex items-center gap-2">
                                        <MonitorSmartphone className="w-5 h-5 text-sky-300" />
                                        활성 로그인 세션
                                    </h2>
                                    <p className="text-sm text-slate-400 mt-1">
                                        refresh token 기준 로그인 세션입니다. 현재 브라우저는 유지한 채 다른 세션을 종료할 수 있습니다. 이미 발급된 access token은 최대 15분 안에 만료됩니다.
                                    </p>
                                </div>
                                <div className="flex gap-2">
                                    <button
                                        type="button"
                                        onClick={loadSessions}
                                        disabled={sessionsLoading || sessionsRevoking}
                                        className="px-3 py-2 rounded-xl border border-slate-700 bg-slate-900 text-xs font-bold text-slate-400 hover:text-white disabled:opacity-50"
                                    >
                                        {sessionsLoading ? '갱신 중...' : '새로고침'}
                                    </button>
                                    <button
                                        type="button"
                                        onClick={revokeOtherSessions}
                                        disabled={
                                            sessionsRevoking
                                            || sessions.filter(item => !item.current).length === 0
                                        }
                                        className="px-3 py-2 rounded-xl border border-rose-500/30 bg-rose-500/10 text-xs font-bold text-rose-300 hover:bg-rose-500/20 disabled:opacity-40"
                                    >
                                        {sessionsRevoking ? '종료 중...' : '다른 세션 모두 종료'}
                                    </button>
                                </div>
                            </div>

                            <div className="mt-5 space-y-2">
                                {sessionsLoading && sessions.length === 0 ? (
                                    <div className="rounded-xl border border-slate-700 bg-slate-900/60 p-4 text-sm text-slate-500">
                                        로그인 세션을 불러오는 중...
                                    </div>
                                ) : sessions.length === 0 ? (
                                    <div className="rounded-xl border border-slate-700 bg-slate-900/60 p-4 text-sm text-slate-500">
                                        확인 가능한 refresh 세션이 없습니다.
                                    </div>
                                ) : (
                                    sessions.map(item => (
                                        <div
                                            key={item.id}
                                            className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 rounded-xl border border-slate-700 bg-slate-900/60 px-4 py-3"
                                        >
                                            <div>
                                                <div className="flex items-center gap-2 text-sm font-bold text-slate-200">
                                                    로그인 세션 #{item.id}
                                                    {item.current && (
                                                        <span className="rounded-full border border-emerald-500/30 bg-emerald-500/10 px-2 py-0.5 text-[10px] text-emerald-300">
                                                            현재 세션
                                                        </span>
                                                    )}
                                                </div>
                                                <div className="mt-1 text-xs text-slate-500">
                                                    생성/갱신 {formatSessionTime(item.createdAt)}
                                                </div>
                                            </div>
                                            <div className="text-xs text-slate-500 shrink-0">
                                                만료 {formatSessionTime(item.expiresAt)}
                                            </div>
                                        </div>
                                    ))
                                )}
                            </div>
                        </section>

                        <section className="bg-slate-800/50 border border-slate-700/60 rounded-3xl p-6">
                            <div className="flex items-center justify-between gap-3">
                                <div>
                                    <h2 className="text-xl font-black text-white flex items-center gap-2">
                                        <ShieldCheck className="w-5 h-5 text-rose-300" />
                                        최근 보안 활동
                                    </h2>
                                    <p className="text-sm text-slate-400 mt-1">
                                        비밀번호, PIN, 로그인 방식 변경 기록을 최근 순서로 표시합니다.
                                    </p>
                                </div>
                                <button
                                    type="button"
                                    onClick={loadSecurityActivity}
                                    disabled={securityActivityLoading}
                                    className="px-3 py-2 rounded-xl border border-slate-700 bg-slate-900 text-xs font-bold text-slate-400 hover:text-white disabled:opacity-50"
                                >
                                    {securityActivityLoading ? '갱신 중...' : '새로고침'}
                                </button>
                            </div>

                            <div className="mt-5 space-y-2">
                                {securityActivityLoading && securityActivity.length === 0 ? (
                                    <div className="rounded-xl border border-slate-700 bg-slate-900/60 p-4 text-sm text-slate-500">
                                        보안 활동을 불러오는 중...
                                    </div>
                                ) : securityActivity.length === 0 ? (
                                    <div className="rounded-xl border border-slate-700 bg-slate-900/60 p-4 text-sm text-slate-500">
                                        아직 기록된 보안 활동이 없습니다.
                                    </div>
                                ) : (
                                    securityActivity.map(item => (
                                        <div
                                            key={item.id}
                                            className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 rounded-xl border border-slate-700 bg-slate-900/60 px-4 py-3"
                                        >
                                            <div className="text-sm text-slate-200">
                                                {item.message}
                                            </div>
                                            <div className="text-xs text-slate-500 shrink-0">
                                                {formatSecurityTime(item.createdAt)}
                                            </div>
                                        </div>
                                    ))
                                )}
                            </div>
                        </section>

                        <section className="bg-slate-800/50 border border-slate-700/60 rounded-3xl p-6">
                            <h2 className="text-xl font-black text-white flex items-center gap-2">
                                <Mail className="w-5 h-5 text-amber-300" />
                                Google 계정 연결
                            </h2>

                            {settings.googleConnected ? (
                                <div className="mt-5 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 bg-slate-900/60 border border-slate-700 rounded-2xl p-4">
                                    <div>
                                        <div className="flex items-center gap-2 font-bold text-emerald-300">
                                            <CheckCircle2 className="w-5 h-5" />
                                            Google 계정 연결됨
                                        </div>
                                        <p className="text-sm text-slate-400 mt-1">
                                            {settings.email}
                                        </p>
                                        {!settings.passwordLoginEnabled && (
                                            <p className="text-xs text-amber-300 mt-2">
                                                Google 전용 계정은 로그인 수단 보호를 위해 연결을 해제할 수 없습니다.
                                            </p>
                                        )}
                                    </div>

                                    {settings.passwordLoginEnabled && (
                                        <button
                                            type="button"
                                            disabled={googleLoading}
                                            onClick={unlinkGoogle}
                                            className="inline-flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl bg-rose-500/10 hover:bg-rose-500/20 text-rose-300 font-bold disabled:opacity-50"
                                        >
                                            <Unlink className="w-4 h-4" />
                                            연결 해제
                                        </button>
                                    )}
                                </div>
                            ) : (
                                <div className="mt-5 bg-slate-900/60 border border-slate-700 rounded-2xl p-5">
                                    <p className="text-sm text-slate-400 mb-4">
                                        현재 계정의 이메일과 동일하고 Google에서 검증된 계정만 연결할 수 있습니다.
                                    </p>
                                    {googleClientId ? (
                                        <div
                                            ref={googleButtonRef}
                                            className={
                                                googleLoading
                                                    ? 'opacity-50 pointer-events-none'
                                                    : ''
                                            }
                                        />
                                    ) : (
                                        <div className="text-sm text-amber-300 flex items-center gap-2">
                                            <Link2 className="w-4 h-4" />
                                            Google Client ID가 배포 환경에 설정되어 있지 않습니다.
                                        </div>
                                    )}
                                </div>
                            )}
                        </section>
                    </div>
                )}
            </div>
        </div>
    );
}

function Info({
    label,
    value,
    good
}: {
    label: string;
    value: string;
    good?: boolean;
}) {
    return (
        <div className="bg-slate-900/60 rounded-2xl p-4 border border-slate-700/60">
            <div className="text-xs font-bold text-slate-500">
                {label}
            </div>
            <div className={
                `mt-1 font-bold break-all ${
                    good === true
                        ? 'text-emerald-300'
                        : 'text-slate-200'
                }`
            }>
                {value}
            </div>
        </div>
    );
}


function SecurityCodeInput({
    value,
    onChange
}: {
    value: string;
    onChange: (value: string) => void;
}) {
    return (
        <input
            type="text"
            inputMode="numeric"
            autoComplete="one-time-code"
            maxLength={6}
            value={value}
            onChange={e =>
                onChange(
                    e.target.value.replace(/\D/g, '').slice(0, 6)
                )
            }
            placeholder="이메일 인증번호 6자리"
            required
            className="w-full bg-slate-900 border border-slate-700 rounded-xl px-4 py-3 outline-none focus:border-amber-500 font-mono tracking-widest"
        />
    );
}

function PasswordInput({
    value,
    onChange,
    placeholder
}: {
    value: string;
    onChange: (value: string) => void;
    placeholder: string;
}) {
    return (
        <input
            type="password"
            minLength={8}
            maxLength={64}
            value={value}
            onChange={e => onChange(e.target.value)}
            placeholder={placeholder}
            required
            className="w-full bg-slate-900 border border-slate-700 rounded-xl px-4 py-3 outline-none focus:border-sky-500"
        />
    );
}

function PinInput({
    value,
    onChange,
    placeholder
}: {
    value: string;
    onChange: (value: string) => void;
    placeholder: string;
}) {
    return (
        <input
            type="password"
            inputMode="numeric"
            maxLength={4}
            value={value}
            onChange={e =>
                onChange(
                    e.target.value.replace(/\D/g, '').slice(0, 4)
                )
            }
            placeholder={placeholder}
            required
            className="w-full bg-slate-900 border border-slate-700 rounded-xl px-4 py-3 outline-none focus:border-indigo-500 font-mono tracking-widest"
        />
    );
}
