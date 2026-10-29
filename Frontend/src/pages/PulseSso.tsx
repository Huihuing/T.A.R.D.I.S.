import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { API_URL } from '../config';
import { storeAccessToken } from '../auth';

// Fragment never reaches the hosting server or Referer; remove immediately.
const ticketFromFragment = (() => {
    const fragment = window.location.hash.slice(1);
    const ticket = new URLSearchParams(fragment).get('pulse_sso') ?? '';
    if (ticket) {
        window.history.replaceState(null, '', window.location.pathname + window.location.search);
    }
    return ticket;
})();

let redeemInFlight: Promise<{ token: string; username: string; needsPinSetup?: boolean }> | null = null;

function redeem(ticket: string) {
    if (!redeemInFlight) redeemInFlight = (async () => {
        const response = await fetch(`${API_URL}/api/auth/pulse-sso`, {
            method: 'POST',
            credentials: 'include',
            headers: { 'content-type': 'application/json' },
            body: JSON.stringify({ ticket })
        });
        const data = await response.json().catch(() => ({}));
        if (!response.ok || typeof data.token !== 'string' || typeof data.username !== 'string') {
            throw new Error(response.status === 503
                ? 'PULSE 로그인 연동 서버가 아직 설정되지 않았습니다.'
                : '입장 요청이 만료되거나 연결되지 않았습니다. PULSE//LAB에서 다시 입장해 주세요.');
        }
        return data;
    })();
    return redeemInFlight;
}

/** From a PULSE central session, issue the native Tardis login session. */
export default function PulseSso() {
    const navigate = useNavigate();
    const [error, setError] = useState('');

    useEffect(() => {
        if (!/^[A-Za-z0-9_-]{43}$/.test(ticketFromFragment)) {
            setError('유효한 PULSE 입장 코드가 없습니다. PULSE//LAB에서 다시 입장해 주세요.');
            return;
        }
        let active = true;
        void redeem(ticketFromFragment).then(data => {
            if (!active) return;
            storeAccessToken(data.token);
            localStorage.setItem('username', data.username);
            if (data.needsPinSetup) {
                localStorage.setItem('needsPinSetup', 'true');
                navigate('/setup-pin', { replace: true });
            } else {
                localStorage.removeItem('needsPinSetup');
                navigate('/dashboard', { replace: true });
            }
        }).catch(cause => {
            if (active) setError(cause instanceof Error ? cause.message : 'PULSE 입장에 실패했습니다.');
        });
        return () => { active = false; };
    }, [navigate]);

    return (
        <main className="min-h-screen bg-[#0b1120] text-slate-100 flex items-center justify-center p-6">
            <section className="border border-slate-700 bg-slate-900 p-9 rounded-2xl max-w-lg w-full">
                <p className="text-xs text-cyan-300 tracking-widest">PULSE//ID · MARKET</p>
                <h1 className="text-2xl font-bold my-5">T.A.R.D.I.S. 입장 확인</h1>
                {error ? (
                    <p role="alert" className="text-amber-300">{error}</p>
                ) : (
                    <p role="status">기존 T.A.R.D.I.S. 계정의 세션을 연결하고 있습니다.</p>
                )}
                <Link to="/login" className="block text-sm text-cyan-300 mt-8 underline">
                    기존 로그인으로 이동
                </Link>
            </section>
        </main>
    );
}
