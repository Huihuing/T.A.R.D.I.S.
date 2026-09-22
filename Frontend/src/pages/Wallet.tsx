import { API_URL } from '../config';
import { useState, useEffect } from 'react';
import { Gift, Send, History } from 'lucide-react';
import EconomyModal from '../components/EconomyModal';
import { authFetch, getAuthHeaders } from '../auth';
import { notify } from '../uiFeedback';

export default function Wallet() {
    const [balance, setBalance] = useState<number>(0);
    const [amount, setAmount] = useState<number | ''>('');
    const [targetUser, setTargetUser] = useState<string>('');
    const [accountPassword, setAccountPassword] = useState<string>('');
    const [isEconomyModalOpen, setIsEconomyModalOpen] = useState<boolean>(false);
    const [ledger, setLedger] = useState<any[]>([]);
    const [lastReceipt, setLastReceipt] = useState<any | null>(null);
    const [isTransferring, setIsTransferring] = useState(false);

    const fetchBalance = () => {
        authFetch(`${API_URL}/api/trade/balance`, { headers: getAuthHeaders() })
            .then(res => res.ok ? res.text() : '0')
            .then(bal => setBalance(Number(bal) || 0))
            .catch(() => setBalance(0));
    };

    const fetchLedger = () => {
        authFetch(`${API_URL}/api/account/ledger`, { headers: getAuthHeaders() })
            .then(async res => {
                const data = await res.json().catch(() => []);
                return res.ok && Array.isArray(data) ? data : [];
            })
            .then(setLedger)
            .catch(() => setLedger([]));
    };

    const formatKstDateTime = (value: string) => {
        if (!value) return '';
        const date = new Date(/[zZ]|[+-]\\d{2}:\\d{2}$/.test(value)
            ? value
            : `${value}+09:00`);
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

    useEffect(() => {
        fetchBalance();
        fetchLedger();
    }, []);

    const handleTransfer = async () => {
        if (isTransferring) return;
        if (!amount || amount <= 0) { notify('올바른 금액을 입력하세요.', 'warning'); return; }
        if (!accountPassword) { notify('계좌 PIN을 입력해 주세요.', 'warning'); return; }
        if (!targetUser) { notify('송금 대상 유저명을 입력하세요.', 'warning'); return; }

        setIsTransferring(true);
        try {
            const res = await authFetch(`${API_URL}/api/account/transfer`, {
                method: 'POST',
                headers: getAuthHeaders(),
                body: JSON.stringify({
                    toUser: targetUser,
                    amount: Number(amount),
                    accountPassword
                })
            });
            const data = await res.json();
            if (res.ok && data.status === 'SUCCESS') {
                setLastReceipt(data);
                notify(
                    `${data.toUser}님에게 ${Number(data.amount || 0).toFixed(2)} 송금했습니다.`,
                    'success'
                );
                setAmount('');
                setTargetUser('');
                setAccountPassword('');
                fetchBalance();
                fetchLedger();
            } else {
                notify(data.message || '송금에 실패했습니다.', 'error');
            }
        } catch {
            notify('서버와 통신 오류가 발생했습니다.', 'error');
        } finally {
            setIsTransferring(false);
        }
    };

    return (
        <div className="min-h-screen bg-[#0b1120] text-slate-200 p-6 md:p-8 font-sans">
            <h1 className="text-3xl font-extrabold text-transparent bg-clip-text bg-gradient-to-r from-sky-400 to-indigo-400 mb-8 pb-4 border-b border-slate-800">
                💳 My Wallet
            </h1>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-8 max-w-4xl">
                <div className="bg-slate-800/50 p-8 rounded-3xl border border-slate-700/50 shadow-xl flex flex-col justify-center relative overflow-hidden">
                    <div className="absolute top-0 right-0 w-32 h-32 bg-sky-500/10 rounded-full -mr-10 -mt-10 blur-2xl"></div>
                    <p className="text-slate-400 font-bold mb-2">총 보유 가상 현금 (Cash Balance)</p>
                    <div className="text-5xl font-mono font-black text-white">${balance.toFixed(2)}</div>

                    <button
                        onClick={() => setIsEconomyModalOpen(true)}
                        className="mt-6 flex items-center justify-center gap-2 w-full py-3 rounded-2xl bg-gradient-to-r from-amber-500 via-orange-500 to-pink-500 hover:from-amber-400 hover:to-pink-400 text-white font-extrabold text-xs shadow-lg shadow-orange-500/20 active:scale-95 transition-all cursor-pointer"
                    >
                        <Gift className="w-4 h-4 text-amber-200" />
                        일일 출석 · 활동 보상 · 긴급 지원
                    </button>

                    {balance < 100 && (
                        <div
                            onClick={() => setIsEconomyModalOpen(true)}
                            className="mt-3 p-3 rounded-xl bg-rose-500/20 border border-rose-500/40 text-rose-300 text-xs font-bold flex items-center justify-between cursor-pointer hover:bg-rose-500/30 transition-colors"
                        >
                            <span>🚨 잔고 $100 미만</span>
                            <span className="underline">긴급 지원 확인 ➔</span>
                        </div>
                    )}
                </div>

                <EconomyModal
                    isOpen={isEconomyModalOpen}
                    onClose={() => setIsEconomyModalOpen(false)}
                    onBalanceUpdate={(newBal) => setBalance(newBal)}
                    currentBalance={balance}
                />

                <div className="bg-slate-800/50 p-6 rounded-3xl border border-slate-700/50 flex flex-col gap-4 shadow-xl">
                    <div className="flex items-center gap-2">
                        <Send className="w-5 h-5 text-indigo-400" />
                        <h2 className="font-extrabold text-white">사용자 간 가상자산 송금</h2>
                    </div>
                    <p className="text-xs text-slate-400">
                        임의 입금·출금은 공개 버전에서 비활성화되어 있습니다. 초기 자금, 출석/활동 보상, 모의투자 결과로 형성된 잔고만 사용됩니다.
                    </p>

                    <div className="bg-slate-900/50 p-4 rounded-xl border border-rose-500/30">
                        <label className="block text-rose-400 text-sm font-bold mb-2">🔒 계좌 PIN</label>
                        <input
                            type="password"
                            value={accountPassword}
                            maxLength={4}
                            onChange={(e) => setAccountPassword(e.target.value)}
                            placeholder="숫자 4자리"
                            className="w-full bg-slate-900 text-white px-4 py-3 rounded-lg border border-slate-600 focus:border-rose-500 outline-none font-mono tracking-[0.5em] text-center text-lg"
                        />
                    </div>

                    <div>
                        <label className="block text-slate-400 text-sm font-bold mb-2">송금 금액 (USD)</label>
                        <input
                            type="number"
                            min="0"
                            value={amount}
                            onChange={(e) => setAmount(e.target.value === '' ? '' : Number(e.target.value))}
                            placeholder="0.00"
                            className="w-full bg-slate-900 text-white px-4 py-3 rounded-lg border border-slate-700 focus:border-sky-500 outline-none font-mono text-lg"
                        />
                    </div>

                    <div>
                        <label className="block text-slate-400 text-sm font-bold mb-2">송금 대상 (Target Username)</label>
                        <input
                            type="text"
                            value={targetUser}
                            onChange={(e) => setTargetUser(e.target.value)}
                            placeholder="받을 사람의 아이디"
                            className="w-full bg-slate-900 text-white px-4 py-3 rounded-lg border border-slate-700 focus:border-sky-500 outline-none"
                        />
                    </div>

                    <button
                        onClick={handleTransfer}
                        disabled={isTransferring}
                        className="w-full bg-indigo-600 hover:bg-indigo-500 text-white py-4 rounded-xl font-bold text-lg shadow-lg transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                        {isTransferring ? '송금 처리 중...' : '송금하기 ➔'}
                    </button>
                </div>
            </div>

            {lastReceipt && (
                <div className="mt-8 max-w-4xl bg-emerald-500/10 p-6 rounded-3xl border border-emerald-500/30 shadow-xl">
                    <div className="flex items-center justify-between gap-3 mb-4">
                        <div>
                            <p className="text-xs uppercase tracking-[0.2em] text-emerald-300 font-black">Transfer Receipt</p>
                            <h2 className="text-xl font-extrabold text-white mt-1">송금 완료</h2>
                        </div>
                        <button
                            type="button"
                            onClick={() => setLastReceipt(null)}
                            className="text-xs text-slate-400 hover:text-white"
                        >
                            닫기
                        </button>
                    </div>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm">
                        <ReceiptRow label="거래번호" value={`#${lastReceipt.transactionId}`} />
                        <ReceiptRow label="처리시각" value={formatKstDateTime(lastReceipt.transferredAt)} />
                        <ReceiptRow label="보내는 사람" value={lastReceipt.fromUser} />
                        <ReceiptRow label="받는 사람" value={lastReceipt.toUser} />
                        <ReceiptRow label="송금액" value={`${Number(lastReceipt.amount || 0).toFixed(2)}`} />
                        <ReceiptRow label="송금 후 잔액" value={`${Number(lastReceipt.newBalance || 0).toFixed(2)}`} />
                    </div>
                </div>
            )}

            <div className="mt-8 max-w-4xl bg-slate-800/50 p-6 rounded-3xl border border-slate-700/50 shadow-xl">
                <div className="flex items-center gap-2 mb-5">
                    <History className="w-5 h-5 text-sky-400" />
                    <h2 className="font-extrabold text-white">가상자산 거래 원장</h2>
                    <span className="text-xs text-slate-500">최근 100건</span>
                </div>

                {ledger.length === 0 ? (
                    <p className="text-sm text-slate-500 py-6 text-center">
                        아직 기록된 거래 내역이 없습니다.
                    </p>
                ) : (
                    <div className="flex flex-col gap-2">
                        {ledger.map((entry) => (
                            <div
                                key={entry.id}
                                className="grid grid-cols-1 md:grid-cols-[1fr_auto_auto] gap-2 md:gap-5 items-center bg-slate-900/50 border border-slate-700/60 rounded-xl px-4 py-3"
                            >
                                <div>
                                    <p className="text-sm font-bold text-slate-200">
                                        {entry.description || entry.type}
                                    </p>
                                    <p className="text-xs text-slate-500 mt-1">
                                        {formatKstDateTime(entry.createdAt)}
                                        {entry.counterparty ? ` · 상대: ${entry.counterparty}` : ''}
                                        {entry.symbol ? ` · ${entry.symbol}` : ''}
                                    </p>
                                </div>
                                <div className={`font-mono font-black ${Number(entry.amount) >= 0 ? 'text-emerald-400' : 'text-rose-400'}`}>
                                    {Number(entry.amount) >= 0 ? '+' : '-'}$
                                    {Math.abs(Number(entry.amount) || 0).toFixed(2)}
                                </div>
                                <div className="text-xs text-slate-400 md:text-right">
                                    잔액 ${Number(entry.balanceAfter || 0).toFixed(2)}
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
}


function ReceiptRow({ label, value }: { label: string; value: string }) {
    return (
        <div className="bg-slate-950/40 border border-slate-700/50 rounded-xl px-4 py-3">
            <p className="text-xs text-slate-500 font-bold">{label}</p>
            <p className="text-sm text-slate-100 font-mono font-bold mt-1 break-all">{value}</p>
        </div>
    );
}
