import React from 'react';
import { createRoot } from 'react-dom/client';
import { ESuiteMobileWorkbench } from './components/generated/ESuiteMobileWorkbench';
createRoot(document.getElementById('root')!).render(<React.StrictMode><ESuiteMobileWorkbench /></React.StrictMode>);
