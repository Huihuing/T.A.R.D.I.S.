require('dotenv').config();

const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const WebSocket = require('ws');

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
    cors: { origin: "http://localhost:5173", methods: ["GET", "POST"] }
});

const FINNHUB_API_KEY = process.env.FINNHUB_API_KEY;

const stockPrices = {
    'AAPL': { symbol: 'AAPL', name: 'Apple Inc.', price: 175.00 },
    'TSLA': { symbol: 'TSLA', name: 'Tesla Inc.', price: 240.00 },
    'MSFT': { symbol: 'MSFT', name: 'Microsoft Corp.', price: 330.00 }
};

const finnhubSocket = new WebSocket(`wss://ws.finnhub.io?token=${FINNHUB_API_KEY}`);

finnhubSocket.on('open', () => {
    console.log('✅ Finnhub WebSocket 연결 성공');
    ['AAPL', 'TSLA', 'MSFT'].forEach(symbol => {
        finnhubSocket.send(JSON.stringify({ 'type': 'subscribe', 'symbol': symbol }));
    });
});

finnhubSocket.on('message', (data) => {
    try {
        const parsed = JSON.parse(data);
        if (parsed.type === 'trade' && parsed.data) {
            parsed.data.forEach(trade => {
                if (stockPrices[trade.s]) {
                    stockPrices[trade.s].price = trade.p; 
                }
            });
        }
    } catch (err) {
        console.error('Finnhub 데이터 파싱 에러:', err);
    }
});

finnhubSocket.on('error', (err) => console.error('Finnhub WebSocket 에러:', err.message));

io.on('connection', (socket) => {
    console.log(`[T.A.R.D.I.S] 프론트엔드 연결됨: ${socket.id}`);
    const interval = setInterval(() => {
        socket.emit('stockData', Object.values(stockPrices));
    }, 1000);

    // 💡 추가된 실시간 채팅(Trollbox) 중계 로직
    socket.on('sendMessage', (msgData) => {
        // 누군가 메시지를 보내면, 접속한 "모든" 클라이언트에게 다시 쏴줍니다.
        io.emit('receiveMessage', msgData);
    });

    socket.on('disconnect', () => {
        console.log(`프론트엔드 연결 종료: ${socket.id}`);
        clearInterval(interval);
    });
});

server.listen(3000, () => console.log('🚀 Socket Server running on port 3000'));