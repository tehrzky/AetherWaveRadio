import React, { useState } from 'react';
import { X, Moon, Clock, Play, PowerOff, Check } from 'lucide-react';
import { SleepTimerConfig } from '../types';

interface SleepTimerModalProps {
  isOpen: boolean;
  onClose: () => void;
  timerConfig: SleepTimerConfig;
  onStartTimer: (minutes: number, fadeOut: boolean) => void;
  onCancelTimer: () => void;
}

export const SleepTimerModal: React.FC<SleepTimerModalProps> = ({
  isOpen,
  onClose,
  timerConfig,
  onStartTimer,
  onCancelTimer,
}) => {
  const [selectedMinutes, setSelectedMinutes] = useState(30);
  const [customInput, setCustomInput] = useState('');
  const [isCustom, setIsCustom] = useState(false);
  const [fadeOut, setFadeOut] = useState(timerConfig.fadeOut ?? true);

  if (!isOpen) return null;

  const presets = [15, 30, 45, 60, 90];

  const handleStart = () => {
    const minutes = isCustom && customInput ? parseInt(customInput, 10) : selectedMinutes;
    if (minutes && minutes > 0) {
      onStartTimer(minutes, fadeOut);
      onClose();
    }
  };

  const formatRemaining = (seconds: number) => {
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return `${m}:${s < 10 ? '0' : ''}${s}`;
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4 overflow-y-auto">
      <div className="bg-neutral-900 border border-neutral-800 rounded-2xl w-full max-w-md overflow-hidden shadow-2xl animate-in fade-in zoom-in-95 duration-150 my-6">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-neutral-800 bg-neutral-950/60">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center text-indigo-400">
              <Moon className="w-4 h-4" />
            </div>
            <div>
              <h2 className="font-semibold text-lg text-white">Sleep Timer</h2>
              <p className="text-xs text-neutral-400">Automatically stop audio playback when you fall asleep</p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1.5 text-neutral-400 hover:text-white rounded-lg hover:bg-neutral-800 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content */}
        <div className="p-6 space-y-5">
          {/* Active Timer Status */}
          {timerConfig.active ? (
            <div className="p-4 rounded-xl bg-neutral-950 border border-[var(--accent)]/30 text-center space-y-3">
              <div className="flex items-center justify-center gap-2 text-xs font-semibold text-[var(--accent)]">
                <Clock className="w-4 h-4 animate-pulse" />
                <span>Timer Running</span>
              </div>

              <div className="text-4xl font-mono font-bold text-white tracking-wider font-mono-numbers">
                {formatRemaining(timerConfig.remainingSeconds)}
              </div>

              {/* Progress bar */}
              <div className="w-full bg-neutral-800 h-1.5 rounded-full overflow-hidden">
                <div
                  className="bg-[var(--accent)] h-full transition-all duration-1000"
                  style={{
                    width: `${Math.max(
                      0,
                      Math.min(
                        100,
                        (timerConfig.remainingSeconds / timerConfig.initialSeconds) * 100
                      )
                    )}%`,
                  }}
                />
              </div>

              {timerConfig.fadeOut && timerConfig.remainingSeconds <= 60 && (
                <p className="text-[11px] text-amber-400">Fading volume out smoothly...</p>
              )}

              <button
                type="button"
                onClick={() => {
                  onCancelTimer();
                }}
                className="w-full flex items-center justify-center gap-2 px-4 py-2 rounded-lg bg-red-500/10 hover:bg-red-500/20 text-red-400 text-xs font-medium border border-red-500/20 transition-colors"
              >
                <PowerOff className="w-3.5 h-3.5" />
                <span>Cancel Sleep Timer</span>
              </button>
            </div>
          ) : (
            <>
              {/* Presets */}
              <div>
                <label className="block text-xs font-medium text-neutral-300 mb-2">
                  Select Duration
                </label>
                <div className="grid grid-cols-5 gap-2">
                  {presets.map((mins) => {
                    const isSel = !isCustom && selectedMinutes === mins;
                    return (
                      <button
                        key={mins}
                        type="button"
                        onClick={() => {
                          setIsCustom(false);
                          setSelectedMinutes(mins);
                        }}
                        className={`py-2 rounded-lg text-xs font-semibold transition-all border ${
                          isSel
                            ? 'bg-[var(--accent)] text-neutral-950 border-[var(--accent)] shadow-md shadow-[var(--accent-glow)]'
                            : 'bg-neutral-950 text-neutral-300 border-white/5 hover:bg-neutral-800 hover:text-white'
                        }`}
                      >
                        {mins}m
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* Custom Minutes */}
              <div>
                <label className="block text-xs font-medium text-neutral-300 mb-1.5">
                  Or Custom Minutes
                </label>
                <div className="flex gap-2">
                  <input
                    type="number"
                    min="1"
                    max="600"
                    value={customInput}
                    onChange={(e) => {
                      setCustomInput(e.target.value);
                      setIsCustom(true);
                    }}
                    placeholder="Enter minutes (e.g. 20, 120)"
                    className={`flex-1 px-3 py-2 rounded-lg bg-neutral-950 border text-xs text-white focus:outline-none ${
                      isCustom && customInput
                        ? 'border-[var(--accent)]'
                        : 'border-neutral-700'
                    }`}
                  />
                  {isCustom && customInput && (
                    <button
                      type="button"
                      onClick={() => {
                        setIsCustom(false);
                        setCustomInput('');
                      }}
                      className="px-3 py-2 rounded-lg bg-neutral-800 text-neutral-400 text-xs"
                    >
                      Clear
                    </button>
                  )}
                </div>
              </div>

              {/* Smooth Fade Out Toggle */}
              <label className="flex items-center justify-between p-3 rounded-xl bg-neutral-950 border border-white/5 cursor-pointer">
                <div>
                  <p className="text-xs font-medium text-neutral-200">Smooth Volume Fade Out</p>
                  <p className="text-[11px] text-neutral-400">
                    Gradually lowers volume during the final 60 seconds
                  </p>
                </div>
                <input
                  type="checkbox"
                  checked={fadeOut}
                  onChange={(e) => setFadeOut(e.target.checked)}
                  className="w-4 h-4 rounded text-[var(--accent)] bg-neutral-800 border-neutral-700 focus:ring-0"
                />
              </label>

              {/* Start Button */}
              <button
                type="button"
                onClick={handleStart}
                className="w-full flex items-center justify-center gap-2 py-2.5 rounded-lg bg-[var(--accent)] text-neutral-950 font-semibold text-xs shadow-md hover:brightness-110 transition-all shadow-[var(--accent-glow)]"
              >
                <Moon className="w-4 h-4" />
                <span>
                  Start Timer ({isCustom && customInput ? `${customInput} min` : `${selectedMinutes} min`})
                </span>
              </button>
            </>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end px-6 py-3 border-t border-neutral-800 bg-neutral-950/40">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 text-xs font-medium text-neutral-300 hover:text-white rounded-lg hover:bg-neutral-800 transition-colors"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
};
