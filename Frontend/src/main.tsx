import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { mountPulseNavigation } from './pulse-platform/pulse-navigation'
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

// Brand/navigation only. Native login, wallet, PIN and DB access are unchanged.
const disposePulseNavigation = mountPulseNavigation({
  world: 'tardis',
  portalUrl: import.meta.env.VITE_PULSE_LAB_URL,
  allowLocal: import.meta.env.DEV,
})
if (import.meta.hot) import.meta.hot.dispose(disposePulseNavigation)

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
