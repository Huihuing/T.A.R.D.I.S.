import { API_URL } from '../config';
import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
    ArrowLeft,
    MessageCircle,
    ReceiptText,
    TrendingDown,
    TrendingUp,
    UserRound
} from 'lucide-react';

type PublicProfile = {
    username: string;
    nickname: string;
    totalAsset: number;
    roi: number;
    tradeCount: number;
    postCount: number;
    commentCount: number;
};

export default function Profile() {
    const { username } = useParams();
    const [profile, setProfile] = useState<PublicProfile | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        const load = async () => {
            if (!username) {
                setError('사용자 아이디가 없습니다.');
                setIsLoading(false);
                return;
            }

            try {
                const res = await fetch(
                    `${API_URL}/api/profile/${encodeURIComponent(username)}`
                );
                if (res.status === 404) {
                    setError('존재하지 않는 사용자입니다.');
                    return;
                }

                const data = await res.json().catch(() => ({}));
                if (!res.ok) {
                    setError(data.message || '프로필을 불러오지 못했습니다.');
                    return;
                }

                setProfile(data);
            } catch {
                setError('프로필을 불러오는 중 오류가 발생했습니다.');
            } finally {
                setIsLoading(false);
            }
        };

        load();
    }, [username]);

    if (isLoading) {
        return (
            <div className="min-h-screen bg-[#0b1120] text-slate-300 flex items-center justify-center">
                프로필을 불러오는 중...
            </div>
        );
    }

    if (error || !profile) {
        return (
            <div className="min-h-screen bg-[#0b1120] text-slate-200 p-8">
                <Link
                    to="/leaderboard"
                    className="inline-flex items-center gap-2 text-sky-400 font-bold mb-8"
                >
                    <ArrowLeft className="w-4 h-4" />
                    랭킹으로
                </Link>
                <div className="max-w-xl mx-auto bg-slate-800/50 border border-slate-700 rounded-3xl p-8 text-center">
                    {error || '프로필을 찾을 수 없습니다.'}
                </div>
            </div>
        );
    }

    const positive = profile.roi >= 0;

    return (
        <div className="min-h-screen bg-[#0b1120] text-slate-200 p-4 sm:p-6 md:p-8">
            <div className="max-w-4xl mx-auto">
                <Link
                    to="/leaderboard"
                    className="inline-flex items-center gap-2 text-slate-400 hover:text-sky-400 font-bold mb-6"
                >
                    <ArrowLeft className="w-4 h-4" />
                    랭킹으로
                </Link>

                <div className="bg-slate-800/50 border border-slate-700/60 rounded-3xl p-7 shadow-2xl">
                    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-5 border-b border-slate-700 pb-6">
                        <div className="flex items-center gap-4">
                            <div className="w-16 h-16 rounded-2xl bg-sky-500/10 border border-sky-500/30 flex items-center justify-center">
                                <UserRound className="w-8 h-8 text-sky-400" />
                            </div>
                            <div>
                                <h1 className="text-3xl font-black text-white">
                                    {profile.nickname}
                                </h1>
                                <p className="text-slate-500">@{profile.username}</p>
                            </div>
                        </div>

                        <div className="text-left sm:text-right">
                            <p className="text-sm text-slate-400">가상 총자산</p>
                            <p className="text-3xl font-black font-mono text-white">
                                ${profile.totalAsset.toFixed(2)}
                            </p>
                            <p className={`font-bold flex sm:justify-end items-center gap-1 ${
                                positive ? 'text-emerald-400' : 'text-rose-400'
                            }`}>
                                {positive
                                    ? <TrendingUp className="w-4 h-4" />
                                    : <TrendingDown className="w-4 h-4" />}
                                {positive ? '+' : ''}
                                {profile.roi.toFixed(2)}%
                            </p>
                        </div>
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mt-6">
                        <StatCard
                            icon={<ReceiptText className="w-5 h-5 text-indigo-400" />}
                            label="모의거래"
                            value={profile.tradeCount}
                        />
                        <StatCard
                            icon={<MessageCircle className="w-5 h-5 text-sky-400" />}
                            label="게시글"
                            value={profile.postCount}
                        />
                        <StatCard
                            icon={<MessageCircle className="w-5 h-5 text-emerald-400" />}
                            label="댓글"
                            value={profile.commentCount}
                        />
                    </div>

                    <div className="mt-6 text-xs text-slate-500 leading-relaxed">
                        이메일, 비밀번호, PIN 및 상세 보유종목은 공개 프로필에 표시하지 않습니다.
                    </div>
                </div>
            </div>
        </div>
    );
}

function StatCard({
    icon,
    label,
    value
}: {
    icon: React.ReactNode;
    label: string;
    value: number;
}) {
    return (
        <div className="bg-slate-900/60 border border-slate-700 rounded-2xl p-5">
            <div className="flex items-center gap-2 text-slate-400 text-sm font-bold">
                {icon}
                {label}
            </div>
            <div className="text-2xl font-black text-white mt-3">
                {value.toLocaleString()}
            </div>
        </div>
    );
}
