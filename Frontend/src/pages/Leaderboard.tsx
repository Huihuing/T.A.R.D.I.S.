import { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import { Trophy, Medal, Crown, TrendingUp, TrendingDown, RefreshCw } from 'lucide-react';

interface Ranker {
    rank: number;
    username: string;
    nickname: string;
    totalAsset: number;
    roi: number;
}

export default function Leaderboard() {
    const [rankings, setRankings] = useState<Ranker[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const currentUsername = localStorage.getItem('username');

    const fetchLeaderboard = async () => {
        setIsLoading(true);
        try {
            const res = await fetch('http://localhost:8080/api/leaderboard');
            const data = await res.json();
            setRankings(data);
        } catch (e) {
            console.error("랭킹 로딩 에러:", e);
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        fetchLeaderboard();
    }, []);

    const getRankIcon = (rank: number) => {
        if (rank === 1) return <Crown className="w-8 h-8 text-yellow-400 drop-shadow-[0_0_8px_rgba(250,204,21,0.8)]" />;
        if (rank === 2) return <Medal className="w-8 h-8 text-slate-300 drop-shadow-[0_0_8px_rgba(203,213,225,0.8)]" />;
        if (rank === 3) return <Medal className="w-8 h-8 text-amber-600 drop-shadow-[0_0_8px_rgba(217,119,6,0.8)]" />;
        return <span className="text-xl font-black text-slate-500">{rank}</span>;
    };

    return (
        <div className="p-4 sm:p-6 md:p-8 min-h-screen text-slate-200 bg-[#0b1120]">
            <div className="flex justify-between items-end mb-8 border-b border-slate-800 pb-4">
                <div>
                    <h1 className="text-3xl font-extrabold text-transparent bg-clip-text bg-gradient-to-r from-yellow-400 to-amber-500 flex items-center gap-3">
                        <Trophy className="w-8 h-8 text-yellow-400" /> Hall of Fame
                    </h1>
                    <p className="text-slate-400 text-sm mt-2">T.A.R.D.I.S. 최고의 투자자들을 확인하세요. (초기 자본금: $10,000)</p>
                </div>
                <button onClick={fetchLeaderboard} disabled={isLoading} className="flex items-center gap-2 bg-slate-800 hover:bg-slate-700 text-slate-300 px-4 py-2 rounded-xl font-bold transition-colors">
                    <RefreshCw className={`w-4 h-4 ${isLoading ? 'animate-spin' : ''}`} /> 갱신
                </button>
            </div>

            {isLoading && rankings.length === 0 ? (
                <div className="flex justify-center items-center py-20">
                    <RefreshCw className="w-10 h-10 animate-spin text-yellow-500" />
                </div>
            ) : (
                <div className="max-w-4xl mx-auto flex flex-col gap-4">
                    {/* 상위 1, 2, 3위 특별 디자인 */}
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
                        {rankings.slice(0, 3).map((ranker, idx) => {
                            const isMe = ranker.username === currentUsername;
                            return (
                                <motion.div 
                                    key={ranker.username} 
                                    initial={{ opacity: 0, y: 20 }} 
                                    animate={{ opacity: 1, y: 0 }} 
                                    transition={{ delay: idx * 0.1 }}
                                    className={`relative bg-slate-800/50 backdrop-blur-md p-6 rounded-3xl border flex flex-col items-center text-center shadow-2xl ${isMe ? 'border-yellow-500/50 bg-slate-800/80' : 'border-slate-700/50'}`}
                                >
                                    {isMe && <span className="absolute top-3 right-4 text-xs font-black bg-yellow-500 text-slate-900 px-2 py-0.5 rounded-full">ME</span>}
                                    <div className="mb-4">{getRankIcon(ranker.rank)}</div>
                                    <h3 className="text-2xl font-black text-white truncate w-full mb-1">{ranker.nickname}</h3>
                                    <p className="text-xs text-slate-500 mb-6">@{ranker.username}</p>
                                    
                                    <div className="w-full bg-slate-900/50 rounded-xl p-4 border border-slate-700/50">
                                        <div className="text-sm text-slate-400 mb-1">총 자산</div>
                                        <div className="text-2xl font-mono font-bold text-white mb-3">${ranker.totalAsset.toFixed(2)}</div>
                                        <div className="text-sm text-slate-400 mb-1">수익률 (ROI)</div>
                                        <div className={`flex items-center justify-center gap-1 font-bold text-lg ${ranker.roi >= 0 ? 'text-emerald-400' : 'text-rose-400'}`}>
                                            {ranker.roi >= 0 ? <TrendingUp className="w-5 h-5" /> : <TrendingDown className="w-5 h-5" />}
                                            {ranker.roi > 0 ? '+' : ''}{ranker.roi.toFixed(2)}%
                                        </div>
                                    </div>
                                </motion.div>
                            );
                        })}
                    </div>

                    {/* 4위부터 10위 리스트 디자인 */}
                    <div className="flex flex-col gap-3">
                        {rankings.slice(3).map((ranker, idx) => {
                            const isMe = ranker.username === currentUsername;
                            return (
                                <motion.div 
                                    key={ranker.username} 
                                    initial={{ opacity: 0, x: -20 }} 
                                    animate={{ opacity: 1, x: 0 }} 
                                    transition={{ delay: (idx + 3) * 0.1 }}
                                    className={`flex items-center justify-between bg-slate-800/30 p-4 rounded-2xl border transition-colors hover:bg-slate-800/80 ${isMe ? 'border-yellow-500/50 bg-slate-800/60' : 'border-slate-700/50'}`}
                                >
                                    <div className="flex items-center gap-4 sm:gap-6 w-1/3">
                                        <div className="w-8 flex justify-center">{getRankIcon(ranker.rank)}</div>
                                        <div className="flex flex-col">
                                            <span className="font-bold text-white flex items-center gap-2">
                                                {ranker.nickname} {isMe && <span className="text-[10px] bg-yellow-500 text-slate-900 px-1.5 py-0.5 rounded-sm">ME</span>}
                                            </span>
                                            <span className="text-xs text-slate-500">@{ranker.username}</span>
                                        </div>
                                    </div>
                                    
                                    <div className="flex-1 flex justify-between items-center pr-2">
                                        <div className="font-mono font-bold text-slate-200 hidden sm:block">
                                            ${ranker.totalAsset.toFixed(2)}
                                        </div>
                                        <div className={`font-bold flex items-center gap-1 ${ranker.roi >= 0 ? 'text-emerald-400' : 'text-rose-400'}`}>
                                            {ranker.roi >= 0 ? '+' : ''}{ranker.roi.toFixed(2)}%
                                        </div>
                                    </div>
                                </motion.div>
                            );
                        })}
                    </div>
                </div>
            )}
        </div>
    );
}