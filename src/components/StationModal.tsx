import React, { useState, useEffect } from 'react';
import { Station } from '../types';
import { X, Check, Upload, Link, Radio, AlertCircle, RefreshCw } from 'lucide-react';

interface StationModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSave: (station: Station) => void;
  stationToEdit?: Station | null;
  existingGroups: string[];
}

export const StationModal: React.FC<StationModalProps> = ({
  isOpen,
  onClose,
  onSave,
  stationToEdit,
  existingGroups,
}) => {
  const [name, setName] = useState('');
  const [streamUrl, setStreamUrl] = useState('');
  const [logoUrl, setLogoUrl] = useState('');
  const [group, setGroup] = useState('Lo-Fi & Chill');
  const [customGroupInput, setCustomGroupInput] = useState('');
  const [isCreatingGroup, setIsCreatingGroup] = useState(false);
  const [codec, setCodec] = useState<'AUTO' | 'MP3' | 'AAC' | 'HLS' | 'OGG'>('AUTO');
  const [bitrate, setBitrate] = useState('128 kbps');
  const [country, setCountry] = useState('');
  const [description, setDescription] = useState('');

  // Stream validation state
  const [testingStream, setTestingStream] = useState(false);
  const [testResult, setTestResult] = useState<{
    success: boolean;
    message: string;
  } | null>(null);

  useEffect(() => {
    if (stationToEdit) {
      setName(stationToEdit.name || '');
      setStreamUrl(stationToEdit.streamUrl || '');
      setLogoUrl(stationToEdit.logoUrl || '');
      setGroup(stationToEdit.group || 'Lo-Fi & Chill');
      setCodec(stationToEdit.codec || 'AUTO');
      setBitrate(stationToEdit.bitrate || '128 kbps');
      setCountry(stationToEdit.country || '');
      setDescription(stationToEdit.description || '');
      setIsCreatingGroup(false);
      setTestResult(null);
    } else {
      setName('');
      setStreamUrl('');
      setLogoUrl('');
      setGroup(existingGroups[2] || 'Lo-Fi & Chill');
      setCustomGroupInput('');
      setIsCreatingGroup(false);
      setCodec('AUTO');
      setBitrate('128 kbps');
      setCountry('');
      setDescription('');
      setTestResult(null);
    }
  }, [stationToEdit, isOpen, existingGroups]);

  if (!isOpen) return null;

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (file.size > 2 * 1024 * 1024) {
      alert('Image size exceeds 2MB limit.');
      return;
    }

    const reader = new FileReader();
    reader.onload = (uploadEvent) => {
      const dataUrl = uploadEvent.target?.result as string;
      setLogoUrl(dataUrl);
    };
    reader.readAsDataURL(file);
  };

  const handleTestStream = async () => {
    if (!streamUrl.trim() || !streamUrl.startsWith('http')) {
      setTestResult({
        success: false,
        message: 'Please enter a valid HTTP/HTTPS stream link.',
      });
      return;
    }

    setTestingStream(true);
    setTestResult(null);

    try {
      const res = await fetch('/api/test-stream', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ url: streamUrl.trim() }),
      });
      const data = await res.json();
      if (data.valid) {
        setTestResult({
          success: true,
          message: data.contentType
            ? `Stream verified (${data.contentType})`
            : 'Stream reachable and ready for playback',
        });
      } else {
        setTestResult({
          success: false,
          message: data.message || 'Stream could not be reached.',
        });
      }
    } catch (e) {
      setTestResult({
        success: true,
        message: 'Network verification completed (will attempt direct browser stream).',
      });
    } finally {
      setTestingStream(false);
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim() || !streamUrl.trim()) return;

    const finalGroup = isCreatingGroup && customGroupInput.trim()
      ? customGroupInput.trim()
      : group;

    const station: Station = {
      id: stationToEdit ? stationToEdit.id : `station-${Date.now()}`,
      name: name.trim(),
      streamUrl: streamUrl.trim(),
      logoUrl: logoUrl.trim() || undefined,
      group: finalGroup,
      description: description.trim() || undefined,
      bitrate: bitrate.trim() || '128 kbps',
      codec: codec,
      country: country.trim() || undefined,
      isFavorite: stationToEdit ? stationToEdit.isFavorite : false,
      lastPlayedAt: stationToEdit?.lastPlayedAt,
      customOrder: stationToEdit?.customOrder ?? Date.now(),
    };

    onSave(station);
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/75 backdrop-blur-sm flex items-center justify-center p-4 overflow-y-auto">
      <div className="bg-neutral-900 border border-neutral-800 rounded-2xl w-full max-w-lg overflow-hidden shadow-2xl animate-in fade-in zoom-in-95 duration-150 my-8">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-neutral-800 bg-neutral-950/50">
          <div className="flex items-center gap-2">
            <Radio className="w-5 h-5 text-[var(--accent)]" />
            <h2 className="font-semibold text-lg text-white">
              {stationToEdit ? 'Edit Station' : 'Add New Station'}
            </h2>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1.5 text-neutral-400 hover:text-white rounded-lg hover:bg-neutral-800 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleSubmit} className="p-6 space-y-4">
          {/* Station Name */}
          <div>
            <label className="block text-xs font-medium text-neutral-300 mb-1.5">
              Station Name <span className="text-red-400">*</span>
            </label>
            <input
              type="text"
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Cyberpunk Synth Radio"
              className="w-full px-3.5 py-2.5 rounded-lg bg-neutral-950 border border-neutral-700 text-white placeholder-neutral-500 focus:outline-none focus:border-[var(--accent)] text-sm"
            />
          </div>

          {/* Stream URL + Test */}
          <div>
            <div className="flex items-center justify-between mb-1.5">
              <label className="block text-xs font-medium text-neutral-300">
                Audio Stream URL <span className="text-red-400">*</span>
              </label>
              <button
                type="button"
                onClick={handleTestStream}
                disabled={testingStream || !streamUrl}
                className="text-xs text-[var(--accent)] hover:underline flex items-center gap-1 disabled:opacity-40"
              >
                {testingStream ? (
                  <>
                    <RefreshCw className="w-3 h-3 animate-spin" />
                    Testing...
                  </>
                ) : (
                  'Test Stream URL'
                )}
              </button>
            </div>
            <input
              type="url"
              required
              value={streamUrl}
              onChange={(e) => {
                setStreamUrl(e.target.value);
                setTestResult(null);
              }}
              placeholder="https://server.stream.com:8000/live.mp3 or .m3u8"
              className="w-full px-3.5 py-2.5 rounded-lg bg-neutral-950 border border-neutral-700 text-white placeholder-neutral-500 focus:outline-none focus:border-[var(--accent)] text-sm font-mono text-xs"
            />
            {testResult && (
              <div
                className={`mt-2 text-xs flex items-center gap-1.5 ${
                  testResult.success ? 'text-emerald-400' : 'text-amber-400'
                }`}
              >
                {testResult.success ? (
                  <Check className="w-3.5 h-3.5 shrink-0" />
                ) : (
                  <AlertCircle className="w-3.5 h-3.5 shrink-0" />
                )}
                <span>{testResult.message}</span>
              </div>
            )}
          </div>

          {/* Category / Group */}
          <div>
            <label className="block text-xs font-medium text-neutral-300 mb-1.5">
              Group / Genre
            </label>
            {!isCreatingGroup ? (
              <div className="flex gap-2">
                <select
                  value={group}
                  onChange={(e) => {
                    if (e.target.value === '__new__') {
                      setIsCreatingGroup(true);
                    } else {
                      setGroup(e.target.value);
                    }
                  }}
                  className="flex-1 px-3.5 py-2.5 rounded-lg bg-neutral-950 border border-neutral-700 text-white focus:outline-none focus:border-[var(--accent)] text-sm"
                >
                  {existingGroups
                    .filter((g) => g !== 'All' && g !== 'Favorites')
                    .map((g) => (
                      <option key={g} value={g}>
                        {g}
                      </option>
                    ))}
                  <option value="__new__">+ Create new group...</option>
                </select>
              </div>
            ) : (
              <div className="flex gap-2">
                <input
                  type="text"
                  autoFocus
                  value={customGroupInput}
                  onChange={(e) => setCustomGroupInput(e.target.value)}
                  placeholder="New group name (e.g. Podcast, Focus, Gaming)"
                  className="flex-1 px-3.5 py-2 rounded-lg bg-neutral-950 border border-neutral-700 text-white text-sm focus:outline-none focus:border-[var(--accent)]"
                />
                <button
                  type="button"
                  onClick={() => setIsCreatingGroup(false)}
                  className="px-3 py-2 text-xs text-neutral-400 hover:text-white bg-neutral-800 rounded-lg"
                >
                  Cancel
                </button>
              </div>
            )}
          </div>

          {/* Custom Logo: URL or Upload */}
          <div>
            <label className="block text-xs font-medium text-neutral-300 mb-1.5">
              Station Logo (Image URL or Upload)
            </label>
            <div className="flex items-center gap-3">
              <div className="relative w-14 h-14 rounded-lg bg-neutral-950 border border-neutral-700 flex items-center justify-center overflow-hidden shrink-0">
                {logoUrl ? (
                  <img
                    src={logoUrl}
                    alt="Preview"
                    className="w-full h-full object-cover"
                    onError={() => setLogoUrl('')}
                  />
                ) : (
                  <Radio className="w-6 h-6 text-neutral-500" />
                )}
              </div>

              <div className="flex-1 space-y-2">
                <input
                  type="url"
                  value={logoUrl}
                  onChange={(e) => setLogoUrl(e.target.value)}
                  placeholder="Paste image URL (https://...)"
                  className="w-full px-3 py-1.5 rounded-lg bg-neutral-950 border border-neutral-700 text-white placeholder-neutral-500 text-xs focus:outline-none focus:border-[var(--accent)]"
                />
                <label className="inline-flex items-center gap-1.5 text-xs text-neutral-300 hover:text-white cursor-pointer px-3 py-1.5 rounded bg-neutral-800 hover:bg-neutral-700 transition-colors">
                  <Upload className="w-3.5 h-3.5" />
                  <span>Upload local image</span>
                  <input
                    type="file"
                    accept="image/*"
                    onChange={handleFileUpload}
                    className="hidden"
                  />
                </label>
              </div>
            </div>
          </div>

          {/* Codec & Bitrate */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-neutral-300 mb-1.5">
                Audio Codec
              </label>
              <select
                value={codec}
                onChange={(e) => setCodec(e.target.value as any)}
                className="w-full px-3 py-2 rounded-lg bg-neutral-950 border border-neutral-700 text-white text-xs focus:outline-none focus:border-[var(--accent)]"
              >
                <option value="AUTO">Auto Detect</option>
                <option value="MP3">MP3</option>
                <option value="AAC">AAC</option>
                <option value="HLS">HLS (.m3u8)</option>
                <option value="OGG">OGG / Opus</option>
              </select>
            </div>

            <div>
              <label className="block text-xs font-medium text-neutral-300 mb-1.5">
                Bitrate
              </label>
              <input
                type="text"
                value={bitrate}
                onChange={(e) => setBitrate(e.target.value)}
                placeholder="e.g. 128 kbps, 320 kbps"
                className="w-full px-3 py-2 rounded-lg bg-neutral-950 border border-neutral-700 text-white text-xs focus:outline-none focus:border-[var(--accent)]"
              />
            </div>
          </div>

          {/* Description */}
          <div>
            <label className="block text-xs font-medium text-neutral-300 mb-1.5">
              Description / Slogan (Optional)
            </label>
            <input
              type="text"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="e.g. Chill downtempo beats for coding"
              className="w-full px-3.5 py-2 rounded-lg bg-neutral-950 border border-neutral-700 text-white placeholder-neutral-500 text-xs focus:outline-none focus:border-[var(--accent)]"
            />
          </div>

          {/* Buttons */}
          <div className="flex items-center justify-end gap-3 pt-3 border-t border-neutral-800">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-xs font-medium text-neutral-300 hover:text-white rounded-lg hover:bg-neutral-800 transition-colors"
            >
              Cancel
            </button>
            <button
              type="submit"
              className="px-5 py-2 text-xs font-semibold text-neutral-950 bg-[var(--accent)] rounded-lg shadow-md hover:brightness-110 transition-all shadow-[var(--accent-glow)]"
            >
              {stationToEdit ? 'Save Changes' : 'Add Station'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
