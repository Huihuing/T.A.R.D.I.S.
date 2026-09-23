import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { startBackendWakeup } from './backendWakeup'

// Start waking the Render backend as soon as the frontend bundle executes.
// The UI can render immediately while readiness is checked in parallel.
void startBackendWakeup()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
