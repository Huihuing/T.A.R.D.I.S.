import { useEffect, useState } from 'react';
import { io } from 'socket.io-client';
import { motion } from 'framer-motion';
import { AreaChart, Area, XAxis, YAxis, Tooltip, ResponsiveContainer, CartesianGrid } from 'recharts';

const socket = io('http://localhost:3000');
interface Stock { symbol: string; name: string; price: number; }
interface TradeHistory { id: number; tradeType: string; symbol: string; amount: number; price: number; tradeTime: string; }
interface ChartData { time: string; price: number; }
interface PortfolioItem { symbol: string; amount: number; averagePrice: number; }

export default function Dashboard() {
    const [stocks, setStocks] = useState<Stock[]>([]);
    const [prevPrices, setPrevPrices] = useState<Record<string, number>>({});
    const [balance, setBalance] = useState<number>(0);
    const [history, setHistory] = useState<TradeHistory[]>([]);
    const [chartData, setChartData] = useState<ChartData[]>([]);
    const [portfolio, setPortfolio] = useState<PortfolioItem[]>([]);

    // 📜 수정 1: 입력창이 완전히 비워질 수 있도록 상태 타입을 변경합니다.
    const [tradeAmount, setTradeAmount] = useState<number | ''>(1);

    useEffect(() => {
        fetchUserData();
    }, []);

    const fetchUserData = async () => {
        try {
            const balRes = await fetch('http://localhost:8080/api/trade/balance');
            setBalance(await balRes.json());
            const histRes = await fetch('http://localhost:8080/api/trade/history');
            setHistory(await histRes.json());
            const portRes = await fetch('http://localhost:8080/api/trade/portfolio');
            setPortfolio(await portRes.json());
        } catch (err) {
            console.error("데이터 조회 실패:", err);
        }
    };

    useEffect(() => {
        socket.on('stock_update', (data: Stock[]) => {
            setStocks((prev) => {
                const newPrevPrices: Record<string, number> = {};
                prev.forEach(s => newPrevPrices[s.symbol] = s.price);
                setPrevPrices(newPrevPrices);
                return data;
            });

            const aapl = data.find(s => s.symbol === 'AAPL');
            if (aapl) {
                setChartData(prevData => {
                    const now = new Date();
                    const timeStr = `${now.getHours()}:${String(now.getMinutes()).padStart(2, '0')}:${String(now.getSeconds()).padStart(2, '0')}`;
                    const newData = [...prevData, { time: timeStr, price: aapl.price }];
                    return newData.slice(-40);
                });
            }
        });

        return () => { socket.off('stock_update'); };
    }, []);

    const aaplStock = stocks.find(s => s.symbol === 'AAPL');
    const currentAaplPrice = aaplStock ? aaplStock.price : 0;
    const myAapl = portfolio.find(p => p.symbol === 'AAPL');
    let totalAssets = balance;
    let unrealizedProfit = 0;
    let profitRate = 0;

    if (myAapl && currentAaplPrice > 0) {
        const currentStockValue = myAapl.amount * currentAaplPrice;
        const totalBuyValue = myAapl.amount * myAapl.averagePrice;
        unrealizedProfit = currentStockValue - totalBuyValue;
        profitRate = (unrealizedProfit / totalBuyValue) * 100;
        totalAssets += currentStockValue;
    }

    // 매수/매도 시 빈칸이면 자동으로 1로 처리하는 안전 장치
    const getValidAmount = () => (typeof tradeAmount === 'number' && tradeAmount > 0 ? tradeAmount : 1);

    return (
        <div className="min-h-screen bg-slate-900 text-white p-8 font-sans flex flex-col">
            <header className="mb-10 text-center">
                <h1 className="text-4xl font-extrabold text-sky-400">T.A.R.D.I.S.</h1>
                <p className="text-slate-400">Time And Relative Dimension In Stocks</p>
            </header>

            <div className="max-w-6xl mx-auto w-full grid grid-cols-1 lg:grid-cols-3 gap-8">
                <div className="col-span-2 flex flex-col gap-6">
                    <div className="bg-slate-800 border border-slate-700 rounded-xl p-6 h-80 shadow-lg">
                        <div className="flex justify-between items-center mb-4">
                            <h2 className="text-lg font-bold text-slate-200">AAPL / USD Real-time Chart</h2>
                            <span className="text-xs bg-sky-500/20 text-sky-400 px-2 py-1 rounded font-mono">LIVE</span>
                        </div>
                        <ResponsiveContainer width="100%" height="80%">
                            <AreaChart data={chartData}>
                                <defs>
                                    <linearGradient id="colorPrice" x1="0" y1="0" x2="0" y2="1">
                                        <stop offset="5%" stopColor="#38bdf8" stopOpacity={0.4} />
                                        <stop offset="95%" stopColor="#38bdf8" stopOpacity={0.0} />
                                    </linearGradient>
                                </defs>
                                <CartesianGrid strokeDasharray="3 3" stroke="#334155" vertical={false} />
                                <XAxis dataKey="time" stroke="#64748b" fontSize={11} tickLine={false} minTickGap={20} />
                                <YAxis
                                    domain={[(dataMin: number) => Math.floor(dataMin - 2), (dataMax: number) => Math.ceil(dataMax + 2)]}
                                    stroke="#64748b" fontSize={11} tickLine={false} orientation="right"
                                    tickFormatter={(val) => `$${val}`}
                                />
                                <Tooltip
                                    contentStyle={{ backgroundColor: '#0f172a', border: '1px solid #334155', borderRadius: '8px' }}
                                    itemStyle={{ color: '#38bdf8', fontWeight: 'bold' }}
                                    formatter={(value: number) => [`$${value.toFixed(2)}`, 'Price']}
                                />
                                {/* 📜 수정 2: 애니메이션을 다시 켜고(true), 300ms 동안 부드럽게 이어지도록 세팅합니다. */}
                                <Area
                                    type="monotone"
                                    dataKey="price"
                                    stroke="#38bdf8"
                                    strokeWidth={2.5}
                                    fillOpacity={1}
                                    fill="url(#colorPrice)"
                                    isAnimationActive={false}
                                    animationDuration={300}
                                    animationEasing="ease-in-out"
                                />
                            </AreaChart>
                        </ResponsiveContainer>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        {stocks.map((stock) => {
                            const prev = prevPrices[stock.symbol] || stock.price;
                            const isUp = stock.price > prev;
                            const isDown = stock.price < prev;
                            const color = isUp ? 'text-green-400' : isDown ? 'text-red-400' : 'text-slate-200';
                            const bg = isUp ? 'rgba(74,222,128,0.2)' : isDown ? 'rgba(248,113,113,0.2)' : 'rgba(30,41,59,1)';

                            return (
                                <motion.div key={stock.symbol} className="p-6 rounded-xl bg-slate-800 border border-slate-700 flex flex-col items-center" animate={{ backgroundColor: [bg, 'rgba(30,41,59,1)'] }} transition={{ duration: 0.5 }}>
                                    <h2 className="text-xl font-bold">{stock.symbol}</h2>
                                    <div className={`text-3xl font-mono mt-2 ${color}`}>${stock.price.toFixed(2)}</div>
                                </motion.div>
                            );
                        })}
                    </div>
                </div>

                <div className="flex flex-col gap-6">
                    <div className="bg-slate-800 border border-slate-700 rounded-xl p-6">
                        <h2 className="text-xl font-bold mb-4 border-b border-slate-600 pb-2">My Assets</h2>
                        <div className="mb-4">
                            <p className="text-slate-400 text-sm">Total Assets (총 자산)</p>
                            <p className="text-3xl font-mono text-white">${totalAssets.toFixed(2)}</p>
                        </div>
                        <div className="flex justify-between text-sm mb-2">
                            <span className="text-slate-400">Cash Balance (현금)</span>
                            <span className="font-mono text-sky-300">${balance.toFixed(2)}</span>
                        </div>
                        {myAapl && (
                            <div className="mt-4 p-3 bg-slate-700 rounded-lg">
                                <div className="flex justify-between items-center mb-1">
                                    <span className="font-bold">AAPL 보유량</span>
                                    <span className="font-mono">{myAapl.amount} 주</span>
                                </div>
                                <div className="flex justify-between items-center text-sm">
                                    <span className="text-slate-400">평균 단가</span>
                                    <span className="font-mono">${myAapl.averagePrice.toFixed(2)}</span>
                                </div>
                                <div className={`flex justify-between items-center text-sm mt-2 font-bold ${unrealizedProfit >= 0 ? 'text-green-400' : 'text-red-400'}`}>
                                    <span>평가 손익</span>
                                    <span className="font-mono">
                                        {unrealizedProfit >= 0 ? '+' : ''}{unrealizedProfit.toFixed(2)} ({profitRate.toFixed(2)}%)
                                    </span>
                                </div>
                            </div>
                        )}
                    </div>

                    <div className="bg-slate-800 border border-slate-700 rounded-xl p-6">
                        <div className="mb-4">
                            <label className="block text-slate-400 text-sm mb-2 font-bold">Quantity (수량)</label>
                            {/* 📜 수정 3: 입력창이 완전히 비워지거나, 숫자만 입력되도록 처리합니다. */}
                            <input
                                type="number"
                                min="1"
                                value={tradeAmount}
                                onChange={(e) => {
                                    const val = e.target.value;
                                    if (val === '') {
                                        setTradeAmount('');
                                    } else {
                                        const parsed = parseInt(val, 10);
                                        if (!isNaN(parsed) && parsed > 0) setTradeAmount(parsed);
                                    }
                                }}
                                className="w-full bg-slate-900 text-white border border-slate-600 rounded-lg px-4 py-3 outline-none focus:border-sky-500 font-mono text-lg transition"
                            />
                        </div>

                        <div className="flex gap-4">
                            <button className="flex-1 bg-red-500 hover:bg-red-600 text-white font-bold py-3 px-4 rounded-lg transition shadow-lg"
                                onClick={async () => {
                                    const res = await fetch('http://localhost:8080/api/trade/sell', {
                                        method: 'POST', headers: { 'Content-Type': 'application/json' },
                                        body: JSON.stringify({ symbol: 'AAPL', amount: getValidAmount(), price: currentAaplPrice })
                                    });
                                    const data = await res.json();
                                    if (data.status === "SUCCESS") fetchUserData();
                                    else alert(data.message);
                                }}
                            >
                                SELL
                            </button>

                            <button className="flex-1 bg-green-500 hover:bg-green-600 text-white font-bold py-3 px-4 rounded-lg transition shadow-lg"
                                onClick={async () => {
                                    const res = await fetch('http://localhost:8080/api/trade/buy', {
                                        method: 'POST', headers: { 'Content-Type': 'application/json' },
                                        body: JSON.stringify({ symbol: 'AAPL', amount: getValidAmount(), price: currentAaplPrice })
                                    });
                                    const data = await res.json();
                                    if (data.status === "SUCCESS") fetchUserData();
                                    else alert(data.message);
                                }}
                            >
                                BUY
                            </button>
                        </div>
                    </div>

                    <div className="bg-slate-800 border border-slate-700 rounded-xl p-6 flex-1 max-h-[300px] flex flex-col">
                        <h2 className="text-xl font-bold mb-4 border-b border-slate-600 pb-2">History</h2>
                        <div className="overflow-y-auto pr-2 space-y-3">
                            {history.length === 0 ? (
                                <p className="text-slate-500 text-center mt-4">No History</p>
                            ) : (
                                history.map((item) => (
                                    <div key={item.id} className="bg-slate-700 p-3 rounded flex justify-between items-center text-sm">
                                        <div>
                                            <span className={`font-bold mr-2 ${item.tradeType === 'BUY' ? 'text-green-400' : 'text-red-400'}`}>{item.tradeType}</span>
                                            <span className="font-bold">{item.symbol}</span> x {item.amount}
                                        </div>
                                        <div className="font-mono">${item.price.toFixed(2)}</div>
                                    </div>
                                ))
                            )}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}