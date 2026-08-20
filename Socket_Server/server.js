const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const cors = require('cors');

const app = express();
app.use(cors());

const server = http.createServer(app);
const io = new Server(server, {
    cors: { origin: "*", methods: ["GET", "POST"] }
});

// 초기 가짜 주식 데이터
let stocks = [
    { symbol: 'AAPL', price: 150.00, name: 'Apple Inc.' },
    { symbol: 'TSLA', price: 250.00, name: 'Tesla Inc.' },
    { symbol: '^KS11', price: 2600.00, name: 'KOSPI Index' },
];

io.on('connection', (socket) => {
    console.log(`[T.A.R.D.I.S] 클라이언트 연결됨: ${socket.id}`);

    // 1초마다 가격을 랜덤하게 위아래로 흔들어서 전송합니다.
    const interval = setInterval(() => {
        stocks = stocks.map(stock => {
            const change = (Math.random() - 0.5) * 0.02; // -1% ~ 1% 변동률
            const newPrice = stock.price * (1 + change);
            return { ...stock, price: parseFloat(newPrice.toFixed(2)) };
        });
        socket.emit('stock_update', stocks);
    }, 1000);

    socket.on('disconnect', () => {
        console.log(`클라이언트 연결 종료: ${socket.id}`);
        clearInterval(interval);
    });
});

const PORT = 3000;
server.listen(PORT, () => console.log(`🚀 Socket Server running on port ${PORT}`));