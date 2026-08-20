import { useEffect, useState } from 'react';
import { io } from 'socket.io-client';
import { motion } from 'framer-motion';

const socket = io('http://localhost:3000');
interface Stock { symbol: string; name: string; price: number; }

export default function App() {
  const [stocks, setStocks] = useState<Stock[]>([]);
  const [prevPrices, setPrevPrices] = useState<Record<string, number>>({});

  // 💰 내 진짜 잔고를 관리할 상태 변수 (처음엔 0으로 시작)
  const [balance, setBalance] = useState<number>(0);

  // 화면이 처음 켜질 때, 백엔드에서 내 잔고(10,000달러)를 가져옵니다.
  useEffect(() => {
    fetch('http://localhost:8080/api/trade/balance')
      .then(res => res.json())
      .then(data => setBalance(data))
      .catch(err => console.error("잔고 조회 실패:", err));
  }, []);

  useEffect(() => {
    socket.on('stock_update', (data: Stock[]) => {
      setStocks((prev) => {
        const newPrevPrices: Record<string, number> = {};
        prev.forEach(s => newPrevPrices[s.symbol] = s.price);
        setPrevPrices(newPrevPrices);
        return data;
      });
    });
    return () => { socket.off('stock_update'); };
  }, []);

  // 현재 AAPL(애플)의 실시간 가격을 찾아서 변수에 담아둡니다. (없으면 0원)
  const aaplStock = stocks.find(s => s.symbol === 'AAPL');
  const currentAaplPrice = aaplStock ? aaplStock.price : 0;

  return (
    <div className="min-h-screen bg-slate-900 text-white p-8 font-sans flex flex-col">
      <header className="mb-10 text-center">
        <h1 className="text-4xl font-extrabold text-sky-400">T.A.R.D.I.S.</h1>
        <p className="text-slate-400">Time And Relative Dimension In Stocks</p>
      </header>

      <div className="max-w-6xl mx-auto w-full grid grid-cols-1 lg:grid-cols-3 gap-8">

        {/* 좌측: 실시간 호가창 */}
        <div className="col-span-2 grid grid-cols-1 md:grid-cols-2 gap-4 h-min">
          {stocks.map((stock) => {
            const prev = prevPrices[stock.symbol] || stock.price;
            const isUp = stock.price > prev;
            const isDown = stock.price < prev;
            const color = isUp ? 'text-green-400' : isDown ? 'text-red-400' : 'text-slate-200';
            const bg = isUp ? 'rgba(74,222,128,0.2)' : isDown ? 'rgba(248,113,113,0.2)' : 'rgba(30,41,59,1)';

            return (
              <motion.div key={stock.symbol}
                className="p-6 rounded-xl bg-slate-800 border border-slate-700 flex flex-col items-center"
                animate={{ backgroundColor: [bg, 'rgba(30,41,59,1)'] }}
                transition={{ duration: 0.5 }}
              >
                <h2 className="text-xl font-bold">{stock.symbol}</h2>
                <div className={`text-3xl font-mono mt-2 ${color}`}>${stock.price.toFixed(2)}</div>
              </motion.div>
            );
          })}
        </div>

        {/* 우측: 트레이딩 패널 */}
        <div className="bg-slate-800 border border-slate-700 rounded-xl p-6 h-min">
          <h2 className="text-xl font-bold mb-4 border-b border-slate-600 pb-2">Trading Panel</h2>
          <div className="mb-6">
            <p className="text-slate-400 text-sm">Available Balance</p>
            {/* 하드코딩된 글자 대신, 진짜 balance 변수를 화면에 보여줍니다. */}
            <p className="text-2xl font-mono text-sky-300">
              ${balance.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
            </p>
          </div>
          <div className="flex gap-4">

            <button
              className="flex-1 bg-red-500 hover:bg-red-600 text-white font-bold py-3 px-4 rounded transition"
              onClick={async () => {
                const res = await fetch('http://localhost:8080/api/trade/sell', {
                  method: 'POST',
                  headers: { 'Content-Type': 'application/json' },
                  // 서버로 '현재 호가창의 애플 주식 가격(price)'을 함께 보냅니다!
                  body: JSON.stringify({ symbol: 'AAPL', amount: 1, price: currentAaplPrice })
                });
                const data = await res.json();

                // 백엔드가 새로 계산해서 돌려준 잔고를 화면에 즉시 업데이트!
                setBalance(data.newBalance);
                alert(data.message);
              }}
            >
              SELL (매도)
            </button>

            <button
              className="flex-1 bg-green-500 hover:bg-green-600 text-white font-bold py-3 px-4 rounded transition"
              onClick={async () => {
                const res = await fetch('http://localhost:8080/api/trade/buy', {
                  method: 'POST',
                  headers: { 'Content-Type': 'application/json' },
                  body: JSON.stringify({ symbol: 'AAPL', amount: 1, price: currentAaplPrice })
                });
                const data = await res.json();

                setBalance(data.newBalance);
                if (data.status === "FAIL") alert(data.message); // 돈이 부족하면 경고창
              }}
            >
              BUY (매수)
            </button>

          </div>
        </div>
      </div>
    </div>
  );
}