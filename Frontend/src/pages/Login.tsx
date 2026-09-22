import { API_URL } from '../config';
import { useEffect, useRef, useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Home } from 'lucide-react';

type GoogleCredentialResponse = {
    credential?: string;
};

declare global {
    interface Window {
        google?: {
            accounts: {
                id: {
                    initialize: (options: {
                        client_id: string;
                        callback: (response: GoogleCredentialResponse) => void;
                    }) => void;
                    renderButton: (
                        element: HTMLElement,
                        options: Record<string, unknown>
                    ) => void;
                };
            };
        };
    }
}

export default function Login() {
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const [isGoogleLoading, setIsGoogleLoading] = useState(false);
    const googleButtonRef = useRef<HTMLDivElement>(null);
    const navigate = useNavigate();
    const googleClientId =
        import.meta.env.VITE_GOOGLE_CLIENT_ID as string | undefined;

    const finishLogin = (data: any) => {
        localStorage.setItem('token', data.token);
        localStorage.setItem(
            'username',
            data.username || username.trim()
        );

        if (data.dailyReward) {
            alert(
                '🎉 일일 출석 체크 완료!\n시드머니 $500이 추가로 지급되었습니다.'
            );
        }

        if (data.needsPinSetup) {
            localStorage.setItem('needsPinSetup', 'true');
            navigate('/setup-pin');
            return;
        }

        localStorage.removeItem('needsPinSetup');
        navigate('/dashboard');
    };

    const handleGoogleCredential = async (
        response: GoogleCredentialResponse
    ) => {
        if (!response.credential) {
            alert('Google 로그인 정보를 받지 못했습니다.');
            return;
        }

        setIsGoogleLoading(true);
        try {
            const res = await fetch(`${API_URL}/api/auth/google`, {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    credential: response.credential
                })
            });
            const data = await res.json().catch(() => ({}));
            if (!res.ok || !data.token) {
                alert(data.message || 'Google 로그인에 실패했습니다.');
                return;
            }
            finishLogin(data);
        } catch {
            alert('Google 로그인 중 서버 오류가 발생했습니다.');
        } finally {
            setIsGoogleLoading(false);
        }
    };

    useEffect(() => {
        if (!googleClientId || !googleButtonRef.current) return;

        const initializeGoogle = () => {
            if (!window.google || !googleButtonRef.current) return;

            window.google.accounts.id.initialize({
                client_id: googleClientId,
                callback: handleGoogleCredential
            });

            googleButtonRef.current.innerHTML = '';
            window.google.accounts.id.renderButton(
                googleButtonRef.current,
                {
                    theme: 'outline',
                    size: 'large',
                    width: 352,
                    text: 'signin_with',
                    shape: 'pill'
                }
            );
        };

        if (window.google) {
            initializeGoogle();
            return;
        }

        const scriptId = 'google-identity-services';
        let script =
            document.getElementById(scriptId) as HTMLScriptElement | null;

        if (!script) {
            script = document.createElement('script');
            script.id = scriptId;
            script.src = 'https://accounts.google.com/gsi/client';
            script.async = true;
            script.defer = true;
            document.head.appendChild(script);
        }

        script.addEventListener('load', initializeGoogle);
        return () => {
            script?.removeEventListener('load', initializeGoogle);
        };
    }, [googleClientId]);

    const handleLogin = async (e: React.FormEvent) => {
        e.preventDefault();
        setIsLoading(true);
        try {
            const res = await fetch(`${API_URL}/api/auth/login`, {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password })
            });
            const data = await res.json().catch(() => ({}));
            if (res.ok && data.token) {
                finishLogin(data);
            } else {
                alert(data.message || '로그인 실패');
            }
        } catch {
            alert('서버 오류가 발생했습니다.');
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <div className="min-h-screen bg-[#0b1120] text-slate-200 flex items-center justify-center p-4 relative">
            <Link
                to="/"
                className="absolute top-6 left-6 flex items-center gap-2 text-slate-400 hover:text-sky-400 font-bold transition-colors bg-slate-800/50 px-4 py-2 rounded-xl border border-slate-700/50"
            >
                <Home className="w-5 h-5" /> 홈으로
            </Link>

            <div className="bg-slate-800/50 backdrop-blur-xl border border-slate-700/50 p-8 rounded-3xl w-full max-w-md shadow-2xl">
                <h1 className="text-3xl font-black text-white text-center mb-6">
                    로그인
                </h1>

                {googleClientId && (
                    <>
                        <div
                            ref={googleButtonRef}
                            className={`flex justify-center ${isGoogleLoading ? 'opacity-50 pointer-events-none' : ''}`}
                        />
                        <div className="flex items-center gap-3 my-6 text-xs text-slate-500">
                            <div className="h-px bg-slate-700 flex-1" />
                            <span>또는 아이디로 로그인</span>
                            <div className="h-px bg-slate-700 flex-1" />
                        </div>
                    </>
                )}

                <form
                    onSubmit={handleLogin}
                    className="flex flex-col gap-4"
                >
                    <div>
                        <label className="block text-sm text-slate-400 mb-1 font-bold">
                            아이디 (Username)
                        </label>
                        <input
                            type="text"
                            value={username}
                            onChange={e => setUsername(e.target.value)}
                            required
                            className="w-full bg-slate-900 border border-slate-700 rounded-xl px-4 py-3 text-white outline-none focus:border-sky-500"
                        />
                    </div>
                    <div>
                        <label className="block text-sm text-slate-400 mb-1 font-bold">
                            비밀번호 (Password)
                        </label>
                        <input
                            type="password"
                            value={password}
                            onChange={e => setPassword(e.target.value)}
                            required
                            className="w-full bg-slate-900 border border-slate-700 rounded-xl px-4 py-3 text-white outline-none focus:border-sky-500"
                        />
                    </div>
                    <div className="flex justify-end">
                        <Link
                            to="/forgot-password"
                            className="text-xs text-sky-400 hover:underline font-bold"
                        >
                            비밀번호를 잊으셨나요?
                        </Link>
                    </div>
                    <button
                        type="submit"
                        disabled={isLoading || isGoogleLoading}
                        className="w-full bg-sky-600 hover:bg-sky-500 text-white font-bold py-3.5 rounded-xl mt-2 transition-colors shadow-lg disabled:opacity-50"
                    >
                        {isLoading ? '로그인 중...' : '로그인'}
                    </button>
                </form>

                <div className="text-center mt-6 text-sm text-slate-400">
                    계정이 없으신가요?{' '}
                    <Link
                        to="/register"
                        className="text-sky-400 font-bold hover:underline"
                    >
                        회원가입
                    </Link>
                </div>
            </div>
        </div>
    );
}
