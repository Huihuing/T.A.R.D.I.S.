import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Calendar, Gift, Coins, CheckCircle2, Sparkles, X, Flame, RotateCcw, TrendingUp, MessageSquare, Award } from 'lucide-react';

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

const ROULETTE_REWARDS = [1000, 2000, 3000, 5000, 1500, 2500];

export default function EconomyModal({ isOpen, onClose, onBalanceUpdate, currentBalance }: EconomyModalProps) {
    const [activeTab, setActiveTab] = useState<'checkin' | 'quests' | 'roulette'>('checkin');
    const [status, setStatus] = useState<EconomyStatus | null>(null);
    const [loading, setLoading] = useState<boolean>(false);
    const [message, setMessage] = useState<string | null>(null);

    // 룰렛 상태
    const [isSpinning, setIsSpinning] = useState<boolean>(false);
    const [rouletteRotation, setRouletteRotation] = useState<number>(0);
    const [wonAmount, setWonAmount] = useState<number | null>(null);

    const getAuthHeaders = () => {
        const token = localStorage.getItem('token');
        return {
            'Content-Type': 'application/json',
            ...(token && { 'Authorization': `Bearer ${token}` })
        };
    };

    const fetchStatus = async () => {
        const username = localStorage.getItem('username');
        if (!username) return;
        try {
            const res = await fetch(`http://localhost:8080/api/economy/status?username=${username}`, {
                headers: getAuthHeaders()
            });
            if (res.ok) {
                const data = await res.json();
                setStatus(data);
                if (data.balance !== undefined) {
                    onBalanceUpdate(data.balance);
                }
            }
        } catch (e) {
            console.error("가상 경제 상태 조회 실패", e);
        }
    };

    useEffect(() => {
        if (isOpen) {
            fetchStatus();
            setMessage(null);
            setWonAmount(null);
        }
    }, [isOpen]);

    const handleCheckIn = async () => {
        const username = localStorage.getItem('username');
        if (!username || loading) return;
        setLoading(true);
        try {
            const res = await fetch(`http://localhost:8080/api/economy/check-in`, {
                method: 'POST',
                headers: getAuthHeaders(),
                body: JSON.stringify({ username })
            });
            const data = await res.json();
            if (res.ok && data.status === 'SUCCESS') {
                setMessage(`🎉 ${data.message}`);
                if (data.newBalance !== undefined) onBalanceUpdate(data.newBalance);
                await fetchStatus();
            } else {
                setMessage(data.message || '출석 체크 실패');
            }
        } catch (e) {
            setMessage('서버 통신 중 오류가 발생했습니다.');
        } finally {
            setLoading(false);
        }
    };

    const handleClaimQuest = async (questType: 'TRADE' | 'COMMUNITY') => {
        const username = localStorage.getItem('username');
        if (!username || loading) return;
        setLoading(true);
        try {
            const res = await fetch(`http://localhost:8080/api/economy/claim-quest`, {
                method: 'POST',
                headers: getAuthHeaders(),
                body: JSON.stringify({ username, questType })
            });
            const data = await res.json();
            if (res.ok && data.status === 'SUCCESS') {
                setMessage(data.message);
                if (data.newBalance !== undefined) onBalanceUpdate(data.newBalance);
                await fetchStatus();
            } else {
                setMessage(data.message || '퀘스트 보상 수령 실패');
            }
        } catch (e) {
            setMessage('서버 통신 중 오류가 발생했습니다.');
        } finally {
            setLoading(false);
        }
    };

    const handleSpinRoulette = async () => {
        const username = localStorage.getItem('username');
        if (!username || isSpinning) return;

        // 랜덤 당첨 금액 선정
        const randomIndex = Math.floor(Math.random() * ROULETTE_REWARDS.length);
        const prize = ROULETTE_REWARDS[randomIndex];

        // 회전 계산: 기본 5바퀴(1800도) + 해당 섹터 각도
        const degreesPerSector = 360 / ROULETTE_REWARDS.length;
        const targetDegrees = 1800 + (360 - (randomIndex * degreesPerSector) - degreesPerSector / 2);

        setIsSpinning(true);
        setWonAmount(null);
        setMessage(null);
        setRouletteRotation(prev => prev + targetDegrees);

        // 3.5초 회전 후 백엔드에 결과 요청
        setTimeout(async () => {
            setIsSpinning(false);
            try {
                const res = await fetch(`http://localhost:8080/api/economy/bankruptcy-relief`, {
                    method: 'POST',
                    headers: getAuthHeaders(),
                    body: JSON.stringify({ username, rewardAmount: prize })
                });
                const data = await res.json();
                if (res.ok && data.status === 'SUCCESS') {
                    setWonAmount(prize);
                    setMessage(data.message);
                    if (data.newBalance !== undefined) onBalanceUpdate(data.newBalance);
                    await fetchStatus();
                } else {
                    setMessage(data.message || '구제금 지급 실패');
                }
            } catch (e) {
                setMessage('통신 오류가 발생했습니다.');
            }
        }, 3600);
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
                    {/* 상단 헤더 */}
                    <div className="flex items-center justify-between p-6 border-b border-slate-800 bg-slate-900/50">
                        <div className="flex items-center gap-3">
                            <div className="p-2.5 rounded-2xl bg-gradient-to-tr from-sky-500 to-indigo-600 shadow-lg shadow-sky-500/20 text-white">
                                <Gift className="w-6 h-6" />
                            </div>
                            <div>
                                <h2 className="text-xl font-extrabold text-white flex items-center gap-2">
                                    가상 경제 & 일일 혜택
                                    <span className="text-xs px-2.5 py-0.5 rounded-full bg-sky-500/20 text-sky-400 font-semibold border border-sky-500/30">
                                        Virtual Economy
                                    </span>
                                </h2>
                                <p className="text-xs text-slate-400 mt-0.5">
                                    매일 출석하고 미션을 완수하여 시드머니를 무료로 충전하세요!
                                </p>
                            </div>
                        </div>
                        <button
                            onClick={onClose}
                            className="p-2 rounded-xl text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
                        >
                            <X className="w-5 h-5" />
                        </button>
                    </div>

                    {/* 탭 네비게이션 */}
                    <div className="flex border-b border-slate-800 bg-slate-950/60 px-6 pt-3 gap-2">
                        <button
                            onClick={() => { setActiveTab('checkin'); setMessage(null); }}
                            className={`pb-3 px-4 font-bold text-sm transition-all border-b-2 flex items-center gap-2 ${
                                activeTab === 'checkin'
                                    ? 'border-sky-500 text-sky-400'
                                    : 'border-transparent text-slate-400 hover:text-slate-200'
                            }`}
                        >
                            <Calendar className="w-4 h-4" />
                            출석 체크 (Check-in)
                            {status?.canCheckIn && (
                                <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse"></span>
                            )}
                        </button>

                        <button
                            onClick={() => { setActiveTab('quests'); setMessage(null); }}
                            className={`pb-3 px-4 font-bold text-sm transition-all border-b-2 flex items-center gap-2 ${
                                activeTab === 'quests'
                                    ? 'border-sky-500 text-sky-400'
                                    : 'border-transparent text-slate-400 hover:text-slate-200'
                            }`}
                        >
                            <Award className="w-4 h-4" />
                            일일 퀘스트 (Quests)
                            {((status?.quests?.trade?.completed && !status?.quests?.trade?.claimed) ||
                              (status?.quests?.community?.completed && !status?.quests?.community?.claimed)) && (
                                <span className="w-2 h-2 rounded-full bg-amber-400 animate-pulse"></span>
                            )}
                        </button>

                        <button
                            onClick={() => { setActiveTab('roulette'); setMessage(null); }}
                            className={`pb-3 px-4 font-bold text-sm transition-all border-b-2 flex items-center gap-2 ${
                                activeTab === 'roulette'
                                    ? 'border-rose-500 text-rose-400'
                                    : 'border-transparent text-slate-400 hover:text-slate-200'
                            }`}
                        >
                            <RotateCcw className="w-4 h-4" />
                            파산 구제 룰렛 (Relief)
                            {status?.canClaimBankruptcy && (
                                <span className="px-1.5 py-0.2 text-[10px] rounded bg-rose-500 text-white font-bold animate-bounce">
                                    ON
                                </span>
                            )}
                        </button>
                    </div>

                    {/* 알림 메시지 배너 */}
                    {message && (
                        <div className="mx-6 mt-4 p-3.5 rounded-2xl bg-sky-500/10 border border-sky-500/30 text-sky-300 text-sm font-semibold flex items-center gap-2.5">
                            <Sparkles className="w-4 h-4 shrink-0 text-sky-400" />
                            <span>{message}</span>
                        </div>
                    )}

                    {/* 탭 본문 내용 */}
                    <div className="p-6 overflow-y-auto space-y-6">
                        {/* 1. 출석체크 탭 */}
                        {activeTab === 'checkin' && (
                            <div className="space-y-6">
                                {/* 스트릭 & 지원금 안내 카드 */}
                                <div className="p-5 rounded-2xl bg-gradient-to-r from-sky-900/40 via-indigo-900/20 to-slate-800/40 border border-sky-500/20 flex items-center justify-between">
                                    <div className="flex items-center gap-4">
                                        <div className="w-12 h-12 rounded-2xl bg-gradient-to-tr from-amber-500 to-orange-500 flex items-center justify-center shadow-lg shadow-orange-500/20 text-white">
                                            <Flame className="w-7 h-7" />
                                        </div>
                                        <div>
                                            <div className="flex items-center gap-2">
                                                <span className="text-2xl font-black text-white font-mono">
                                                    {status?.attendanceStreak || 0}일 연속
                                                </span>
                                                <span className="text-xs font-semibold px-2 py-0.5 rounded-full bg-orange-500/20 text-orange-400 border border-orange-500/30">
                                                    STREAK
                                                </span>
                                            </div>
                                            <p className="text-xs text-slate-400 mt-1">
                                                매일 연속 출석 시 보너스 추가 지급 (3일: +$300, 7일: +$1,000)
                                            </p>
                                        </div>
                                    </div>

                                    <div className="text-right">
                                        <span className="text-xs text-slate-400 block">오늘의 출석 보상</span>
                                        <span className="text-2xl font-extrabold text-emerald-400 font-mono">
                                            +${(status?.attendanceStreak || 0) >= 6 ? '1,500' : (status?.attendanceStreak || 0) >= 2 ? '800' : '500'}
                                        </span>
                                    </div>
                                </div>

                                {/* 7일 출석 현황 바 */}
                                <div>
                                    <h4 className="text-xs font-bold uppercase tracking-wider text-slate-400 mb-3">
                                        7 Days Attendance Track
                                    </h4>
                                    <div className="grid grid-cols-7 gap-2">
                                        {[1, 2, 3, 4, 5, 6, 7].map((day) => {
                                            const streak = status?.attendanceStreak || 0;
                                            const isPast = day <= streak;
                                            const isToday = day === streak + 1 && status?.canCheckIn;
                                            return (
                                                <div
                                                    key={day}
                                                    className={`p-3 rounded-2xl border text-center transition-all ${
                                                        isPast
                                                            ? 'bg-emerald-500/10 border-emerald-500/40 text-emerald-400'
                                                            : isToday
                                                            ? 'bg-sky-500/20 border-sky-400 text-white shadow-lg shadow-sky-500/20 animate-pulse'
                                                            : 'bg-slate-800/30 border-slate-700/40 text-slate-500'
                                                    }`}
                                                >
                                                    <span className="text-[11px] block font-bold mb-1">Day {day}</span>
                                                    {isPast ? (
                                                        <CheckCircle2 className="w-5 h-5 mx-auto text-emerald-400" />
                                                    ) : (
                                                        <Coins className="w-5 h-5 mx-auto opacity-70" />
                                                    )}
                                                    <span className="text-[10px] font-mono mt-1 block">
                                                        {day === 7 ? '+$1,500' : day >= 3 ? '+$800' : '+$500'}
                                                    </span>
                                                </div>
                                            );
                                        })}
                                    </div>
                                </div>

                                {/* 출석체크 액션 버튼 */}
                                <button
                                    disabled={!status?.canCheckIn || loading}
                                    onClick={handleCheckIn}
                                    className={`w-full py-4 rounded-2xl font-bold text-base flex items-center justify-center gap-2 transition-all shadow-xl ${
                                        status?.canCheckIn
                                            ? 'bg-gradient-to-r from-emerald-600 to-teal-600 hover:from-emerald-500 hover:to-teal-500 text-white shadow-emerald-600/30 cursor-pointer active:scale-[0.99]'
                                            : 'bg-slate-800 text-slate-500 border border-slate-700 cursor-not-allowed'
                                    }`}
                                >
                                    {status?.canCheckIn ? (
                                        <>
                                            <Sparkles className="w-5 h-5 text-amber-300" />
                                            오늘의 출석 체크하고 지원금 받기
                                        </>
                                    ) : (
                                        <>
                                            <CheckCircle2 className="w-5 h-5 text-emerald-400" />
                                            오늘 출석 완료! (내일 다시 도전하세요)
                                        </>
                                    )}
                                </button>
                            </div>
                        )}

                        {/* 2. 데일리 퀘스트 탭 */}
                        {activeTab === 'quests' && (
                            <div className="space-y-4">
                                <p className="text-xs text-slate-400">
                                    매일 자정에 미션이 초기화됩니다. 간단한 활동으로 추가 투자 자금을 모아보세요!
                                </p>

                                {/* 퀘스트 1: 출석체크 */}
                                <div className="p-4 rounded-2xl bg-slate-800/40 border border-slate-700/60 flex items-center justify-between">
                                    <div className="flex items-center gap-3.5">
                                        <div className="p-2.5 rounded-xl bg-sky-500/10 text-sky-400 border border-sky-500/20">
                                            <Calendar className="w-5 h-5" />
                                        </div>
                                        <div>
                                            <h4 className="text-sm font-bold text-white flex items-center gap-2">
                                                매일 출석 체크하기
                                                <span className="text-xs text-emerald-400 font-mono">+$500</span>
                                            </h4>
                                            <p className="text-xs text-slate-400">오늘 출석 버튼을 눌러 하루를 시작하세요.</p>
                                        </div>
                                    </div>
                                    <button
                                        onClick={() => setActiveTab('checkin')}
                                        className={`px-4 py-2 rounded-xl text-xs font-bold transition-colors ${
                                            !status?.canCheckIn
                                                ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 cursor-default'
                                                : 'bg-sky-600 hover:bg-sky-500 text-white'
                                        }`}
                                    >
                                        {!status?.canCheckIn ? '완료됨' : '출석하러 가기'}
                                    </button>
                                </div>

                                {/* 퀘스트 2: 1회 이상 매수 */}
                                <div className="p-4 rounded-2xl bg-slate-800/40 border border-slate-700/60 flex items-center justify-between">
                                    <div className="flex items-center gap-3.5">
                                        <div className="p-2.5 rounded-xl bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
                                            <TrendingUp className="w-5 h-5" />
                                        </div>
                                        <div>
                                            <h4 className="text-sm font-bold text-white flex items-center gap-2">
                                                오늘 1회 이상 모의투자 매수
                                                <span className="text-xs text-emerald-400 font-mono">+$300</span>
                                            </h4>
                                            <p className="text-xs text-slate-400">원하는 종목을 1주 이상 매수해 포트폴리오를 구성하세요.</p>
                                        </div>
                                    </div>
                                    {status?.quests?.trade?.claimed ? (
                                        <span className="px-4 py-2 rounded-xl text-xs font-bold bg-slate-800 text-slate-500 border border-slate-700">
                                            수령 완료
                                        </span>
                                    ) : status?.quests?.trade?.completed ? (
                                        <button
                                            disabled={loading}
                                            onClick={() => handleClaimQuest('TRADE')}
                                            className="px-4 py-2 rounded-xl text-xs font-bold bg-gradient-to-r from-amber-500 to-orange-500 text-white shadow-lg shadow-orange-500/20 hover:scale-105 active:scale-95 transition-all"
                                        >
                                            보상 받기 ($300)
                                        </button>
                                    ) : (
                                        <span className="px-4 py-2 rounded-xl text-xs font-bold bg-slate-800 text-slate-400 border border-slate-700">
                                            진행 중 (0/1)
                                        </span>
                                    )}
                                </div>

                                {/* 퀘스트 3: 커뮤니티 글/댓글 작성 */}
                                <div className="p-4 rounded-2xl bg-slate-800/40 border border-slate-700/60 flex items-center justify-between">
                                    <div className="flex items-center gap-3.5">
                                        <div className="p-2.5 rounded-xl bg-purple-500/10 text-purple-400 border border-purple-500/20">
                                            <MessageSquare className="w-5 h-5" />
                                        </div>
                                        <div>
                                            <h4 className="text-sm font-bold text-white flex items-center gap-2">
                                                커뮤니티 글 또는 댓글 작성
                                                <span className="text-xs text-emerald-400 font-mono">+$200</span>
                                            </h4>
                                            <p className="text-xs text-slate-400">투자 인사이트나 의견을 게시판/댓글로 나누어보세요.</p>
                                        </div>
                                    </div>
                                    {status?.quests?.community?.claimed ? (
                                        <span className="px-4 py-2 rounded-xl text-xs font-bold bg-slate-800 text-slate-500 border border-slate-700">
                                            수령 완료
                                        </span>
                                    ) : status?.quests?.community?.completed ? (
                                        <button
                                            disabled={loading}
                                            onClick={() => handleClaimQuest('COMMUNITY')}
                                            className="px-4 py-2 rounded-xl text-xs font-bold bg-gradient-to-r from-amber-500 to-orange-500 text-white shadow-lg shadow-orange-500/20 hover:scale-105 active:scale-95 transition-all"
                                        >
                                            보상 받기 ($200)
                                        </button>
                                    ) : (
                                        <span className="px-4 py-2 rounded-xl text-xs font-bold bg-slate-800 text-slate-400 border border-slate-700">
                                            진행 중 (0/1)
                                        </span>
                                    )}
                                </div>
                            </div>
                        )}

                        {/* 3. 파산 구제 룰렛 탭 */}
                        {activeTab === 'roulette' && (
                            <div className="flex flex-col items-center text-center space-y-5 py-2">
                                <div className="max-w-md">
                                    <h3 className="text-lg font-black text-white flex items-center justify-center gap-2">
                                        🎰 파산 구제 럭키 룰렛
                                    </h3>
                                    <p className="text-xs text-slate-400 mt-1">
                                        보유 현금이 <span className="text-rose-400 font-bold">$100 미만</span>으로 떨어졌을 때,
                                        <br />최대 <span className="text-emerald-400 font-bold">$5,000</span>의 긴급 회생 자본금을 룰렛으로 지급합니다 (24시간 쿨타임).
                                    </p>
                                </div>

                                {/* 현재 조건 체크 박스 */}
                                <div className="w-full max-w-sm p-3.5 rounded-2xl bg-slate-800/50 border border-slate-700/60 flex items-center justify-between text-xs">
                                    <span className="text-slate-400">현재 보유 현금</span>
                                    <span className={`font-mono font-bold ${currentBalance < 100 ? 'text-rose-400' : 'text-emerald-400'}`}>
                                        ${currentBalance.toFixed(2)} {currentBalance < 100 ? '(구제 대상)' : '(정상 자산)'}
                                    </span>
                                </div>

                                {/* 룰렛 그래픽 시각화 */}
                                <div className="relative w-64 h-64 my-2 flex items-center justify-center">
                                    {/* 상단 화살표 포인터 */}
                                    <div className="absolute -top-3 z-20 w-0 h-0 border-l-[10px] border-l-transparent border-r-[10px] border-r-transparent border-t-[18px] border-t-rose-500 filter drop-shadow"></div>

                                    {/* 회전하는 휠 */}
                                    <motion.div
                                        animate={{ rotate: rouletteRotation }}
                                        transition={{ duration: 3.5, ease: [0.15, 0.9, 0.3, 1] }}
                                        className="w-64 h-64 rounded-full border-4 border-slate-700 shadow-2xl relative overflow-hidden bg-slate-900"
                                    >
                                        {ROULETTE_REWARDS.map((reward, i) => {
                                            const angle = (360 / ROULETTE_REWARDS.length) * i;
                                            const colors = ['#0284c7', '#4f46e5', '#7c3aed', '#059669', '#d97706', '#dc2626'];
                                            return (
                                                <div
                                                    key={i}
                                                    style={{
                                                        transform: `rotate(${angle}deg)`,
                                                        transformOrigin: '50% 100%',
                                                        backgroundColor: colors[i % colors.length]
                                                    }}
                                                    className="absolute top-0 left-[16.6%] w-[66.8%] h-[50%] flex items-start justify-center pt-3 text-white font-mono font-extrabold text-xs shadow-inner opacity-90 border-r border-white/20"
                                                >
                                                    ${reward.toLocaleString()}
                                                </div>
                                            );
                                        })}
                                        {/* 중앙 축 */}
                                        <div className="absolute inset-0 m-auto w-12 h-12 rounded-full bg-slate-950 border-2 border-slate-600 flex items-center justify-center shadow-lg z-10 text-white font-bold text-[10px]">
                                            SPIN
                                        </div>
                                    </motion.div>
                                </div>

                                {/* 당첨 결과 안내 */}
                                {wonAmount && (
                                    <motion.div
                                        initial={{ scale: 0.8, opacity: 0 }}
                                        animate={{ scale: 1, opacity: 1 }}
                                        className="p-3.5 rounded-2xl bg-emerald-500/20 border border-emerald-500/40 text-emerald-300 font-bold text-sm"
                                    >
                                        🎉 축하합니다! 긴급 구제 지원금 ${wonAmount.toLocaleString()} 획득!
                                    </motion.div>
                                )}

                                {/* 룰렛 버튼 */}
                                <button
                                    disabled={!status?.canClaimBankruptcy || isSpinning}
                                    onClick={handleSpinRoulette}
                                    className={`w-full max-w-sm py-4 rounded-2xl font-black text-base transition-all shadow-xl ${
                                        status?.canClaimBankruptcy && !isSpinning
                                            ? 'bg-gradient-to-r from-rose-600 via-pink-600 to-indigo-600 hover:from-rose-500 hover:to-indigo-500 text-white shadow-rose-600/30 cursor-pointer hover:scale-105 active:scale-95'
                                            : 'bg-slate-800 text-slate-500 border border-slate-700 cursor-not-allowed'
                                    }`}
                                >
                                    {isSpinning ? (
                                        <span className="flex items-center justify-center gap-2">
                                            <RotateCcw className="w-5 h-5 animate-spin" />
                                            행운의 룰렛 회전 중...
                                        </span>
                                    ) : !status?.bankruptcyBalanceLow ? (
                                        `자산 조건 미달 (현금 $100 미만 시 활성화)`
                                    ) : !status?.canClaimBankruptcy ? (
                                        `쿨타임 대기 중 (남은 시간: ${status?.bankruptcyHoursRemaining || 24}시간)`
                                    ) : (
                                        `🎰 파산 구제 룰렛 돌리기 (SPIN)`
                                    )}
                                </button>
                            </div>
                        )}
                    </div>

                    {/* 하단 닫기 푸터 */}
                    <div className="p-4 bg-slate-950/80 border-t border-slate-800/80 flex items-center justify-between text-xs text-slate-500 px-6">
                        <span>💡 T.A.R.D.I.S. 가상 경제 시스템은 유저의 건전한 모의투자를 지원합니다.</span>
                        <button
                            onClick={onClose}
                            className="px-4 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 font-semibold transition-colors"
                        >
                            닫기
                        </button>
                    </div>
                </motion.div>
            </div>
        </AnimatePresence>
    );
}
