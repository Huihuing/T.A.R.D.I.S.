import { API_URL } from '../config';
import { getAuthHeaders } from '../auth';
import { useEffect, useState } from 'react';
import {
    Activity,
    Bell,
    FileText,
    MessageCircle,
    ReceiptText,
    ShieldCheck,
    Target,
    Users
} from 'lucide-react';

type AdminStats = {
    members: number;
    posts: number;
    comments: number;
    trades: number;
    ledgerEntries: number;
    notifications: number;
    limitOrders: number;
    priceAlerts: number;
};

export default function Admin() {
    const [stats, setStats] = useState<AdminStats | null>(null);
    const [error, setError] = useState('');
    const [isLoading, setIsLoading] = useState(true);

    useEffect(() => {
        const load = async () => {
            try {
                const res = await fetch(`${API_URL}/api/admin/stats`, {
                    headers: getAuthHeaders(false)
                });
                const data = await res.json().catch(() => ({}));

                if (!res.ok) {
                    setError(data.message || '관리자 정보를 불러오지 못했습니다.');
                    return;
                }

                setStats(data);
            } catch {
                setError('관리자 통계를 불러오는 중 오류가 발생했습니다.');
            } finally {
                setIsLoading(false);
            }
        };

        load();
    }, []);

    return (
        <div className="min-h-screen bg-[#0b1120] text-slate-200 p-4 sm:p-6 md:p-8">
            <div className="max-w-6xl mx-auto">
                <div className="flex items-center gap-3 border-b border-slate-800 pb-5 mb-7">
                    <div className="w-12 h-12 rounded-2xl bg-rose-500/10 border border-rose-500/30 flex items-center justify-center">
                        <ShieldCheck className="w-7 h-7 text-rose-400" />
                    </div>
                    <div>
                        <h1 className="text-3xl font-black text-white">
                            Administration
                        </h1>
                        <p className="text-sm text-slate-400">
                            공개판 운영 상태를 확인합니다. 잔고 수정 기능은 제공하지 않습니다.
                        </p>
                    </div>
                </div>

                {isLoading && (
                    <div className="text-slate-400 py-10 text-center">
                        운영 통계를 불러오는 중...
                    </div>
                )}

                {error && (
                    <div className="bg-rose-500/10 border border-rose-500/30 text-rose-300 rounded-2xl p-5">
                        {error}
                    </div>
                )}

                {stats && (
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                        <Metric icon={<Users />} label="회원" value={stats.members} />
                        <Metric icon={<FileText />} label="게시글" value={stats.posts} />
                        <Metric icon={<MessageCircle />} label="댓글" value={stats.comments} />
                        <Metric icon={<ReceiptText />} label="모의거래" value={stats.trades} />
                        <Metric icon={<Activity />} label="원장 기록" value={stats.ledgerEntries} />
                        <Metric icon={<Bell />} label="알림 기록" value={stats.notifications} />
                        <Metric icon={<Target />} label="지정가 주문" value={stats.limitOrders} />
                        <Metric icon={<Target />} label="가격 알림" value={stats.priceAlerts} />
                    </div>
                )}
            </div>
        </div>
    );
}

function Metric({
    icon,
    label,
    value
}: {
    icon: React.ReactNode;
    label: string;
    value: number;
}) {
    return (
        <div className="bg-slate-800/50 border border-slate-700/60 rounded-2xl p-5 shadow-lg">
            <div className="flex items-center gap-2 text-slate-400 text-sm font-bold">
                <span className="text-sky-400">{icon}</span>
                {label}
            </div>
            <div className="text-3xl font-black text-white mt-3">
                {value.toLocaleString()}
            </div>
        </div>
    );
}
