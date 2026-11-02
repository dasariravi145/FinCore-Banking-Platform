import React from 'react';
import { ShieldAlert } from 'lucide-react';

export const SimulationBanner: React.FC = () => {
  return (
    <div className="bg-amber-500/10 border-b border-amber-500/20 px-4 py-1.5 text-xs text-amber-900 flex items-center justify-center gap-2">
      <ShieldAlert className="w-4 h-4 text-amber-600 flex-shrink-0" />
      <span className="font-semibold tracking-wide">BANKING SIMULATION PLATFORM</span>
      <span className="hidden md:inline text-amber-700">
        — Demonstration & Portfolio Architecture. No real money or credentials. All data is synthetic.
      </span>
    </div>
  );
};

