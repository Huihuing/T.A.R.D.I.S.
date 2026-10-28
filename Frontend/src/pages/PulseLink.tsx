import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { API_URL } from '../config';
import { getStoredToken, refreshAccessToken } from '../auth';

const CHALLENGE_PATTERN = /^[A-Za-z0-9_-]{43}$/;

/** Links an EXISTING logged-in Tardis user without sharing credentials with PULSE. */
export default function PulseLink() {
  const [params] = useSearchParams();
  const challenge = params.get('challenge') ?? '';
  const valid = CHALLENGE_PATTERN.test(challenge);
  const [proof, setProof] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function issueProof() {
    setBusy(true);
    setError('');
    setProof('');
    try {
      let token = getStoredToken();
      if (!token && await refreshAccessToken()) token = getStoredToken();
      if (!token) throw new Error('먼저 T.A.R.D.I.S. 계정으로 로그인한 뒤 연결 화면을 다시 열어주세요.');
      const res = await fetch(`${API_URL}/api/auth/pulse-link/proof`, {
        method: 'POST',
        credentials: 'include',
        headers: { Authorization: `Bearer ${token}`, 'content-type': 'application/json' },
        body: JSON.stringify({ challenge })
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok || typeof data.proof !== 'string') {
        throw new Error(data.error === 'PULSE_LINK_DISABLED'
          ? 'PULSE 계정 연결 기능이 아직 설정되지 않았습니다.'
          : '본인 확인에 실패했습니다. 로그인 상태를 확인하세요.');
      }
      setProof(data.proof);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '연결 증명 발급에 실패했습니다.');
    } finally { setBusy(false); }
  }

  return (
    <main className="min-h-screen bg-[#0b1120] text-slate-100 px-5 py-20">
      <div className="max-w-2xl mx-auto border border-slate-700 rounded-2xl p-8 bg-slate-900/80">
        <div className="text-xs font-mono tracking-widest text-cyan-300">MARKET PULSE // ID</div>
        <h1 className="text-3xl font-bold mt-4">PULSE//ID 계정 연결</h1>
        <p className="mt-4 text-slate-300">
          현재 T.A.R.D.I.S. 로그인 계정의 소유권만 증명합니다.
          송금 PIN과 비밀번호는 PULSE//LAB에 전달하지 않습니다.
        </p>
        {!valid ? (
          <p role="alert" className="mt-5 text-amber-300">
            유효한 연결 요청이 없습니다. PULSE//LAB에서 연결을 다시 시작하세요.
          </p>
        ) : (
          <>
            <button type="button" onClick={() => void issueProof()} disabled={busy}
              className="mt-8 rounded-full bg-cyan-300 px-5 py-3 text-slate-950 font-semibold disabled:opacity-50">
              {busy ? '확인 중…' : '현재 로그인 계정 소유권 증명'}
            </button>
            {proof && (
              <div className="mt-6 space-y-3">
                <p className="text-sm">90초 이내에 아래 증명을 PULSE//ID 연결 화면에 붙여넣으세요.</p>
                <textarea aria-label="일회용 계정 연결 증명" readOnly rows={4}
                  value={proof} onFocus={e => e.currentTarget.select()}
                  className="w-full rounded-lg bg-slate-950 border border-slate-600 p-3 font-mono text-xs" />
                <button type="button" className="rounded-lg bg-slate-700 px-4 py-2"
                  onClick={() => void navigator.clipboard.writeText(proof)}>증명 복사</button>
              </div>
            )}
          </>
        )}
        {error && <p role="alert" className="text-amber-300 mt-4">{error}</p>}
        <div className="mt-8 flex gap-5 text-sm">
          <Link className="text-cyan-300 underline" to="/login">기존 계정 로그인</Link>
          <Link className="text-slate-300 underline" to="/">T.A.R.D.I.S. 돌아가기</Link>
        </div>
      </div>
    </main>
  );
}
