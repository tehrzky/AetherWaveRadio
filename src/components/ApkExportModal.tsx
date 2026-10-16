import React, { useState } from 'react';
import {
  X,
  Smartphone,
  Download,
  ExternalLink,
  Check,
  Copy,
  Radio,
  Code2,
  FileCode,
  FolderArchive,
  Layers,
} from 'lucide-react';
import { usePWAInstall } from '../hooks/usePWAInstall';

interface ApkExportModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const ApkExportModal: React.FC<ApkExportModalProps> = ({ isOpen, onClose }) => {
  const { isInstallable, isInstalled, install } = usePWAInstall();
  const [activeTab, setActiveTab] = useState<'kotlin' | 'pwa'>('kotlin');
  const [selectedCodeFile, setSelectedCodeFile] = useState<'service' | 'main' | 'manifest'>('service');
  const [copiedCode, setCopiedCode] = useState(false);

  if (!isOpen) return null;

  const appUrl = typeof window !== 'undefined' ? window.location.origin : '';

  const kotlinServiceSnippet = `// RadioPlaybackService.kt - Native AndroidX Media3 Foreground Audio Service
package com.aetherwave.radio.service

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class RadioPlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true) // Handles Audio Focus automatically
            .setHandleAudioBecomingNoisy(true) // Auto-pause when Bluetooth disconnects
            .setWakeMode(C.WAKE_MODE_NETWORK) // Keeps audio streaming screen-off
            .build()

        mediaSession = MediaSession.Builder(this, player!!).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession
}`;

  const kotlinMainSnippet = `// MainActivity.kt - Native Jetpack Compose Material 3 UI
package com.aetherwave.radio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.aetherwave.radio.ui.RadioViewModel
import com.aetherwave.radio.ui.theme.AetherWaveRadioTheme

class MainActivity : ComponentActivity() {
    private val viewModel: RadioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AetherWaveRadioTheme {
                RadioAppScreen(viewModel)
            }
        }
    }
}`;

  const manifestSnippet = `<!-- AndroidManifest.xml -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

    <application android:theme="@style/Theme.AetherWaveRadio">
        <service
            android:name=".service.RadioPlaybackService"
            android:exported="false"
            android:foregroundServiceType="mediaPlayback">
            <intent-filter>
                <action android:name="androidx.media3.session.MediaSessionService" />
            </intent-filter>
        </service>
    </application>
</manifest>`;

  const getCodeSnippet = () => {
    switch (selectedCodeFile) {
      case 'service':
        return kotlinServiceSnippet;
      case 'main':
        return kotlinMainSnippet;
      case 'manifest':
        return manifestSnippet;
    }
  };

  const handleCopyCode = () => {
    navigator.clipboard.writeText(getCodeSnippet());
    setCopiedCode(true);
    setTimeout(() => setCopiedCode(false), 2000);
  };

  return (
    <div className="fixed inset-0 z-50 bg-black/85 backdrop-blur-sm flex items-center justify-center p-3 sm:p-6 overflow-y-auto">
      <div className="bg-neutral-900 border border-neutral-800 rounded-3xl w-full max-w-2xl overflow-hidden shadow-2xl animate-in fade-in zoom-in-95 duration-150 my-6">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-neutral-800 bg-neutral-950/70">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400">
              <Smartphone className="w-5 h-5" />
            </div>
            <div>
              <h2 className="font-bold text-lg text-white">Native Android App (Kotlin & APK)</h2>
              <p className="text-xs text-neutral-400">Complete native Kotlin project & APK deployment options</p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1.5 text-neutral-400 hover:text-white rounded-full hover:bg-neutral-800 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Tab Switcher */}
        <div className="flex border-b border-neutral-800 px-6 bg-neutral-950/40">
          <button
            type="button"
            onClick={() => setActiveTab('kotlin')}
            className={`py-3 px-4 text-xs font-bold border-b-2 transition-colors flex items-center gap-2 ${
              activeTab === 'kotlin'
                ? 'border-[var(--accent)] text-white'
                : 'border-transparent text-neutral-400 hover:text-neutral-200'
            }`}
          >
            <FolderArchive className="w-4 h-4 text-emerald-400" />
            <span>Native Kotlin Project (.ZIP)</span>
          </button>
          <button
            type="button"
            onClick={() => setActiveTab('pwa')}
            className={`py-3 px-4 text-xs font-bold border-b-2 transition-colors flex items-center gap-2 ${
              activeTab === 'pwa'
                ? 'border-[var(--accent)] text-white'
                : 'border-transparent text-neutral-400 hover:text-neutral-200'
            }`}
          >
            <Layers className="w-4 h-4 text-sky-400" />
            <span>Instant Android WebAPK</span>
          </button>
        </div>

