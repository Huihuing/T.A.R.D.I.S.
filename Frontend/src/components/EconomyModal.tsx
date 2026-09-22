import { API_URL } from '../config';
import { getStoredToken } from '../auth';
import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Calendar, Gift, Coins, CheckCircle2, Sparkles, X, Flame, TrendingUp, MessageSquare, Award, LifeBuoy } from 'lucide-react';

interface EconomyModalProps {
    isOpen: boolean;
    onClose: () => void;
    onBalanceUpdate: (newBalance: number) => void;
    currentBalance: number;
}

interface QuestState {
    completed: boolean;
    claimed?: boolean;
    reward: number;
}

interface EconomyStatus {
    balance: number;
    canCheckIn: boolean;
    attendanceStreak: number;
    lastCheckInDate: string | null;
    canClaimBankruptcy: boolean;
    bankruptcyBalanceLow: boolean;
    bankruptcyHoursRemaining: number;
    quests: {
        checkIn: QuestState;
        trade: QuestState;
        community: QuestState;
    };
}

export default function EconomyModal({ isOpen, onClose, onBalanceUpdate, currentBalance }: EconomyModalProps) {
    const [activeTab, setActiveTab] = useState<'checkin' | 'quests' | 'relief'>('checkin');
    const [status, setStatus] = useState<EconomyStatus | null>(null);
    const [loading, setLoading] = useState<boolean>(false);
    const [message, setMessage] = useState<string | null>(null);

    const getAuthHeaders = () => {
        const token = getStoredToken();
        return {
            'Content-Type': 'application/json',
            ...(token && { 'Authorization': `Bearer ${token}` })
        };
    };

    const fetchStatus = async () => {
        try {
            const res = await fetch(`${API_URL}/api/economy/status`, { headers: getAuthHeaders() });
            if (res.ok) {
                const data = await res.json();
                setStatus(data);
                if (data.balance !== undefined) onBalanceUpdate(data.balance);
            }
        } catch (e) {
            console.error('가상 경제 상태 조회 실패', e);
        }
    };

    useEffect(() => {
        if (isOpen) {
            fetchStatus();
            setMessage(null);
        }
    }, [isOpen]);

    const handleCheckIn = async () => {
        if (loading) return;
        setLoading(true);
        try {
            const res = await fetch(`${API_URL}/api/economy/check-in`, {
                method: 'POST',
                headers: getAuthHeaders()
            });
            const data = await res.json();
            setMessage(data.message || (res.ok ? '출석 체크 완료' : '출석 체크 실패'));
            if (res.ok && data.newBalance !== undefined) {
                onBalanceUpdate(data.newBalance);
                await fetchStatus();
            }
        } catch {
            setMessage('서버 통신 중 오류가 발생했습니다.');
        } finally {
            setLoading(false);
        }
    };

    const handleClaimQuest = async (questType: 'TRADE' | 'COMMUNITY') => {
        if (loading) return;
        setLoading(true);
        try {
            const res = await fetch(`${API_URL}/api/economy/claim-quest`, {
                method: 'POST',
                headers: getAuthHeaders(),
                body: JSON.stringify({ questType })
            });
            const data = await res.json();
            setMessage(data.message || (res.ok ? '보상 수령 완료' : '보상 수령 실패'));
            if (res.ok && data.newBalance !== undefined) {
                onBalanceUpdate(data.newBalance);
                await fetchStatus();
            }
        } catch {
            setMessage('서버 통신 중 오류가 발생했습니다.');
        } finally {
            setLoading(false);
        }
    };

    const handleClaimRelief = async () => {
        if (loading || !status?.canClaimBankruptcy) return;
        setLoading(true);
        setMessage(null);
        try {
            const res = await fetch(`${API_URL}/api/economy/bankruptcy-relief`, {
                method: 'POST',
                headers: getAuthHeaders()
            });
            const data = await res.json();
            setMessage(data.message || (res.ok ? '긴급 지원금 지급 완료' : '긴급 지원금 지급 실패'));
            if (res.ok && data.newBalance !== undefined) {
                onBalanceUpdate(data.newBalance);
                await fetchStatus();
            }
        } catch {
            setMessage('서버 통신 중 오류가 발생했습니다.');
        } finally {
            setLoading(false);
        }
    };

    if (!isOpen) return null;

    return (
        <AnimatePresence>
            <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 backdrop-blur-sm">
                <motion.div
                    initial={{ opacity: 0, scale: 0.95, y: 20 }}
                    animate={{ opacity: 1, scale: 1, y: 0 }}
                    exit={{ opacity: 0, scale: 0.95, y: 20 }}
                    className="relative w-full max-w-2xl bg-gradient-to-b from-slate-900 to-slate-950 border border-slate-700/60 rounded-3xl shadow-2xl overflow-hidden flex flex-col max-h-[90vh]"
                >
                    <div className="flex items-center justify-between p-6 border-b border-slate-800 bg-slate-900/50">
                        <div className="flex items-center gap-3">
                            <div className="p-2.5 rounded-2xl bg-gradient-to-tr from-sky-500 to-indigo-600 text-white">
                                <Gift className="w-6 h-6" />
                            </div>
                            <div>
                                <h2 className="text-xl font-extrabold text-white">가상 경제 & 일일 혜택</h2>
                                <p className="text-xs text-slate-400 mt-0.5">출석, 활동 보상, 조건부 긴급 지원으로 모의투자를 이어가세요.</p>
                            </div>
                        </div>
                        <button onClick={onClose} className="p-2 rounded-xl text-slate-400 hover:text-white hover:bg-slate-800">
                            <X className="w-5 h-5" />
                        </button>
                    </div>

                    <div className="flex border-b border-slate-800 bg-slate-950/60 px-6 pt-3 gap-2">
                        <button
                            onClick={() => { setActiveTab('checkin'); setMessage(null); }}
                            className={`pb-3 px-4 font-bold text-sm border-b-2 flex items-center gap-2 ${activeTab === 'checkin' ? 'border-sky-500 text-sky-400' : 'border-transparent text-slate-400'}`}
                        >
                            <Calendar className="w-4 h-4" /> 출석 체크
                        </button>
                        <button
                            onClick={() => { setActiveTab('quests'); setMessage(null); }}
                            className={`pb-3 px-4 font-bold text-sm border-b-2 flex items-center gap-2 ${activeTab === 'quests' ? 'border-sky-500 text-sky-400' : 'border-transparent text-slate-400'}`}
                        >
                            <Award className="w-4 h-4" /> 일일 퀘스트
                        </button>
                        <button
                            onClick={() => { setActiveTab('relief'); setMessage(null); }}
                            className={`pb-3 px-4 font-bold text-sm border-b-2 flex items-center gap-2 ${activeTab === 'relief' ? 'border-rose-500 text-rose-400' : 'border-transparent text-slate-400'}`}
                        >
                            <LifeBuoy className="w-4 h-4" /> 긴급 지원
                        </button>
                    </div>

                    {message && (
                        <div className="mx-6 mt-4 p-3.5 rounded-2xl bg-sky-500/10 border border-sky-500/30 text-sky-300 text-sm font-semibold flex items-center gap-2.5">
                            <Sparkles className="w-4 h-4 shrink-0 text-sky-400" />
                            <span>{message}</span>
                        </div>
                    )}

                    <div className="p-6 overflow-y-auto space-y-6">
                        {activeTab === 'checkin' && (
                            <div className="space-y-6">
                                <div className="p-5 rounded-2xl bg-gradient-to-r from-sky-900/40 via-indigo-900/20 to-slate-800/40 border border-sky-500/20 flex items-center justify-between">
                                    <div className="flex items-center gap-4">
                                        <div className="w-12 h-12 rounded-2xl bg-gradient-to-tr from-amber-500 to-orange-500 flex items-center justify-center text-white">
                                            <Flame className="w-7 h-7" />
                                        </div>
                                        <div>
                                            <span className="text-2xl font-black text-white font-mono">{status?.attendanceStreak || 0}일 연속</span>
                                            <p className="text-xs text-slate-400 mt-1">3일·7일 연속 출석 시 추가 보상이 적용됩니다.</p>
                                        </div>
                                    </div>
                                    <div className="text-right">
                                        <span className="text-xs text-slate-400 block">기본 출석 보상</span>
                                        <span className="text-2xl font-extrabold text-emerald-400 font-mono">+$500</span>
                                    </div>
                                </div>

                                <div className="grid grid-cols-7 gap-2">
                                    {[1, 2, 3, 4, 5, 6, 7].map((day) => {
                                        const streak = status?.attendanceStreak || 0;
                                        const done = day <= streak;
                                        return (
                                            <div key={day} className={`p-3 rounded-2xl border text-center ${done ? 'bg-emerald-500/10 border-emerald-500/40 text-emerald-400' : 'bg-slate-800/30 border-slate-700/40 text-slate-500'}`}>
                                                <span className="text-[11px] block font-bold mb-1">Day {day}</span>
                                                {done ? <CheckCircle2 className="w-5 h-5 mx-auto" /> : <Coins className="w-5 h-5 mx-auto opacity-70" />}
                                            </div>
                                        );
                                    })}
                                </div>

                                <button
                                    disabled={!status?.canCheckIn || loading}
                                    onClick={handleCheckIn}
                                    className={`w-full py-4 rounded-2xl font-bold text-base flex items-center justify-center gap-2 ${status?.canCheckIn ? 'bg-emerald-600 hover:bg-emerald-500 text-white' : 'bg-slate-800 text-slate-500 cursor-not-allowed'}`}
                                >
                                    {status?.canCheckIn ? '오늘 출석하고 지원금 받기' : '오늘 출석 완료'}
                                </button>
                            </div>
                        )}

                        {activeTab === 'quests' && (
                            <div className="space-y-4">
                                <QuestCard
                                    icon={<TrendingUp className="w-5 h-5" />}
                                    title="오늘 1회 이상 모의투자 매수"
                                    reward={300}
                                    completed={!!status?.quests?.trade?.completed}
                                    claimed={!!status?.quests?.trade?.claimed}
                                    loading={loading}
                                    onClaim={() => handleClaimQuest('TRADE')}
                                />
                                <QuestCard
                                    icon={<MessageSquare className="w-5 h-5" />}
                                    title="커뮤니티 글 또는 댓글 작성"
                                    reward={200}
                                    completed={!!status?.quests?.community?.completed}
                                    claimed={!!status?.quests?.community?.claimed}
                                    loading={loading}
                                    onClaim={() => handleClaimQuest('COMMUNITY')}
                                />
                            </div>
                        )}

                        {activeTab === 'relief' && (
                            <div className="space-y-5 text-center">
                                <div className="mx-auto w-20 h-20 rounded-3xl bg-rose-500/10 border border-rose-500/30 flex items-center justify-center">
                                    <LifeBuoy className="w-10 h-10 text-rose-400" />
                                </div>
                                <div>
                                    <h3 className="text-xl font-black text-white">긴급 구제 지원금</h3>
                                    <p className="text-sm text-slate-400 mt-2">
                                        보유 가상 현금이 <span className="text-rose-400 font-bold">$100 미만</span>일 때
                                        24시간에 한 번 <span className="text-emerald-400 font-bold">$1,000</span>을 지원합니다.
                                    </p>
                                </div>

                                <div className="p-4 rounded-2xl bg-slate-800/50 border border-slate-700/60 flex items-center justify-between text-sm">
                                    <span className="text-slate-400">현재 보유 현금</span>
                                    <span className={`font-mono font-bold ${currentBalance < 100 ? 'text-rose-400' : 'text-emerald-400'}`}>
                                        ${currentBalance.toFixed(2)}
                                    </span>
                                </div>

                                <button
                                    disabled={!status?.canClaimBankruptcy || loading}
                                    onClick={handleClaimRelief}
                                    className={`w-full py-4 rounded-2xl font-black ${status?.canClaimBankruptcy && !loading ? 'bg-rose-600 hover:bg-rose-500 text-white' : 'bg-slate-800 text-slate-500 cursor-not-allowed'}`}
                                >
                                    {loading
                                        ? '처리 중...'
                                        : !status?.bankruptcyBalanceLow
                                            ? '현금 $100 미만일 때 이용 가능'
                                            : !status?.canClaimBankruptcy
                                                ? `다음 지원까지 약 ${status?.bankruptcyHoursRemaining || 24}시간`
                                                : '긴급 지원금 $1,000 받기'}
                                </button>
                            </div>
                        )}
                    </div>

                    <div className="p-4 bg-slate-950/80 border-t border-slate-800/80 flex items-center justify-between text-xs text-slate-500 px-6">
                        <span>T.A.R.D.I.S.의 모든 자산과 거래는 모의투자용 가상 데이터입니다.</span>
                        <button onClick={onClose} className="px-4 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 font-semibold">닫기</button>
                    </div>
                </motion.div>
            </div>
        </AnimatePresence>
    );
}

