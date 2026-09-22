export const API_URL = import.meta.env.PROD
    ? ''
    : (import.meta.env.VITE_API_URL || 'http://localhost:8080');

export const WS_URL = import.meta.env.PROD
    ? (import.meta.env.VITE_WS_URL || 'https://t-a-r-d-i-s.onrender.com')
    : (import.meta.env.VITE_WS_URL || 'http://localhost:8080');
