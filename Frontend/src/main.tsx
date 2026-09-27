import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import {
  installBackendAwareFetch,
  startBackendWakeup,
} from './backendWakeup'

// Install a production-only wrapper for idempotent backend reads before the
// React tree starts issuing API requests. POST/PUT/PATCH/DELETE are never
// replayed automatically.
installBackendAwareFetch()

// Start waking the Render backend as soon as the frontend bundle executes.
// The UI can render immediately while readiness is checked in parallel.
void startBackendWakeup()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
