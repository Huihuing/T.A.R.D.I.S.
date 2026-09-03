import { API_URL, WS_URL } from '../config';
import { useState, useEffect, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { MessageSquare, X, Send } from 'lucide-react';
import { io, Socket } from 'socket.io-client';

interface ChatMessage {
    username: string;
    text: string;
    time: string;
}

let socket: Socket;

export default function Trollbox() {
    const [isOpen, setIsOpen] = useState(false);
    const [messages, setMessages] = useState<ChatMessage[]>([]);
    const [input, setInput] = useState('');
    const messagesEndRef = useRef<HTMLDivElement>(null);

    // 로그인하지 않은 게스트는 랜덤 숫자를 부여해 닉네임 겹침 방지
    const getUsername = () => {
        const stored = localStorage.getItem('username');
        if (!stored || stored === 'Guest') {
            return `Guest_${Math.floor(Math.random() * 10000)}`;
        }
        return stored;
    };
    const [currentUsername] = useState(getUsername());

    useEffect(() => {
        // Node.js 소켓 서버(포트 3000) 연결
        socket = io(`${WS_URL}`);

        socket.on('receiveMessage', (msg: ChatMessage) => {
            setMessages((prev) => [...prev, msg]);
        });

        return () => {
            socket.disconnect();
        };
    }, []);

    // 새 메시지가 올 때마다 스크롤을 맨 아래로 내리기
    useEffect(() => {
        if (isOpen) {
            messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
        }
    }, [messages, isOpen]);

    const sendMessage = (e: React.FormEvent) => {
        e.preventDefault();
        if (!input.trim()) return;

        const msgData: ChatMessage = {
            username: currentUsername,
            text: input.trim(),
            time: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        };

        socket.emit('sendMessage', msgData);
        setInput('');
    };

    return (
        <div className="fixed bottom-8 right-8 z-[100] flex flex-col items-end">
            <AnimatePresence>
                {isOpen && (
                    <motion.div 
                        initial={{ opacity: 0, y: 20, scale: 0.95 }} 
                        animate={{ opacity: 1, y: 0, scale: 1 }} 
                        exit={{ opacity: 0, y: 20, scale: 0.95 }}
                        transition={{ duration: 0.2 }}
                        className="bg-slate-900/95 backdrop-blur-xl border border-slate-700 shadow-2xl rounded-2xl w-[350px] h-[500px] mb-4 flex flex-col overflow-hidden"
                    >
                        {/* 채팅창 헤더 */}
                        <div className="bg-slate-800/80 p-4 border-b border-slate-700 flex justify-between items-center">
                            <div className="flex items-center gap-2">
                                <div className="w-2 h-2 bg-emerald-500 rounded-full animate-pulse"></div>
                                <h3 className="font-bold text-white">Trollbox (실시간 채팅)</h3>
                            </div>
                            <button onClick={() => setIsOpen(false)} className="text-slate-400 hover:text-white transition-colors">
                                <X className="w-5 h-5" />
                            </button>
                        </div>

                        {/* 채팅 메시지 영역 */}
                        <div className="flex-1 p-4 overflow-y-auto custom-scrollbar flex flex-col gap-4">
                            {messages.length === 0 ? (
                                <div className="flex-1 flex flex-col items-center justify-center text-slate-500 text-sm">
                                    <MessageSquare className="w-8 h-8 mb-2 opacity-50" />
                                    <p>아직 메시지가 없습니다.</p>
                                    <p>첫 번째로 인사해 보세요!</p>
                                </div>
                            ) : (
                                messages.map((msg, idx) => {
                                    const isMe = msg.username === currentUsername;
                                    return (
                                        <div key={idx} className={`flex flex-col ${isMe ? 'items-end' : 'items-start'}`}>
                                            <div className="flex items-baseline gap-2 mb-1">
                                                <span className={`text-xs font-bold ${isMe ? 'text-sky-400' : 'text-indigo-400'}`}>{msg.username}</span>
                                                <span className="text-[10px] text-slate-500">{msg.time}</span>
                                            </div>
                                            <div className={`px-4 py-2 rounded-2xl max-w-[85%] text-sm leading-relaxed ${isMe ? 'bg-sky-600 text-white rounded-tr-sm' : 'bg-slate-800 text-slate-200 border border-slate-700 rounded-tl-sm'}`}>
                                                {msg.text}
                                            </div>
                                        </div>
                                    );
                                })
                            )}
                            <div ref={messagesEndRef} />
                        </div>

                        {/* 입력창 */}
                        <form onSubmit={sendMessage} className="p-3 bg-slate-800/80 border-t border-slate-700 flex gap-2">
                            <input 
                                type="text" 
                                value={input}
                                onChange={(e) => setInput(e.target.value)}
                                placeholder="메시지를 입력하세요..." 
                                className="flex-1 bg-slate-900 border border-slate-700 text-white px-4 py-2.5 rounded-xl outline-none focus:border-sky-500 text-sm transition-colors"
                            />
                            <button type="submit" disabled={!input.trim()} className="bg-sky-600 hover:bg-sky-500 text-white p-2.5 rounded-xl transition-colors disabled:opacity-50 flex items-center justify-center">
                                <Send className="w-4 h-4" />
                            </button>
                        </form>
                    </motion.div>
                )}
            </AnimatePresence>

            {/* 채팅 토글 플로팅 버튼 */}
            <motion.button 
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
                onClick={() => setIsOpen(!isOpen)}
                className={`w-14 h-14 rounded-full flex items-center justify-center shadow-2xl transition-colors ${isOpen ? 'bg-slate-700 text-white' : 'bg-sky-600 hover:bg-sky-500 text-white shadow-sky-900/50'}`}
            >
                {isOpen ? <X className="w-6 h-6" /> : <MessageSquare className="w-6 h-6" />}
            </motion.button>
        </div>
    );
}