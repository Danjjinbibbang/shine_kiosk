import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { KioskApp } from './kiosk/KioskApp'
import { StaffApp } from './staff/StaffApp'
import './styles.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <Routes>
        <Route path="/kiosk/*" element={<KioskApp />} />
        <Route path="/staff/*" element={<StaffApp />} />
        <Route path="*" element={<Navigate to="/kiosk" replace />} />
      </Routes>
    </BrowserRouter>
  </StrictMode>,
)
