import { WS_URL } from '../config';
import { useState, useEffect, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { MessageSquare, X, Send } from 'lucide-react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { getStoredToken } from '../auth';

interface ChatMessage {
    username: string;
    text: string;
    time: string;
    clientMessageId?: string;
    mine?: boolean;
}

export default function Trollbox() {
    const [isOpen, setIsOpen] = useState(false);
    const [messages, setMessages] = useState<ChatMessage[]>([]);
    const [input, setInput] = useState('');
    const [isConnected, setIsConnected] = useState(false);
    const messagesEndRef = useRef<HTMLDivElement>(null);
    const clientRef = useRef<Client | null>(null);
    const ownMessageIds = useRef<Set<string>>(new Set());
    const currentUsername = localStorage.getItem('username') || 'Guest';

    useEffect(() => {
        let disposed = false;

        const client = new Client({
            webSocketFactory: () =>
                new SockJS(`${WS_URL}/ws-stomp`) as any,
            reconnectDelay: 5000,
            beforeConnect: () => {
                const token = getStoredToken();
                client.connectHeaders = token
                    ? { Authorization: `Bearer ${token}` }
                    : {};
            },
            debug: () => {},
            onConnect: () => {
                if (disposed) return;
                setIsConnected(true);
                client.subscribe('/topic/chat', frame => {
                    try {
                        const incoming = JSON.parse(
                            frame.body
                        ) as ChatMessage;
                        const mine = Boolean(
                            incoming.clientMessageId
                            && ownMessageIds.current.delete(
                                incoming.clientMessageId
                            )
                        );
                        setMessages(prev => [
                            ...prev,
                            { ...incoming, mine }
                        ].slice(-200));
                    } catch {
                        // 잘못된 프레임은 화면에 반영하지 않습니다.
                    }
                });
            },
            onDisconnect: () => {
                if (!disposed) setIsConnected(false);
            },
            onWebSocketClose: () => {
                if (!disposed) setIsConnected(false);
            },
            onStompError: () => {
                if (!disposed) setIsConnected(false);
            }
        });

        clientRef.current = client;
        client.activate();

        return () => {
            disposed = true;
            setIsConnected(false);
            if (clientRef.current === client) {
                clientRef.current = null;
            }
            void client.deactivate();
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

        const text = input.trim();
        const client = clientRef.current;
        if (!text || !client?.connected) return;

        if (ownMessageIds.current.size >= 200) {
            ownMessageIds.current.clear();
        }

        const clientMessageId =
            typeof crypto !== 'undefined' && crypto.randomUUID
                ? crypto.randomUUID()
                : `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
        ownMessageIds.current.add(clientMessageId);

        client.publish({
            destination: '/app/chat',
            body: JSON.stringify({
                text,
                clientMessageId
            })
        });
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
                        {/* 🚀 채팅창 헤더 */}
                        <div className="bg-slate-800/80 p-4 border-b border-slate-700 flex justify-between items-center">
                            <div className="flex items-center gap-2">
                                <div className={`w-2 h-2 rounded-full ${isConnected ? 'bg-emerald-500 animate-pulse' : 'bg-amber-500'}`}></div>
                                <h3 className="font-bold text-white">
                                    Trollbox {isConnected ? '(실시간 채팅)' : '(서버 연결 중...)'}
                                </h3>
                            </div>
                            <button onClick={() => setIsOpen(false)} className="text-slate-400 hover:text-white transition-colors">
                                <X className="w-5 h-5" />
                            </button>
                        </div>

                        {/* 🚀 채팅 메시지 영역 */}
                        <div className="flex-1 p-4 overflow-y-auto custom-scrollbar flex flex-col gap-4">
                            {messages.length === 0 ? (
                                <div className="flex-1 flex flex-col items-center justify-center text-slate-500 text-sm">
                                    <MessageSquare className="w-8 h-8 mb-2 opacity-50" />
                                    <p>{isConnected ? '아직 메시지가 없습니다.' : '채팅 서버에 연결하는 중입니다.'}</p>
                                    {isConnected && <p>첫 번째로 인사해 보세요!</p>}
                                </div>
                            ) : (
                                messages.map((msg, idx) => {
                                    const isMe = Boolean(msg.mine)
                                        || (currentUsername !== 'Guest'
                                            && msg.username === currentUsername);
                                    return (
                                        <div key={`${msg.clientMessageId || idx}-${idx}`} className={`flex flex-col ${isMe ? 'items-end' : 'items-start'}`}>
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

                        {/* 🚀 입력창 */}
                        <form onSubmit={sendMessage} className="p-3 bg-slate-800/80 border-t border-slate-700 flex gap-2">
                            <input 
                                type="text" 
                                value={input}
                                maxLength={300}
                                onChange={(e) => setInput(e.target.value)}
                                placeholder={isConnected ? '메시지를 입력하세요...' : '서버 연결을 기다리는 중...'}
                                disabled={!isConnected}
                                className="flex-1 bg-slate-900 border border-slate-700 text-white px-4 py-2.5 rounded-xl outline-none focus:border-sky-500 text-sm transition-colors disabled:opacity-60"
                            />
                            <button type="submit" disabled={!isConnected || !input.trim()} className="bg-sky-600 hover:bg-sky-500 text-white p-2.5 rounded-xl transition-colors disabled:opacity-50 flex items-center justify-center">
                                <Send className="w-4 h-4" />
                            </button>
                        </form>
                    </motion.div>
                )}
            </AnimatePresence>

            {/* 🚀 채팅 토글 플로팅 버튼 */}
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
