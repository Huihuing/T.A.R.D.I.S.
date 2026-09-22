import { useEffect, useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { API_URL } from '../config';
import { authFetch, getAuthHeaders, getStoredToken } from '../auth';
import { notify } from '../uiFeedback';
import { Home } from 'lucide-react';

export default function SetupPin() {
    const [pin, setPin] = useState('');
    const [confirmPin, setConfirmPin] = useState('');
    const [isLoading, setIsLoading] = useState(false);
    const navigate = useNavigate();

    useEffect(() => {
        if (!getStoredToken()) {
            navigate('/login', { replace: true });
        }
    }, [navigate]);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();

        if (!/^\d{4}$/.test(pin)) {
            notify('계좌 PIN은 숫자 4자리로 입력해주세요.', 'warning');
            return;
        }
        if (pin !== confirmPin) {
            notify('PIN 확인 값이 일치하지 않습니다.', 'warning');
            return;
        }

        setIsLoading(true);
        try {
            const res = await authFetch(
                `${API_URL}/api/account/pin/setup`,
                {
                    method: 'POST',
                    headers: getAuthHeaders(),
                    body: JSON.stringify({ pin })
                }
            );
            const data = await res.json().catch(() => ({}));

            if (!res.ok) {
                notify(data.message || 'PIN 설정에 실패했습니다.', 'error');
                return;
            }

            localStorage.removeItem('needsPinSetup');
            notify('송금용 계좌 PIN 설정이 완료되었습니다.', 'success');
            navigate('/dashboard', { replace: true });
        } catch {
            notify('PIN 설정 중 서버 오류가 발생했습니다.', 'error');
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
                <h1 className="text-2xl font-black text-white text-center mb-3">
                    송금 PIN 설정
                </h1>
                <p className="text-sm text-slate-400 text-center mb-6">
                    SNS 로그인 계정은 첫 로그인 시 송금에 사용할
                    숫자 4자리 PIN을 한 번 설정합니다.
                </p>

                <form
                    onSubmit={handleSubmit}
                    className="flex flex-col gap-4"
                >
                    <input
                        type="password"
                        inputMode="numeric"
                        maxLength={4}
                        value={pin}
                        onChange={e =>
                            setPin(e.target.value.replace(/\D/g, ''))
                        }
                        placeholder="PIN 4자리"
                        required
                        className="w-full bg-slate-900 border border-slate-700 rounded-xl px-4 py-3 text-white outline-none focus:border-sky-500 font-mono tracking-widest"
                    />
                    <input
                        type="password"
                        inputMode="numeric"
                        maxLength={4}
                        value={confirmPin}
                        onChange={e =>
                            setConfirmPin(
                                e.target.value.replace(/\D/g, '')
                            )
                        }
                        placeholder="PIN 다시 입력"
                        required
                        className="w-full bg-slate-900 border border-slate-700 rounded-xl px-4 py-3 text-white outline-none focus:border-sky-500 font-mono tracking-widest"
                    />
                    <button
                        type="submit"
                        disabled={
                            isLoading
                            || pin.length !== 4
                            || confirmPin.length !== 4
                        }
                        className="w-full bg-sky-600 hover:bg-sky-500 text-white font-bold py-3.5 rounded-xl transition-colors shadow-lg disabled:opacity-50"
                    >
                        {isLoading ? '설정 중...' : 'PIN 설정 완료'}
                    </button>
                </form>
            </div>
        </div>
    );
}
