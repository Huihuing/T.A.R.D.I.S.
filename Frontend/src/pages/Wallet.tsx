import { useState, useEffect } from 'react';
import { Gift } from 'lucide-react';
import EconomyModal from '../components/EconomyModal';

export default function Wallet() {
    const [balance, setBalance] = useState<number>(0);
    const [amount, setAmount] = useState<number | ''>('');
    const [targetUser, setTargetUser] = useState<string>('');
    const [accountPassword, setAccountPassword] = useState<string>('');
    const [isEconomyModalOpen, setIsEconomyModalOpen] = useState<boolean>(false);

    const getAuthHeaders = () => {
        const token = localStorage.getItem('token');
        return { 'Content-Type': 'application/json', ...(token && { 'Authorization': `Bearer ${token}` }) };
    };

    const fetchBalance = () => {
        const username = localStorage.getItem('username');
        if (username) {
            fetch(`http://localhost:8080/api/trade/balance?username=${username}`, { headers: getAuthHeaders() })
                .then(res => res.text()).then(bal => setBalance(Number(bal) || 0));
        }
    };

    useEffect(() => { fetchBalance(); }, []);

    const handleAction = async (action: 'deposit' | 'withdrawal' | 'transfer') => {
        const username = localStorage.getItem('username');
        if (!username) return alert("로그인이 필요합니다.");
        if (!amount || amount <= 0) return alert("올바른 금액을 입력하세요.");
        if (!accountPassword) return alert("계좌 비밀번호를 입력해 주세요.");
        if (action === 'transfer' && !targetUser) return alert("송금 대상 유저명을 입력하세요.");

        const url = `http://localhost:8080/api/account/${action}`;
        const bodyData = action === 'transfer' ? { fromUser: username, toUser: targetUser, amount: Number(amount), accountPassword } : { username: username, amount: Number(amount), accountPassword };

        try {
            const res = await fetch(url, { method: 'POST', headers: getAuthHeaders(), body: JSON.stringify(bodyData) });
            const data = await res.json();
            if (res.ok && data.status === "SUCCESS") {
                alert(data.message);
                setAmount(''); setTargetUser(''); setAccountPassword('');
                fetchBalance();
            } else { alert(`오류: ${data.message}`); }
        } catch (error) { alert("서버와 통신 오류가 발생했습니다."); }
    };

    return (
        <div className="min-h-screen bg-[#0b1120] text-slate-200 p-6 md:p-8 font-sans">
            <h1 className="text-3xl font-extrabold text-transparent bg-clip-text bg-gradient-to-r from-sky-400 to-indigo-400 mb-8 pb-4 border-b border-slate-800">
                💳 My Wallet
            </h1>
            
            <div className="grid grid-cols-1 md:grid-cols-2 gap-8 max-w-4xl">
                <div className="bg-slate-800/50 p-8 rounded-3xl border border-slate-700/50 shadow-xl flex flex-col justify-center relative overflow-hidden">
                    <div className="absolute top-0 right-0 w-32 h-32 bg-sky-500/10 rounded-full -mr-10 -mt-10 blur-2xl"></div>
                    <p className="text-slate-400 font-bold mb-2">총 보유 현금 (Cash Balance)</p>
                    <div className="text-5xl font-mono font-black text-white">${balance.toFixed(2)}</div>
                    
                    <button
                        onClick={() => setIsEconomyModalOpen(true)}
                        className="mt-6 flex items-center justify-center gap-2 w-full py-3 rounded-2xl bg-gradient-to-r from-amber-500 via-orange-500 to-pink-500 hover:from-amber-400 hover:to-pink-400 text-white font-extrabold text-xs shadow-lg shadow-orange-500/20 active:scale-95 transition-all cursor-pointer"
                    >
                        <Gift className="w-4 h-4 text-amber-200" />
                        일일 출석 체크 & 긴급 파산 구제 룰렛
                    </button>

                    {balance < 100 && (
                        <div
                            onClick={() => setIsEconomyModalOpen(true)}
                            className="mt-3 p-3 rounded-xl bg-rose-500/20 border border-rose-500/40 text-rose-300 text-xs font-bold flex items-center justify-between cursor-pointer hover:bg-rose-500/30 transition-colors animate-pulse"
                        >
                            <span>🚨 잔고 $100 미만 경고!</span>
                            <span className="underline">구제 룰렛 돌리기 ➔</span>
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
                    <div className="bg-slate-900/50 p-4 rounded-xl border border-rose-500/30 mb-2">
                        <label className="block text-rose-400 text-sm font-bold mb-2"><span>🔒</span> 2차 인증 (계좌 비밀번호)</label>
                        <input type="password" value={accountPassword} maxLength={4} onChange={(e) => setAccountPassword(e.target.value)} placeholder="숫자 4자리" className="w-full bg-slate-900 text-white px-4 py-3 rounded-lg border border-slate-600 focus:border-rose-500 outline-none font-mono tracking-[0.5em] text-center text-lg" />
                    </div>
                    <div>
                        <label className="block text-slate-400 text-sm font-bold mb-2">금액 (Amount USD)</label>
                        <input type="number" min="0" value={amount} onChange={(e) => setAmount(Number(e.target.value))} placeholder="0.00" className="w-full bg-slate-900 text-white px-4 py-3 rounded-lg border border-slate-700 focus:border-sky-500 outline-none font-mono text-lg" />
                    </div>
                    <div className="flex gap-3">
                        <button onClick={() => handleAction('deposit')} className="flex-1 bg-emerald-600 hover:bg-emerald-500 text-white py-3 rounded-lg font-bold shadow-lg transition-colors">+ 입금 (Deposit)</button>
                        <button onClick={() => handleAction('withdrawal')} className="flex-1 bg-slate-600 hover:bg-slate-500 text-white py-3 rounded-lg font-bold shadow-lg transition-colors">- 출금 (Withdrawal)</button>
                    </div>
                    
                    <div className="flex items-center gap-4 my-2">
                        <hr className="flex-1 border-slate-700" />
                        <span className="text-xs text-slate-500 font-bold uppercase">or Transfer</span>
                        <hr className="flex-1 border-slate-700" />
                    </div>
                    
                    <div>
                        <label className="block text-slate-400 text-sm font-bold mb-2">송금 대상 (Target Username)</label>
                        <input type="text" value={targetUser} onChange={(e) => setTargetUser(e.target.value)} placeholder="받을 사람의 아이디" className="w-full bg-slate-900 text-white px-4 py-3 rounded-lg border border-slate-700 focus:border-sky-500 outline-none" />
                    </div>
                    <button onClick={() => handleAction('transfer')} className="w-full bg-indigo-600 hover:bg-indigo-500 text-white py-4 rounded-xl font-bold text-lg shadow-lg transition-colors">
                        송금하기 (Transfer) ➔
                    </button>
                </div>
            </div>
        </div>
    );
}