        {/* Content */}
        <div className="p-6 space-y-5">
          {activeTab === 'kotlin' ? (
            <>
              {/* Download Native Android Studio Project Banner */}
              <div className="p-4 rounded-2xl bg-neutral-950 border border-emerald-500/20 space-y-3">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
                    <h3 className="font-bold text-sm text-white">Complete Kotlin Android Studio Project</h3>
                  </div>
                  <span className="text-[11px] font-mono px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                    Kotlin + Compose + Media3
                  </span>
                </div>

                <p className="text-xs text-neutral-300 leading-relaxed">
                  We generated the complete native Android codebase in Kotlin with <strong>Jetpack Compose</strong>, <strong>Material 3 Expressive</strong>, and <strong>AndroidX Media3 (ExoPlayer)</strong> for real background audio streaming, lockscreen controls, and Bluetooth headset events!
                </p>

                <a
                  href="/api/download-android-project"
                  download="aetherwave-android-kotlin.zip"
                  className="w-full flex items-center justify-center gap-2 py-3 px-4 rounded-xl bg-emerald-500 hover:bg-emerald-400 text-neutral-950 font-bold text-xs shadow-lg transition-all"
                >
                  <Download className="w-4 h-4 stroke-[2.5]" />
                  <span>Download Full Android Studio Project (.ZIP)</span>
                </a>
              </div>

              {/* Code File Viewer */}
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-1.5 p-1 rounded-xl bg-neutral-950 border border-white/10">
                    <button
                      type="button"
                      onClick={() => setSelectedCodeFile('service')}
                      className={`px-3 py-1 rounded-lg text-xs font-semibold transition-colors ${
                        selectedCodeFile === 'service'
                          ? 'bg-[var(--accent)] text-neutral-950'
                          : 'text-neutral-400 hover:text-white'
                      }`}
                    >
                      RadioPlaybackService.kt
                    </button>
                    <button
                      type="button"
                      onClick={() => setSelectedCodeFile('main')}
                      className={`px-3 py-1 rounded-lg text-xs font-semibold transition-colors ${
                        selectedCodeFile === 'main'
                          ? 'bg-[var(--accent)] text-neutral-950'
                          : 'text-neutral-400 hover:text-white'
                      }`}
                    >
                      MainActivity.kt
                    </button>
                    <button
                      type="button"
                      onClick={() => setSelectedCodeFile('manifest')}
                      className={`px-3 py-1 rounded-lg text-xs font-semibold transition-colors ${
                        selectedCodeFile === 'manifest'
                          ? 'bg-[var(--accent)] text-neutral-950'
                          : 'text-neutral-400 hover:text-white'
                      }`}
                    >
                      AndroidManifest.xml
                    </button>
                  </div>

                  <button
                    type="button"
                    onClick={handleCopyCode}
                    className="text-xs text-[var(--accent)] hover:underline flex items-center gap-1"
                  >
                    {copiedCode ? (
                      <>
                        <Check className="w-3.5 h-3.5 text-emerald-400" />
                        <span>Copied!</span>
                      </>
                    ) : (
                      <>
                        <Copy className="w-3.5 h-3.5" />
                        <span>Copy Code</span>
                      </>
                    )}
                  </button>
                </div>

                <pre className="p-3.5 rounded-xl bg-black/80 border border-white/5 font-mono text-[11px] text-neutral-300 max-h-52 overflow-y-auto overflow-x-auto leading-relaxed select-all">
                  {getCodeSnippet()}
                </pre>
              </div>

              {/* Steps to build APK in Android Studio */}
              <div className="p-3.5 rounded-xl bg-neutral-950/60 border border-white/5 space-y-1.5 text-xs text-neutral-400">
                <p className="font-semibold text-neutral-200">How to generate your .APK in Android Studio:</p>
                <ol className="list-decimal list-inside space-y-1 text-neutral-400">
                  <li>Unzip the downloaded <code className="text-emerald-400">aetherwave-android-kotlin.zip</code></li>
                  <li>Open Android Studio and click <strong>Open Existing Project</strong></li>
                  <li>Click <strong>Build &gt; Build Bundle(s) / APK(s) &gt; Build APK(s)</strong></li>
                  <li>Transfer the generated <code className="text-[var(--accent)] font-mono">app-debug.apk</code> to your Android phone!</li>
                </ol>
              </div>
            </>
          ) : (
            <>
              {/* WebAPK option */}
              <div className="p-4 rounded-2xl bg-neutral-950 border border-white/5 space-y-3">
                <div className="flex items-center justify-between">
                  <h3 className="font-bold text-sm text-white">Instant Android WebAPK (Zero Android Studio)</h3>
                  <span className="text-[11px] font-mono px-2 py-0.5 rounded bg-sky-500/10 text-sky-400 border border-sky-500/20">
                    Instant Install
                  </span>
                </div>

                <p className="text-xs text-neutral-400 leading-relaxed">
                  If you don&apos;t want to compile via Android Studio, Android Chrome/Edge can convert this application directly into an installed <strong>WebAPK</strong> on your phone.
                </p>

                {isInstalled ? (
                  <div className="flex items-center gap-2 text-xs text-emerald-400 bg-emerald-500/10 border border-emerald-500/20 px-3 py-2.5 rounded-xl">
                    <Check className="w-4 h-4 shrink-0" />
                    <span>Application is already installed in Standalone Mode on this device!</span>
                  </div>
                ) : isInstallable ? (
                  <button
                    type="button"
                    onClick={async () => {
                      await install();
                    }}
                    className="w-full flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl bg-[var(--accent)] text-neutral-950 font-bold text-xs shadow-md hover:brightness-110 transition-all shadow-[var(--accent-glow)]"
                  >
                    <Download className="w-4 h-4" />
                    <span>Install App on Android Now</span>
                  </button>
                ) : (
                  <div className="text-xs text-neutral-400 bg-neutral-900 p-3 rounded-xl border border-white/5">
                    <p className="font-medium text-neutral-300 mb-1">To install directly on Android phone:</p>
                    <ol className="list-decimal list-inside space-y-1 text-neutral-400">
                      <li>Open this URL on your phone&apos;s Chrome browser</li>
                      <li>Tap menu <strong className="text-neutral-200">(⋮)</strong> and select <strong className="text-neutral-200">&quot;Install App&quot;</strong></li>
                    </ol>
                  </div>
                )}
              </div>
            </>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end px-6 py-3 border-t border-neutral-800 bg-neutral-950/40">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 text-xs font-semibold text-neutral-300 hover:text-white rounded-xl hover:bg-neutral-800 transition-colors"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
};
