import { API_URL } from '../config';
import { notify } from '../uiFeedback';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ArrowLeft, KeyRound, Mail } from 'lucide-react';

export default function ForgotPassword() {
    const [email, setEmail] = useState('');
    const [code, setCode] = useState('');
    const [newPassword, setNewPassword] = useState('');
    const [sent, setSent] = useState(false);
    const [isSending, setIsSending] = useState(false);
    const [isResetting, setIsResetting] = useState(false);
    const navigate = useNavigate();

    const sendCode = async () => {
        if (!email.trim()) {
            notify('이메일을 입력해주세요.', 'warning');
            return;
        }

        setIsSending(true);
        try {
            const res = await fetch(`${API_URL}/api/auth/password/send`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email })
            });
            const data = await res.json().catch(() => ({}));
            if (!res.ok) {
                notify(data.message || '인증번호 요청에 실패했습니다.', 'error');
                return;
            }

            setSent(true);
            notify(
                data.message
                || '재설정 가능한 계정이 있다면 인증번호를 전송했습니다.',
                'success'
            );
        } catch {
            notify('인증번호 요청 중 서버 오류가 발생했습니다.', 'error');
        } finally {
            setIsSending(false);
        }
    };

    const resetPassword = async (e: React.FormEvent) => {
        e.preventDefault();

        if (!/^\d{6}$/.test(code.trim())) {
            notify('6자리 인증번호를 입력해주세요.', 'warning');
            return;
        }
        if (newPassword.length < 8 || newPassword.length > 64) {
            notify('새 비밀번호는 8~64자로 입력해주세요.', 'warning');
            return;
        }

        setIsResetting(true);
        try {
            const res = await fetch(`${API_URL}/api/auth/password/reset`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    email,
                    code: code.trim(),
                    newPassword
                })
            });

            const data = await res.json().catch(() => ({}));
            if (!res.ok) {
                notify(data.message || '비밀번호 재설정에 실패했습니다.', 'error');
                return;
            }

            notify(data.message || '비밀번호가 변경되었습니다.', 'success');
            navigate('/login');
        } catch {
            notify('비밀번호 재설정 중 서버 오류가 발생했습니다.', 'error');
        } finally {
            setIsResetting(false);
        }
    };

    return (
        <div className="min-h-screen bg-[#0b1120] text-slate-200 flex items-center justify-center p-4">
            <div className="w-full max-w-md">
                <Link
                    to="/login"
                    className="inline-flex items-center gap-2 text-slate-400 hover:text-sky-400 font-bold mb-5"
                >
                    <ArrowLeft className="w-4 h-4" />
                    로그인으로
                </Link>

                <div className="bg-slate-800/50 border border-slate-700/50 rounded-3xl p-8 shadow-2xl">
                    <div className="flex items-center gap-3 mb-2">
                        <div className="w-11 h-11 rounded-xl bg-sky-500/10 flex items-center justify-center">
                            <KeyRound className="w-6 h-6 text-sky-400" />
                        </div>
                        <h1 className="text-2xl font-black text-white">
                            비밀번호 재설정
                        </h1>
                    </div>

                    <p className="text-sm text-slate-400 mb-6">
                        일반 회원가입 계정에 등록한 이메일로 인증번호를 받아 비밀번호를 변경합니다.
                        Google로 만든 계정은 Google 로그인을 이용해주세요.
                    </p>

                    <form onSubmit={resetPassword} className="space-y-4">
                        <div>
                            <label className="text-sm text-slate-400 font-bold">
                                이메일
                            </label>
                            <div className="flex gap-2 mt-1">
                                <div className="relative flex-1">
                                    <Mail className="absolute left-3 top-3.5 w-4 h-4 text-slate-500" />
                                    <input
                                        type="email"
                                        value={email}
                                        onChange={e => {
                                            setEmail(e.target.value);
                                            setSent(false);
                                        }}
                                        required
                                        className="w-full bg-slate-900 border border-slate-700 rounded-xl pl-10 pr-3 py-3 outline-none focus:border-sky-500"
                                    />
                                </div>
                                <button
                                    type="button"
                                    onClick={sendCode}
                                    disabled={isSending}
                                    className="px-4 rounded-xl bg-slate-700 hover:bg-slate-600 font-bold disabled:opacity-50"
                                >
                                    {isSending ? '전송 중' : '인증번호'}
                                </button>
                            </div>
                        </div>

                        {sent && (
                            <>
                                <div>
                                    <label className="text-sm text-slate-400 font-bold">
                                        6자리 인증번호
                                    </label>
                                    <input
                                        type="text"
                                        inputMode="numeric"
                                        maxLength={6}
                                        value={code}
                                        onChange={e =>
                                            setCode(
                                                e.target.value
                                                    .replace(/\D/g, '')
                                                    .slice(0, 6)
                                            )
                                        }
                                        required
                                        className="w-full mt-1 bg-slate-900 border border-slate-700 rounded-xl px-4 py-3 outline-none focus:border-sky-500 font-mono tracking-[0.3em]"
                                    />
                                </div>

                                <div>
                                    <label className="text-sm text-slate-400 font-bold">
                                        새 비밀번호
                                    </label>
                                    <input
                                        type="password"
                                        minLength={8}
                                        maxLength={64}
                                        value={newPassword}
                                        onChange={e => setNewPassword(e.target.value)}
                                        required
                                        className="w-full mt-1 bg-slate-900 border border-slate-700 rounded-xl px-4 py-3 outline-none focus:border-sky-500"
                                    />
                                </div>

                                <button
                                    type="submit"
                                    disabled={isResetting}
                                    className="w-full bg-sky-600 hover:bg-sky-500 py-3.5 rounded-xl font-bold disabled:opacity-50"
                                >
                                    {isResetting
                                        ? '변경 중...'
                                        : '비밀번호 변경'}
                                </button>
                            </>
                        )}
                    </form>
                </div>
            </div>
        </div>
    );
}
