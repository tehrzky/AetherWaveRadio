import React, { useState, useEffect } from 'react';
import { X, Bluetooth, Headphones, Speaker, RefreshCw, Check, AlertCircle, Radio, Volume2 } from 'lucide-react';
import { audioEngine } from '../utils/audioEngine';
import { AudioDevice } from '../types';

interface BluetoothModalProps {
  isOpen: boolean;
  onClose: () => void;
  activeDeviceId?: string;
  onSelectDevice: (deviceId: string) => void;
}

export const BluetoothModal: React.FC<BluetoothModalProps> = ({
  isOpen,
  onClose,
  activeDeviceId,
  onSelectDevice,
}) => {
  const [devices, setDevices] = useState<AudioDevice[]>([]);
  const [bluetoothDeviceName, setBluetoothDeviceName] = useState<string | null>(null);
  const [isScanning, setIsScanning] = useState(false);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [hasBluetoothSupport, setHasBluetoothSupport] = useState(false);
  const [hasSinkIdSupport, setHasSinkIdSupport] = useState(false);

  useEffect(() => {
    if (!isOpen) return;

    setHasBluetoothSupport('bluetooth' in navigator);
    const audio = audioEngine.getAudioElement();
    setHasSinkIdSupport('setSinkId' in audio && typeof (audio as any).setSinkId === 'function');

    loadAudioDevices();
  }, [isOpen]);

  const loadAudioDevices = async () => {
    try {
      if (!navigator.mediaDevices || !navigator.mediaDevices.enumerateDevices) {
        return;
      }
      const allDevices = await navigator.mediaDevices.enumerateDevices();
      const audioOutputs = allDevices
        .filter((d) => d.kind === 'audiooutput')
        .map((d, index) => ({
          deviceId: d.deviceId,
          label: d.label || `Audio Output Device ${index + 1}`,
        }));

      setDevices(audioOutputs);
    } catch (e) {
      console.warn('Error enumerating audio devices:', e);
    }
  };

  const handlePairBluetoothDevice = async () => {
    if (!('bluetooth' in navigator)) {
      setStatusMessage('Web Bluetooth API is not available on this browser. Use your system Bluetooth settings.');
      return;
    }

    setIsScanning(true);
    setStatusMessage(null);

    try {
      // Prompt user with native Bluetooth pairing sheet
      const device = await (navigator as any).bluetooth.requestDevice({
        acceptAllDevices: true,
        optionalServices: ['generic_access', 'battery_service'],
      });

      setBluetoothDeviceName(device.name || 'Bluetooth Audio Controller');
      setStatusMessage(`Connected to ${device.name || 'Bluetooth Controller'}`);

      // Refresh audio devices list
      setTimeout(loadAudioDevices, 1000);
    } catch (err: any) {
      if (err.name !== 'NotFoundError') {
        setStatusMessage(err.message || 'Bluetooth connection was cancelled.');
      }
    } finally {
      setIsScanning(false);
    }
  };

  const handleSelectOutput = async (deviceId: string) => {
    onSelectDevice(deviceId);
    const success = await audioEngine.setAudioOutputDevice(deviceId);
    if (success) {
      setStatusMessage('Audio stream routed successfully to selected device.');
    } else {
      setStatusMessage('Device selected (system default routing applied).');
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4 overflow-y-auto">
      <div className="bg-neutral-900 border border-neutral-800 rounded-2xl w-full max-w-lg overflow-hidden shadow-2xl animate-in fade-in zoom-in-95 duration-150 my-6">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-neutral-800 bg-neutral-950/60">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-blue-500/10 border border-blue-500/20 flex items-center justify-center text-blue-400">
              <Bluetooth className="w-4 h-4" />
            </div>
            <div>
              <h2 className="font-semibold text-lg text-white">Bluetooth & External Audio</h2>
              <p className="text-xs text-neutral-400">Route stream to wireless speakers, earbuds & car stereos</p>
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

        {/* Body */}
        <div className="p-6 space-y-5">
          {/* Bluetooth Hardware pairing button */}
          <div className="p-4 rounded-xl bg-neutral-950 border border-white/5 space-y-3">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-neutral-200">
                Wireless Bluetooth Pairing
              </span>
              {bluetoothDeviceName && (
                <span className="text-[11px] text-emerald-400 font-medium flex items-center gap-1">
                  <Check className="w-3.5 h-3.5" /> Paired: {bluetoothDeviceName}
                </span>
              )}
            </div>

            <p className="text-xs text-neutral-400 leading-relaxed">
              Pair your Bluetooth headphones, external boomboxes, or wireless media controllers with native Web Bluetooth.
            </p>

            <button
              type="button"
              onClick={handlePairBluetoothDevice}
              disabled={isScanning}
              className="w-full flex items-center justify-center gap-2 px-4 py-2.5 rounded-lg bg-neutral-800 hover:bg-neutral-700 text-white font-medium text-xs transition-colors border border-white/10 disabled:opacity-50"
            >
              {isScanning ? (
                <>
                  <RefreshCw className="w-4 h-4 animate-spin text-blue-400" />
                  <span>Searching for Bluetooth devices...</span>
                </>
              ) : (
                <>
                  <Bluetooth className="w-4 h-4 text-blue-400" />
                  <span>Scan & Connect Bluetooth Device</span>
                </>
              )}
            </button>
          </div>

          {/* Audio Output device selector */}
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <label className="text-xs font-semibold text-neutral-200">
                Audio Output Destination
              </label>
              <button
                type="button"
                onClick={loadAudioDevices}
                className="text-[11px] text-[var(--accent)] hover:underline flex items-center gap-1"
              >
                <RefreshCw className="w-3 h-3" /> Refresh list
              </button>
            </div>

            <div className="space-y-1.5 max-h-48 overflow-y-auto">
              <button
                type="button"
                onClick={() => handleSelectOutput('')}
                className={`w-full flex items-center justify-between p-3 rounded-lg border text-left transition-colors ${
                  !activeDeviceId
                    ? 'bg-neutral-800/80 border-[var(--accent)] text-white'
                    : 'bg-neutral-950/60 border-white/5 hover:bg-neutral-900 text-neutral-300'
                }`}
              >
                <div className="flex items-center gap-2.5 min-w-0">
                  <Speaker className="w-4 h-4 text-neutral-400 shrink-0" />
                  <div>
                    <p className="text-xs font-medium">Default Audio Output</p>
                    <p className="text-[11px] text-neutral-500">System default speakers or connected headphones</p>
                  </div>
                </div>
                {!activeDeviceId && <Check className="w-4 h-4 text-[var(--accent)] shrink-0" />}
              </button>

              {devices.map((device) => {
                const isSelected = activeDeviceId === device.deviceId;
                const isHeadphones = device.label.toLowerCase().includes('headphone') || device.label.toLowerCase().includes('earbuds') || device.label.toLowerCase().includes('bluetooth') || device.label.toLowerCase().includes('airpods');

                return (
                  <button
                    key={device.deviceId}
                    type="button"
                    onClick={() => handleSelectOutput(device.deviceId)}
                    className={`w-full flex items-center justify-between p-3 rounded-lg border text-left transition-colors ${
                      isSelected
                        ? 'bg-neutral-800/80 border-[var(--accent)] text-white'
                        : 'bg-neutral-950/60 border-white/5 hover:bg-neutral-900 text-neutral-300'
                    }`}
                  >
                    <div className="flex items-center gap-2.5 min-w-0">
                      {isHeadphones ? (
                        <Headphones className="w-4 h-4 text-blue-400 shrink-0" />
                      ) : (
                        <Speaker className="w-4 h-4 text-neutral-400 shrink-0" />
                      )}
                      <div className="truncate">
                        <p className="text-xs font-medium truncate">{device.label}</p>
                        <p className="text-[11px] text-neutral-500 font-mono text-[10px]">
                          ID: {device.deviceId.slice(0, 16)}...
                        </p>
                      </div>
                    </div>
                    {isSelected && <Check className="w-4 h-4 text-[var(--accent)] shrink-0" />}
                  </button>
                );
              })}
            </div>
          </div>

          {/* MediaSession Remote Controls Note */}
          <div className="p-3.5 rounded-xl bg-neutral-950/70 border border-white/5 space-y-1.5">
            <div className="flex items-center gap-2 text-xs font-medium text-neutral-200">
              <Headphones className="w-4 h-4 text-[var(--accent)]" />
              <span>Bluetooth Headset Media Keys Active</span>
            </div>
            <p className="text-xs text-neutral-400 leading-relaxed">
              External Play, Pause, Next Track, and Previous Track buttons on Bluetooth headphones or steering wheels are linked to station switching and lockscreen notifications.
            </p>
          </div>

          {statusMessage && (
            <div className="flex items-center gap-2 text-xs text-neutral-300 bg-neutral-800/80 p-3 rounded-lg border border-white/10">
              <AlertCircle className="w-4 h-4 text-[var(--accent)] shrink-0" />
              <span>{statusMessage}</span>
            </div>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end px-6 py-3 border-t border-neutral-800 bg-neutral-950/40">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 text-xs font-semibold text-neutral-950 bg-[var(--accent)] rounded-lg shadow-sm hover:brightness-110 transition-all shadow-[var(--accent-glow)]"
          >
            Done
          </button>
        </div>
      </div>
    </div>
  );
};