function QuestCard({
    icon,
    title,
    reward,
    completed,
    claimed,
    loading,
    onClaim
}: {
    icon: React.ReactNode;
    title: string;
    reward: number;
    completed: boolean;
    claimed: boolean;
    loading: boolean;
    onClaim: () => void;
}) {
    return (
        <div className="p-4 rounded-2xl bg-slate-800/40 border border-slate-700/60 flex items-center justify-between gap-4">
            <div className="flex items-center gap-3.5">
                <div className="p-2.5 rounded-xl bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">{icon}</div>
                <div>
                    <h4 className="text-sm font-bold text-white">{title}</h4>
                    <p className="text-xs text-emerald-400 font-mono">+${reward}</p>
                </div>
            </div>
            {claimed ? (
                <span className="px-4 py-2 rounded-xl text-xs font-bold bg-slate-800 text-slate-500 border border-slate-700">수령 완료</span>
            ) : completed ? (
                <button
                    disabled={loading}
                    onClick={onClaim}
                    className="px-4 py-2 rounded-xl text-xs font-bold bg-amber-500 hover:bg-amber-400 text-white disabled:opacity-50"
                >
                    보상 받기
                </button>
            ) : (
                <span className="px-4 py-2 rounded-xl text-xs font-bold bg-slate-800 text-slate-400 border border-slate-700">진행 중</span>
            )}
        </div>
    );
}
