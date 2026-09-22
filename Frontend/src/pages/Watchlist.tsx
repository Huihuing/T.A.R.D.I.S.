import { API_URL, WS_URL } from '../config';
import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import { TrendingUp, TrendingDown, RefreshCw, X, Star, Bell, Trash2 } from 'lucide-react';
import { authFetch, getAuthHeaders, getStoredToken } from '../auth';
import { notify } from '../uiFeedback';

export default function Watchlist() {
    const navigate = useNavigate();
    const [stocks, setStocks] = useState<any[]>([]);
    const [isLoading, setIsLoading] = useState(false);
    const [selectedStock, setSelectedStock] = useState<any | null>(null);
    const [alerts, setAlerts] = useState<any[]>([]);
    const [alertTarget, setAlertTarget] = useState<number | ''>('');
    const [alertDirection, setAlertDirection] = useState<'ABOVE' | 'BELOW'>('ABOVE');
    const [isSavingAlert, setIsSavingAlert] = useState(false);

    const fetchWatchlist = async () => {
        setIsLoading(true);
        const username = localStorage.getItem('username');
        if (!username || username === 'Guest') { 
            setIsLoading(false); 
            return; 
        }
        
        try {
            // DB에서 찜한 종목 리스트 가져오기
            const res = await authFetch(
                `${API_URL}/api/bookmark`,
                { headers: getAuthHeaders(false) }
            );
            if (!res.ok) {
                setStocks([]);
                return;
            }
            const bookmarkedSymbols: string[] = await res.json();
            
            if (bookmarkedSymbols.length > 0) {
                // 해당 종목들의 실시간 시세 불러오기
                const promises = bookmarkedSymbols.map(async (symbol) => {
                    const quoteRes = await fetch(`${API_URL}/api/stock/quote?symbol=${symbol}`);
                    if (quoteRes.status === 429) return { symbol, error: '한도 초과' };
                    const data = await quoteRes.json();
                    return { symbol, ...data };
                });
                const newStocks = await Promise.all(promises);
                setStocks(newStocks);
            } else {
                setStocks([]);
            }
        } catch (e) {
            console.error(e);
        } finally {
            setIsLoading(false);
        }
    };

    const fetchAlerts = async () => {
        const token = getStoredToken();
        if (!token) {
            setAlerts([]);
            return;
        }

        try {
            const res = await authFetch(`${API_URL}/api/price-alerts`, {
                headers: getAuthHeaders(false)
            });
            const data = await res.json().catch(() => []);
            if (res.ok && Array.isArray(data)) {
                setAlerts(data);
            }
        } catch {
            setAlerts([]);
        }
    };

    useEffect(() => { 
        fetchWatchlist();
        fetchAlerts();
    }, []);

    const removeBookmark = async (e: React.MouseEvent, symbol: string) => {
        e.stopPropagation();
        const username = localStorage.getItem('username');
        if (!username) return;
        try {
            const res = await authFetch(`${API_URL}/api/bookmark/toggle`, {
                method: 'POST',
                headers: getAuthHeaders(),
                body: JSON.stringify({ symbol, price: 0 })
            });
            const data = await res.json();
            if (data.status === 'REMOVED') {
                setStocks(prev => prev.filter(s => s.symbol !== symbol));
            }
        } catch (e) {}
    };

    const createPriceAlert = async () => {
        if (!selectedStock?.symbol) return;
        if (!alertTarget || alertTarget <= 0) {
            notify('목표 가격을 올바르게 입력해주세요.', 'warning');
            return;
        }

        setIsSavingAlert(true);
        try {
            const res = await authFetch(`${API_URL}/api/price-alerts`, {
                method: 'POST',
                headers: getAuthHeaders(),
                body: JSON.stringify({
                    symbol: selectedStock.symbol,
                    direction: alertDirection,
                    targetPrice: Number(alertTarget)
                })
            });
            const data = await res.json().catch(() => ({}));
            if (!res.ok) {
                notify(data.message || '가격 알림 등록에 실패했습니다.', 'error');
                return;
            }

            notify(
                `${selectedStock.symbol}이(가) ${Number(alertTarget).toFixed(2)} ${alertDirection === 'ABOVE' ? '이상' : '이하'}일 때 알림을 보내도록 등록했습니다.`,
                'success'
            );
            setAlertTarget('');
            await fetchAlerts();
        } catch {
            notify('가격 알림 등록 중 오류가 발생했습니다.', 'error');
        } finally {
            setIsSavingAlert(false);
        }
    };

    const deletePriceAlert = async (id: number) => {
        try {
            const res = await authFetch(`${API_URL}/api/price-alerts/${id}`, {
                method: 'DELETE',
                headers: getAuthHeaders(false)
            });
            const data = await res.json().catch(() => ({}));
            if (!res.ok) {
                notify(data.message || '가격 알림 삭제에 실패했습니다.', 'error');
                return;
            }
            setAlerts(prev => prev.filter(item => item.id !== id));
            notify('가격 알림을 삭제했습니다.', 'success');
        } catch {
            notify('가격 알림 삭제 중 오류가 발생했습니다.', 'error');
        }
    };

    const isGuest = (localStorage.getItem('username') || 'Guest') === 'Guest';

    return (
        <div className="p-4 sm:p-6 md:p-8 min-h-screen text-slate-200 bg-[#0b1120] relative">
            <div className="mb-8 border-b border-slate-800 pb-4">
                <h1 className="text-3xl font-extrabold text-transparent bg-clip-text bg-gradient-to-r from-sky-400 to-indigo-400 flex items-center gap-3">
                    <Star className="w-8 h-8 text-yellow-400 fill-yellow-400" /> My Watchlist
                </h1>
                <p className="text-slate-400 text-sm mt-2">내가 찜한 관심 종목들을 한눈에 모아보세요.</p>
            </div>

            {isGuest ? (
                <div className="text-center py-20 bg-slate-800/30 rounded-3xl border border-slate-700/50">
                    <Star className="w-16 h-16 text-slate-600 mx-auto mb-4" />
                    <h2 className="text-xl font-bold text-slate-300 mb-2">로그인이 필요합니다</h2>
                    <p className="text-slate-500 text-sm">관심 종목을 등록하고 관리하려면 로그인해 주세요.</p>
                </div>
            ) : isLoading ? (
                <div className="flex flex-col items-center justify-center py-20">
                    <RefreshCw className="w-10 h-10 animate-spin text-sky-500 mb-4" />
                    <span className="text-slate-400 font-bold">관심 종목 시세 불러오는 중...</span>
                </div>
            ) : stocks.length === 0 ? (
                <div className="text-center py-20 bg-slate-800/30 rounded-3xl border border-slate-700/50">
                    <Star className="w-16 h-16 text-slate-600 mx-auto mb-4" />
                    <h2 className="text-xl font-bold text-slate-300 mb-2">등록된 관심 종목이 없습니다</h2>
                    <p className="text-slate-500 text-sm">Stock & Fund 메뉴에서 별표(⭐)를 눌러 추가해 보세요.</p>
                </div>
            ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-6 relative z-10">
                    {stocks.map((stock, index) => {
                        const isUp = stock.d > 0;
                        const isDown = stock.d < 0;

                        return (
                            <motion.div key={`${stock.symbol}-${index}`} initial={{ opacity: 0, scale: 0.9 }} animate={{ opacity: 1, scale: 1 }} transition={{ duration: 0.2 }} onClick={() => !stock.error && setSelectedStock(stock)} className={`bg-slate-800/50 backdrop-blur-md p-5 rounded-3xl border border-slate-700/50 shadow-lg transition-all flex flex-col relative overflow-hidden ${stock.error ? 'opacity-70' : 'cursor-pointer hover:border-sky-500 hover:-translate-y-1'}`}>
                                {stock.error ? (
                                    <div className="flex flex-col items-center justify-center h-full text-slate-500 py-6"><RefreshCw className="w-8 h-8 mb-2 opacity-20" /><span className="text-sm font-bold text-rose-500/80">API 호출 대기중...</span></div>
                                ) : (
                                    <>
                                        <div className="flex justify-between items-start mb-4">
                                            <h2 className="text-xl font-black text-white">{stock.symbol}</h2>
                                            <div className="flex items-center gap-3">
                                                {isUp && <TrendingUp className="w-6 h-6 text-emerald-500" />}
                                                {isDown && <TrendingDown className="w-6 h-6 text-rose-500" />}
                                                <button onClick={(e) => removeBookmark(e, stock.symbol)} className="z-10 hover:scale-110 transition-transform">
                                                    <Star className="w-6 h-6 fill-yellow-400 text-yellow-400" />
                                                </button>
                                            </div>
                                        </div>
                                        <div className="mt-auto">
                                            <p className="text-3xl font-mono font-bold tracking-tight text-white">${stock.c?.toFixed(2)}</p>
                                            <div className="flex items-center gap-2 mt-2">
                                                <span className={`text-sm font-bold ${isUp ? 'text-emerald-500' : isDown ? 'text-rose-500' : 'text-slate-400'}`}>{isUp ? '+' : ''}{stock.d?.toFixed(2)}</span>
                                                <span className={`text-sm px-2 py-0.5 rounded-md ${isUp ? 'bg-emerald-500/20 text-emerald-400' : isDown ? 'bg-rose-500/20 text-rose-400' : 'bg-slate-700 text-slate-300'}`}>{isUp ? '+' : ''}{stock.dp?.toFixed(2)}%</span>
                                            </div>
                                        </div>
                                    </>
                                )}
                            </motion.div>
                        );
                    })}
                </div>
            )}

            {!isGuest && (
                <div className="mt-8 bg-slate-800/40 border border-slate-700/50 rounded-3xl p-5">
                    <div className="flex items-center gap-2 mb-4">
                        <Bell className="w-5 h-5 text-amber-400" />
                        <h2 className="font-extrabold text-white">등록된 가격 알림</h2>
                        <span className="text-xs text-slate-500">
                            {alerts.filter(item => item.active).length}개 활성
                        </span>
                    </div>

                    {alerts.length === 0 ? (
                        <p className="text-sm text-slate-500">
                            관심 종목을 눌러 목표 가격 알림을 등록할 수 있습니다.
                        </p>
                    ) : (
                        <div className="flex flex-col gap-2">
                            {alerts.map(item => (
                                <div
                                    key={item.id}
                                    className="flex items-center justify-between gap-3 bg-slate-900/50 border border-slate-700/60 rounded-xl px-4 py-3"
                                >
                                    <div>
                                        <p className="font-bold text-white">
                                            {item.symbol} · ${Number(item.targetPrice).toFixed(2)} {item.direction === 'ABOVE' ? '이상' : '이하'}
                                        </p>
                                        <p className="text-xs text-slate-500 mt-1">
                                            {item.active
                                                ? '감시 중'
                                                : item.triggeredAt
                                                    ? '조건 도달 완료'
                                                    : '비활성'}
                                        </p>
                                    </div>
                                    <button
                                        type="button"
                                        onClick={() => deletePriceAlert(item.id)}
                                        className="p-2 text-rose-400 hover:bg-rose-500/10 rounded-lg"
                                        aria-label="가격 알림 삭제"
                                    >
                                        <Trash2 className="w-4 h-4" />
                                    </button>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            )}

            <AnimatePresence>
                {selectedStock && (
                    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="fixed inset-0 bg-black/80 backdrop-blur-sm z-[100] flex items-center justify-center p-2 sm:p-6" onClick={() => setSelectedStock(null)}>
                        <motion.div initial={{ scale: 0.95, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} exit={{ scale: 0.95, opacity: 0 }} onClick={(e) => e.stopPropagation()} className="bg-slate-900 border border-slate-700 rounded-2xl sm:rounded-3xl w-full max-w-5xl shadow-2xl overflow-hidden flex flex-col md:flex-row h-full max-h-[90vh]">
                            <div className="flex-1 bg-slate-950 p-2 min-h-[300px] md:min-h-[500px]">
                                <iframe src={`https://s.tradingview.com/widgetembed/?symbol=${selectedStock.symbol}&interval=D&theme=dark&style=1&hide_top_toolbar=1&hide_side_toolbar=1&withdateranges=1&saveimage=0&locale=kr`} className="w-full h-full border-0 rounded-xl" allowTransparency={true} />
                            </div>
                            <div className="w-full md:w-80 p-4 sm:p-6 flex flex-col border-t md:border-t-0 md:border-l border-slate-800 overflow-y-auto">
                                <div className="flex justify-between items-start mb-6">
                                    <div><h2 className="text-2xl sm:text-3xl font-black text-white">{selectedStock.symbol}</h2></div>
                                    <button onClick={() => setSelectedStock(null)} className="text-slate-400 hover:text-white bg-slate-800 rounded-full p-1.5 sm:p-1 transition-colors"><X className="w-5 h-5 sm:w-6 sm:h-6" /></button>
                                </div>
                                <div className="mb-6 sm:mb-8">
                                    <p className="text-4xl sm:text-5xl font-mono font-bold text-white">${selectedStock.c?.toFixed(2)}</p>
                                    <div className="flex items-center gap-2 mt-2"><span className={`font-bold ${selectedStock.d > 0 ? 'text-emerald-500' : selectedStock.d < 0 ? 'text-rose-500' : 'text-slate-400'}`}>{selectedStock.d > 0 ? '+' : ''}{selectedStock.d?.toFixed(2)} ({selectedStock.dp > 0 ? '+' : ''}{selectedStock.dp?.toFixed(2)}%)</span></div>
                                </div>
                                <div className="grid grid-cols-2 gap-3 mb-auto">
                                    <div className="bg-slate-800/50 p-3 rounded-xl border border-slate-700/50"><p className="text-xs text-slate-400 mb-1">고가 (High)</p><p className="font-mono font-bold text-white">${selectedStock.h?.toFixed(2)}</p></div>
                                    <div className="bg-slate-800/50 p-3 rounded-xl border border-slate-700/50"><p className="text-xs text-slate-400 mb-1">저가 (Low)</p><p className="font-mono font-bold text-white">${selectedStock.l?.toFixed(2)}</p></div>
                                    <div className="bg-slate-800/50 p-3 rounded-xl border border-slate-700/50"><p className="text-xs text-slate-400 mb-1">시가 (Open)</p><p className="font-mono font-bold text-white">${selectedStock.o?.toFixed(2)}</p></div>
                                    <div className="bg-slate-800/50 p-3 rounded-xl border border-slate-700/50"><p className="text-xs text-slate-400 mb-1">전일 종가 (Prev)</p><p className="font-mono font-bold text-white">${selectedStock.pc?.toFixed(2)}</p></div>
                                </div>
                                <div className="mt-6 pt-5 border-t border-slate-800">
                                    <div className="flex items-center gap-2 mb-3">
                                        <Bell className="w-4 h-4 text-amber-400" />
                                        <h3 className="font-bold text-white text-sm">가격 도달 알림</h3>
                                    </div>
                                    <div className="grid grid-cols-[1fr_auto] gap-2">
                                        <input
                                            type="number"
                                            min="0.01"
                                            step="0.01"
                                            value={alertTarget}
                                            onChange={e => setAlertTarget(
                                                e.target.value === ''
                                                    ? ''
                                                    : Number(e.target.value)
                                            )}
                                            placeholder="목표 가격"
                                            className="bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-white outline-none focus:border-amber-500"
                                        />
                                        <select
                                            value={alertDirection}
                                            onChange={e => setAlertDirection(
                                                e.target.value as 'ABOVE' | 'BELOW'
                                            )}
                                            className="bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-white outline-none"
                                        >
                                            <option value="ABOVE">이상</option>
                                            <option value="BELOW">이하</option>
                                        </select>
                                    </div>
                                    <button
                                        type="button"
                                        onClick={createPriceAlert}
                                        disabled={isSavingAlert}
                                        className="w-full mt-2 bg-amber-500 hover:bg-amber-400 text-slate-950 font-black py-2.5 rounded-lg disabled:opacity-50"
                                    >
                                        {isSavingAlert ? '등록 중...' : '가격 알림 등록'}
                                    </button>
                                </div>

                                <button
                                    type="button"
                                    onClick={() => navigate(
                                        `/stock?symbol=${encodeURIComponent(selectedStock.symbol)}`
                                    )}
                                    className="w-full mt-4 bg-sky-600 hover:bg-sky-500 text-white font-bold py-3 sm:py-4 rounded-xl transition-colors shadow-lg text-base sm:text-lg"
                                >
                                    이 종목 거래하기
                                </button>
                            </div>
                        </motion.div>
                    </motion.div>
                )}
            </AnimatePresence>
        </div>
    );
}