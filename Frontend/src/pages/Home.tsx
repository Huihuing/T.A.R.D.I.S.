import { useState } from 'react';
import { motion } from 'framer-motion';
import { Rocket, ShieldCheck, TrendingUp, BarChart2 } from 'lucide-react';
import { Link } from 'react-router-dom';

export default function Home() {
    // 💡 게스트 체험용 차트 상태
    const [demoSymbol, setDemoSymbol] = useState('NVDA');

    return (
        <div className="min-h-screen bg-[#0b1120] text-slate-200 overflow-y-auto">
            {/* 🚀 Hero Section */}
            <div className="relative pt-20 pb-16 md:pt-32 md:pb-24 px-6 text-center">
                <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] h-[600px] bg-sky-500/10 rounded-full blur-[100px] pointer-events-none"></div>
                <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} className="relative z-10">
                    <h1 className="text-5xl md:text-7xl font-black text-white mb-6 tracking-tight">
                        투자의 새로운 시공간<br/>
                        <span className="text-transparent bg-clip-text bg-gradient-to-r from-sky-400 to-indigo-400">T.A.R.D.I.S.</span>
                    </h1>
                    <p className="text-lg md:text-xl text-slate-400 mb-10 max-w-2xl mx-auto">
                        글로벌 증시 데이터 확인부터 모의 투자까지.<br/>
                        지금 바로 나만의 포트폴리오를 구성하고 실시간 시장을 경험하세요.
                    </p>
                    <div className="flex flex-col sm:flex-row justify-center gap-4">
                        <Link to="/register" className="px-8 py-4 bg-sky-600 hover:bg-sky-500 text-white font-bold rounded-xl shadow-lg shadow-sky-900/20 transition-all">
                            1만 달러 모의투자 시작하기
                        </Link>
                        <Link to="/dashboard" className="px-8 py-4 bg-slate-800 hover:bg-slate-700 border border-slate-700 text-white font-bold rounded-xl transition-all">
                            대시보드 둘러보기
                        </Link>
                    </div>
                </motion.div>
            </div>

            {/* 🚀 3 Key Features */}
            <div className="max-w-6xl mx-auto px-6 py-16 grid grid-cols-1 md:grid-cols-3 gap-8">
                <motion.div initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} className="bg-slate-800/50 p-8 rounded-3xl border border-slate-700/50 shadow-xl">
                    <div className="w-14 h-14 bg-sky-500/20 rounded-2xl flex items-center justify-center text-sky-400 mb-6"><BarChart2 className="w-7 h-7" /></div>
                    <h3 className="text-xl font-bold text-white mb-3">글로벌 실시간 시세</h3>
                    <p className="text-slate-400 text-sm leading-relaxed">Finnhub 및 TradingView API를 통한 전 세계 수만 개의 주식 실시간 차트와 펀더멘털 데이터를 제공합니다.</p>
                </motion.div>
                <motion.div initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} transition={{ delay: 0.1 }} className="bg-slate-800/50 p-8 rounded-3xl border border-slate-700/50 shadow-xl">
                    <div className="w-14 h-14 bg-indigo-500/20 rounded-2xl flex items-center justify-center text-indigo-400 mb-6"><Rocket className="w-7 h-7" /></div>
                    <h3 className="text-xl font-bold text-white mb-3">리스크 없는 모의투자</h3>
                    <p className="text-slate-400 text-sm leading-relaxed">가입 즉시 지급되는 1만 달러로 안전하게 투자 전략을 테스트하고 포트폴리오 수익률을 트래킹하세요.</p>
                </motion.div>
                <motion.div initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }} transition={{ delay: 0.2 }} className="bg-slate-800/50 p-8 rounded-3xl border border-slate-700/50 shadow-xl">
                    <div className="w-14 h-14 bg-emerald-500/20 rounded-2xl flex items-center justify-center text-emerald-400 mb-6"><ShieldCheck className="w-7 h-7" /></div>
                    <h3 className="text-xl font-bold text-white mb-3">안전한 자산 관리</h3>
                    <p className="text-slate-400 text-sm leading-relaxed">JWT 기반의 인증과 2차 계좌 비밀번호를 통해 철저하게 사용자의 지갑과 자산을 보호합니다.</p>
                </motion.div>
            </div>

            {/* 🚀 Interactive Demo Section */}
            <div className="max-w-5xl mx-auto px-6 py-20">
                <div className="text-center mb-10">
                    <h2 className="text-3xl font-black text-white mb-4">실제 동작하는 차트를 체험해보세요</h2>
                    <p className="text-slate-400">버튼을 클릭하여 글로벌 주요 기업의 실시간 흐름을 확인하세요.</p>
                </div>
                <div className="bg-slate-800/80 backdrop-blur-xl p-4 md:p-8 rounded-3xl border border-slate-700 shadow-2xl">
                    <div className="flex flex-wrap justify-center gap-3 mb-6">
                        {['AAPL', 'NVDA', 'TSLA', 'MSFT'].map(sym => (
                            <button key={sym} onClick={() => setDemoSymbol(sym)} className={`px-6 py-2 rounded-xl font-bold transition-all ${demoSymbol === sym ? 'bg-sky-500 text-white shadow-lg' : 'bg-slate-900 border border-slate-700 text-slate-400 hover:text-white hover:border-sky-500'}`}>
                                {sym}
                            </button>
                        ))}
                    </div>
                    <div className="w-full h-[450px] bg-slate-900 rounded-2xl overflow-hidden border border-slate-800">
                        {/* 💡 key 속성을 주어 완전히 새로 렌더링되게 만듦 */}
                        <iframe key={demoSymbol} src={`https://s.tradingview.com/widgetembed/?symbol=${demoSymbol}&interval=D&theme=dark&style=1&hide_top_toolbar=1&hide_side_toolbar=1&withdateranges=1&saveimage=0&locale=kr`} className="w-full h-full border-0" allowTransparency={true} />
                    </div>
                </div>
            </div>
        </div>
    );
}