import React, { useState } from 'react';
import { X, Palette, Check, Sparkles, RotateCcw } from 'lucide-react';
import { THEME_PRESETS, applyTheme, hexToRgb } from '../utils/theme';

interface ThemeColorModalProps {
  isOpen: boolean;
  onClose: () => void;
  currentColor: string;
  onColorChange: (colorHex: string) => void;
}

export const ThemeColorModal: React.FC<ThemeColorModalProps> = ({
  isOpen,
  onClose,
  currentColor,
  onColorChange,
}) => {
  const [hexInput, setHexInput] = useState(currentColor);

  if (!isOpen) return null;

  const handleSelectPreset = (color: string) => {
    setHexInput(color);
    onColorChange(color);
    applyTheme(color);
  };

  const handleCustomInput = (color: string) => {
    setHexInput(color);
    if (/^#[0-9A-Fa-f]{6}$/.test(color)) {
      onColorChange(color);
      applyTheme(color);
    }
  };

  const handleResetDefault = () => {
    const defaultColor = '#f59e0b';
    setHexInput(defaultColor);
    onColorChange(defaultColor);
    applyTheme(defaultColor);
  };

  const rgb = hexToRgb(currentColor) || { r: 245, g: 158, b: 11 };

  return (
    <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4 overflow-y-auto">
      <div className="bg-neutral-900 border border-neutral-800 rounded-2xl w-full max-w-lg overflow-hidden shadow-2xl animate-in fade-in zoom-in-95 duration-150 my-6">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-neutral-800 bg-neutral-950/60">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-pink-500/10 border border-pink-500/20 flex items-center justify-center text-pink-400">
              <Palette className="w-4 h-4" />
            </div>
            <div>
              <h2 className="font-semibold text-lg text-white">Studio Theme & Color Customizer</h2>
              <p className="text-xs text-neutral-400">Customize the UI theme colors and glowing audio deck accents</p>
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
        <div className="p-6 space-y-6">
          {/* Curated Theme Presets */}
          <div>
            <div className="flex items-center justify-between mb-3">
              <label className="text-xs font-semibold text-neutral-200">
                Curated Color Deck Presets
              </label>
              <button
                type="button"
                onClick={handleResetDefault}
                className="text-[11px] text-neutral-400 hover:text-white flex items-center gap-1"
              >
                <RotateCcw className="w-3 h-3" /> Reset default
              </button>
            </div>

            <div className="grid grid-cols-2 sm:grid-cols-5 gap-2.5">
              {THEME_PRESETS.map((preset) => {
                const isSelected = currentColor.toLowerCase() === preset.primaryColor.toLowerCase();
                return (
                  <button
                    key={preset.id}
                    type="button"
                    onClick={() => handleSelectPreset(preset.primaryColor)}
                    className={`flex flex-col items-center gap-2 p-2.5 rounded-xl border text-center transition-all ${
                      isSelected
                        ? 'bg-neutral-800 border-white/40 ring-2 ring-[var(--accent)] shadow-md'
                        : 'bg-neutral-950/70 border-white/5 hover:bg-neutral-800/60 hover:border-white/10'
                    }`}
                  >
                    <div
                      className="w-8 h-8 rounded-full shadow-inner flex items-center justify-center border border-white/20"
                      style={{ backgroundColor: preset.primaryColor }}
                    >
                      {isSelected && <Check className="w-4 h-4 text-neutral-950 stroke-[3]" />}
                    </div>
                    <span className="text-[11px] font-medium text-neutral-300 truncate w-full">
                      {preset.name}
                    </span>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Extensive Custom Color Picker */}
          <div className="p-4 rounded-xl bg-neutral-950 border border-white/5 space-y-4">
            <div className="flex items-center justify-between">
              <label className="text-xs font-semibold text-neutral-200 flex items-center gap-1.5">
                <Sparkles className="w-3.5 h-3.5 text-[var(--accent)]" />
                <span>Custom Color Picker & Fine-Tuner</span>
              </label>
              <span className="text-[11px] font-mono text-neutral-400">
                RGB({rgb.r}, {rgb.g}, {rgb.b})
              </span>
            </div>

            <div className="flex items-center gap-3">
              {/* Native Color Input */}
              <div className="relative w-12 h-12 rounded-xl overflow-hidden border border-white/10 shrink-0 cursor-pointer shadow-md">
                <input
                  type="color"
                  value={currentColor}
                  onChange={(e) => handleCustomInput(e.target.value)}
                  className="absolute -inset-4 w-20 h-20 cursor-pointer"
                />
              </div>

              {/* Hex Text input */}
              <div className="flex-1">
                <label className="block text-[11px] text-neutral-400 mb-1">
                  Hex Color Code
                </label>
                <div className="relative">
                  <input
                    type="text"
                    maxLength={7}
                    value={hexInput}
                    onChange={(e) => handleCustomInput(e.target.value)}
                    placeholder="#f59e0b"
                    className="w-full px-3.5 py-2 rounded-lg bg-neutral-900 border border-neutral-700 text-white font-mono text-xs focus:outline-none focus:border-[var(--accent)] uppercase"
                  />
                  <div
                    className="absolute right-2.5 top-1/2 -translate-y-1/2 w-4 h-4 rounded-full border border-white/20"
                    style={{ backgroundColor: currentColor }}
                  />
                </div>
              </div>
            </div>

            {/* Live Deck Preview Card */}
            <div className="pt-2">
              <label className="block text-[11px] text-neutral-400 mb-2">
                Live UI Deck Preview
              </label>
              <div className="p-3.5 rounded-lg bg-neutral-900 border border-white/10 flex items-center justify-between gap-3">
                <div className="flex items-center gap-3">
                  <div
                    className="w-10 h-10 rounded-lg flex items-center justify-center font-bold text-neutral-950 text-xs shadow-md"
                    style={{
                      backgroundColor: currentColor,
                      boxShadow: `0 0 16px ${currentColor}55`,
                    }}
                  >
                    EQ
                  </div>
                  <div>
                    <p className="text-xs font-semibold text-white">Live Broadcast Channel</p>
                    <div className="flex items-center gap-2 text-[11px] text-neutral-400 font-mono-numbers">
                      <span
                        className="px-1.5 py-0.2 rounded text-[10px] font-bold"
                        style={{
                          backgroundColor: `${currentColor}25`,
                          color: currentColor,
                        }}
                      >
                        ACTIVE
                      </span>
                      <span>320 kbps</span>
                    </div>
                  </div>
                </div>

                {/* Animated EQ sample in theme color */}
                <div className="flex items-end gap-1 px-2.5 py-1.5 rounded bg-black/60">
                  <span
                    className="w-1 h-4 rounded-full animate-eq-1"
                    style={{ backgroundColor: currentColor }}
                  />
                  <span
                    className="w-1 h-6 rounded-full animate-eq-2"
                    style={{ backgroundColor: currentColor }}
                  />
                  <span
                    className="w-1 h-3 rounded-full animate-eq-3"
                    style={{ backgroundColor: currentColor }}
                  />
                  <span
                    className="w-1 h-5 rounded-full animate-eq-4"
                    style={{ backgroundColor: currentColor }}
                  />
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end px-6 py-3 border-t border-neutral-800 bg-neutral-950/40">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 text-xs font-semibold text-neutral-950 bg-[var(--accent)] rounded-lg shadow-sm hover:brightness-110 transition-all shadow-[var(--accent-glow)]"
          >
            Apply & Close
          </button>
        </div>
      </div>
    </div>
  );
};
