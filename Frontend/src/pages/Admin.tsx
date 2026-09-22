import { API_URL } from '../config';
import { getAuthHeaders } from '../auth';
import { useEffect, useState } from 'react';
import {
    Activity,
    Bell,
    CheckCircle2,
    FileText,
    Flag,
    MessageCircle,
    ReceiptText,
    ShieldCheck,
    Target,
    Trash2,
    Users,
    Search
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
    openReports: number;
};

type ModerationReport = {
    id: number;
    targetType: 'POST' | 'COMMENT';
    targetId: number;
    reason: string;
    detail?: string | null;
    reporterUsername?: string | null;
    reporterIp?: string | null;
    targetAuthor?: string | null;
    targetPreview: string;
    status: string;
    createdAt: string;
    resolvedAt?: string | null;
    resolvedBy?: string | null;
};

export default function Admin() {
    const [stats, setStats] = useState<AdminStats | null>(null);
    const [error, setError] = useState('');
    const [isLoading, setIsLoading] = useState(true);
    const [reports, setReports] = useState<ModerationReport[]>([]);
    const [moderationLoading, setModerationLoading] = useState(false);
    const [reportStatus, setReportStatus] = useState<'OPEN' | 'ACTIONED' | 'DISMISSED' | 'ALL'>('OPEN');
    const [targetFilter, setTargetFilter] = useState<'ALL' | 'POST' | 'COMMENT'>('ALL');
    const [reasonFilter, setReasonFilter] = useState('ALL');
    const [reportSearch, setReportSearch] = useState('');

    const loadAdminData = async () => {
        try {
            const [statsRes, reportsRes] = await Promise.all([
                fetch(`${API_URL}/api/admin/stats`, {
                    headers: getAuthHeaders(false)
                }),
                fetch(`${API_URL}/api/admin/reports?status=${reportStatus}`, {
                    headers: getAuthHeaders(false)
                })
            ]);

            const statsData = await statsRes.json().catch(() => ({}));
            const reportsData = await reportsRes.json().catch(() => ([]));

            if (!statsRes.ok) {
                setError(statsData.message || '관리자 정보를 불러오지 못했습니다.');
                return;
            }
            if (!reportsRes.ok) {
                setError(reportsData.message || '신고 내역을 불러오지 못했습니다.');
                return;
            }

            setStats(statsData);
            setReports(Array.isArray(reportsData) ? reportsData : []);
            setError('');
        } catch {
            setError('관리자 데이터를 불러오는 중 오류가 발생했습니다.');
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        loadAdminData();
    }, [reportStatus]);

    const resolveReport = async (
        report: ModerationReport,
        action: 'DISMISS' | 'DELETE_CONTENT'
    ) => {
        const message = action === 'DELETE_CONTENT'
            ? '신고된 원문을 삭제하고 관련 신고를 처리 완료할까요?'
            : '이 신고를 기각 처리할까요?';

        if (!window.confirm(message)) return;

        setModerationLoading(true);
        try {
            const res = await fetch(
                `${API_URL}/api/admin/reports/${report.id}`,
                {
                    method: 'PATCH',
                    headers: getAuthHeaders(),
                    body: JSON.stringify({ action })
                }
            );
            const data = await res.json().catch(() => ({}));
            if (!res.ok) {
                alert(data?.message || '신고 처리에 실패했습니다.');
                return;
            }
            await loadAdminData();
        } catch {
            alert('신고 처리 중 오류가 발생했습니다.');
        } finally {
            setModerationLoading(false);
        }
    };

    const reasonLabel = (reason: string) => ({
        SPAM: '스팸/도배',
        ABUSE: '욕설/불쾌한 내용',
        HARASSMENT: '괴롭힘/공격적 내용',
        MISINFORMATION: '허위·오해 소지 정보',
        OTHER: '기타'
    }[reason] || reason);

    const formatKst = (value: string) => {
        const normalized = /[zZ]|[+-]\d{2}:\d{2}$/.test(value)
            ? value
            : `${value}+09:00`;
        return new Intl.DateTimeFormat('ko-KR', {
            timeZone: 'Asia/Seoul',
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
        }).format(new Date(normalized));
    };

    const statusLabel = (status: string) => ({
        OPEN: '미처리',
        ACTIONED: '원문 삭제',
        DISMISSED: '기각'
    }[status] || status);

    const statusClass = (status: string) => {
        if (status === 'OPEN') return 'bg-amber-500/10 text-amber-300 border-amber-500/20';
        if (status === 'ACTIONED') return 'bg-rose-500/10 text-rose-300 border-rose-500/20';
        return 'bg-emerald-500/10 text-emerald-300 border-emerald-500/20';
    };

    const filteredReports = reports.filter(report => {
        if (targetFilter !== 'ALL' && report.targetType !== targetFilter) return false;
        if (reasonFilter !== 'ALL' && report.reason !== reasonFilter) return false;

        const query = reportSearch.trim().toLowerCase();
        if (!query) return true;

        return [
            report.targetAuthor,
            report.targetPreview,
            report.detail,
            report.reporterUsername,
            report.reporterIp,
            String(report.targetId)
        ].filter(Boolean).some(value =>
            String(value).toLowerCase().includes(query)
        );
    });
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
                        <Metric icon={<Flag />} label="미처리 신고" value={stats.openReports} />
                    </div>
                )}

                {!isLoading && !error && (
                    <section className="mt-8 bg-slate-800/40 border border-slate-700/60 rounded-3xl p-5 sm:p-6">
                        <div className="flex items-center justify-between gap-3 mb-5">
                            <div>
                                <h2 className="text-xl font-black text-white flex items-center gap-2">
                                    <Flag className="w-5 h-5 text-amber-300" />
                                    커뮤니티 신고 큐
                                </h2>
                                <p className="text-sm text-slate-400 mt-1">
                                    상태·대상·사유별로 신고를 확인하고 처리 이력까지 추적할 수 있습니다.
                                </p>
                            </div>
                            <span className="text-xs font-bold px-3 py-1.5 rounded-full bg-amber-500/10 text-amber-300 border border-amber-500/20">
                                {filteredReports.length}건
                            </span>
                        </div>

                        <div className="mb-5 space-y-3">
                            <div className="flex flex-wrap gap-2">
                                {[
                                    ['OPEN', '미처리'],
                                    ['ACTIONED', '원문 삭제'],
                                    ['DISMISSED', '기각'],
                                    ['ALL', '전체']
                                ].map(([value, label]) => (
                                    <button
                                        key={value}
                                        type="button"
                                        onClick={() => setReportStatus(value as typeof reportStatus)}
                                        className={
                                            'rounded-xl border px-3 py-2 text-xs font-bold transition-colors '
                                            + (reportStatus === value
                                                ? 'border-sky-500/40 bg-sky-500/10 text-sky-300'
                                                : 'border-slate-700 bg-slate-900/50 text-slate-500 hover:text-slate-300')
                                        }
                                    >
                                        {label}
                                    </button>
                                ))}
                            </div>

                            <div className="grid gap-2 md:grid-cols-[1fr_160px_190px]">
                                <div className="relative">
                                    <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-500" />
                                    <input
                                        type="search"
                                        value={reportSearch}
                                        onChange={e => setReportSearch(e.target.value)}
                                        placeholder="작성자, 신고자, 내용, 번호 검색"
                                        className="w-full rounded-xl border border-slate-700 bg-slate-900 py-2.5 pl-10 pr-3 text-sm text-white outline-none focus:border-sky-500"
                                    />
                                </div>
                                <select
                                    value={targetFilter}
                                    onChange={e => setTargetFilter(e.target.value as typeof targetFilter)}
                                    className="rounded-xl border border-slate-700 bg-slate-900 px-3 py-2.5 text-sm text-slate-300 outline-none focus:border-sky-500"
                                >
                                    <option value="ALL">전체 대상</option>
                                    <option value="POST">게시글</option>
                                    <option value="COMMENT">댓글</option>
                                </select>
                                <select
                                    value={reasonFilter}
                                    onChange={e => setReasonFilter(e.target.value)}
                                    className="rounded-xl border border-slate-700 bg-slate-900 px-3 py-2.5 text-sm text-slate-300 outline-none focus:border-sky-500"
                                >
                                    <option value="ALL">전체 사유</option>
                                    <option value="SPAM">스팸/도배</option>
                                    <option value="ABUSE">욕설/불쾌한 내용</option>
                                    <option value="HARASSMENT">괴롭힘/공격적 내용</option>
                                    <option value="MISINFORMATION">허위·오해 소지 정보</option>
                                    <option value="OTHER">기타</option>
                                </select>
                            </div>
                        </div>

                        {filteredReports.length === 0 ? (
                            <div className="text-center text-slate-500 py-10">
                                조건에 맞는 신고 내역이 없습니다.
                            </div>
                        ) : (
                            <div className="space-y-4">
                                {filteredReports.map((report) => (
                                    <article
                                        key={report.id}
                                        className="bg-slate-900/70 border border-slate-700 rounded-2xl p-4 sm:p-5"
                                    >
                                        <div className="flex flex-col md:flex-row md:items-start md:justify-between gap-4">
                                            <div className="min-w-0 flex-1">
                                                <div className="flex flex-wrap items-center gap-2 text-xs font-bold">
                                                    <span className="px-2 py-1 rounded bg-rose-500/10 text-rose-300">
                                                        {report.targetType === 'POST' ? '게시글' : '댓글'} #{report.targetId}
                                                    </span>
                                                    <span className="px-2 py-1 rounded bg-amber-500/10 text-amber-300">
                                                        {reasonLabel(report.reason)}
                                                    </span>
                                                    <span className={'px-2 py-1 rounded border ' + statusClass(report.status)}>
                                                        {statusLabel(report.status)}
                                                    </span>
                                                    <span className="text-slate-500">
                                                        {formatKst(report.createdAt)}
                                                    </span>
                                                </div>

                                                <div className="mt-3 text-sm text-slate-300">
                                                    <span className="text-slate-500">작성자:</span>{' '}
                                                    {report.targetAuthor || '알 수 없음'}
                                                </div>
                                                <p className="mt-2 text-sm text-slate-200 whitespace-pre-wrap break-words">
                                                    {report.targetPreview}
                                                </p>

                                                {report.detail && (
                                                    <div className="mt-3 text-sm bg-slate-800 rounded-xl px-3 py-2 text-slate-300">
                                                        <span className="text-slate-500">신고 설명:</span>{' '}
                                                        {report.detail}
                                                    </div>
                                                )}

                                                <div className="mt-3 text-xs text-slate-500">
                                                    신고자: {report.reporterUsername || `Guest (${report.reporterIp || 'masked'})`}
                                                </div>
                                            </div>

                                            {report.status === 'OPEN' ? (
                                                <div className="flex md:flex-col gap-2 shrink-0">
                                                    <button
                                                        type="button"
                                                        disabled={moderationLoading}
                                                        onClick={() => resolveReport(report, 'DELETE_CONTENT')}
                                                        className="inline-flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg bg-rose-600/20 hover:bg-rose-600/30 text-rose-300 text-sm font-bold disabled:opacity-50"
                                                    >
                                                        <Trash2 className="w-4 h-4" />
                                                        원문 삭제
                                                    </button>
                                                    <button
                                                        type="button"
                                                        disabled={moderationLoading}
                                                        onClick={() => resolveReport(report, 'DISMISS')}
                                                        className="inline-flex items-center justify-center gap-1.5 px-3 py-2 rounded-lg bg-emerald-600/15 hover:bg-emerald-600/25 text-emerald-300 text-sm font-bold disabled:opacity-50"
                                                    >
                                                        <CheckCircle2 className="w-4 h-4" />
                                                        기각
                                                    </button>
                                                </div>
                                            ) : (
                                                <div className="shrink-0 rounded-xl border border-slate-700 bg-slate-800/60 px-3 py-2 text-xs text-slate-400 md:text-right">
                                                    <div className="font-bold text-slate-300">
                                                        {statusLabel(report.status)}
                                                    </div>
                                                    {report.resolvedAt && (
                                                        <div className="mt-1">
                                                            {formatKst(report.resolvedAt)}
                                                        </div>
                                                    )}
                                                    {report.resolvedBy && (
                                                        <div className="mt-1 text-slate-500">
                                                            처리: {report.resolvedBy}
                                                        </div>
                                                    )}
                                                </div>
                                            )}
                                        </div>
                                    </article>
                                ))}
                            </div>
                        )}
                    </section>
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
