import React, { useEffect, useRef } from 'react';
import { audioEngine } from '../utils/audioEngine';

interface AudioVisualizerProps {
  isPlaying: boolean;
  barCount?: number;
  height?: number;
  accentColor?: string;
}

export const AudioVisualizer: React.FC<AudioVisualizerProps> = ({
  isPlaying,
  barCount = 28,
  height = 54,
  accentColor = '#f59e0b',
}) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const animFrameId = useRef<number | null>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let simPhase = 0;
    const dataArray = new Uint8Array(64);

    const render = () => {
      const analyser = audioEngine.getAnalyser();
      const width = canvas.width;
      const ch = canvas.height;

      ctx.clearRect(0, 0, width, ch);

      let hasRealData = false;
      if (analyser && isPlaying) {
        analyser.getByteFrequencyData(dataArray);
        for (let i = 0; i < 32; i++) {
          if (dataArray[i] > 10) {
            hasRealData = true;
            break;
          }
        }
      }

      const barWidth = Math.max(2, (width / barCount) - 3);

      for (let i = 0; i < barCount; i++) {
        let barHeight = 4;

        if (isPlaying) {
          if (hasRealData) {
            const index = Math.floor((i / barCount) * 24);
            const val = dataArray[index] || 0;
            barHeight = Math.max(4, (val / 255) * (ch - 6));
          } else {
            // Simulated dynamic pulse when playing audio without CORS WebAudio reflection
            const wave1 = Math.sin(simPhase + i * 0.35) * 0.5 + 0.5;
            const wave2 = Math.cos(simPhase * 1.4 + i * 0.2) * 0.5 + 0.5;
            const combined = (wave1 * 0.6 + wave2 * 0.4);
            barHeight = Math.max(4, combined * (ch - 8));
          }
        } else {
          barHeight = 3;
        }

        const x = i * (barWidth + 3);
        const y = ch - barHeight;

        // Gradient for bars
        const grad = ctx.createLinearGradient(0, y, 0, ch);
        grad.addColorStop(0, accentColor);
        grad.addColorStop(1, `${accentColor}44`);

        ctx.fillStyle = grad;
        // Rounded bars
        ctx.beginPath();
        const radius = Math.min(barWidth / 2, 2);
        ctx.roundRect(x, y, barWidth, barHeight, [radius, radius, 0, 0]);
        ctx.fill();
      }

      if (isPlaying) {
        simPhase += 0.08;
      }

      animFrameId.current = requestAnimationFrame(render);
    };

    render();

    return () => {
      if (animFrameId.current) {
        cancelAnimationFrame(animFrameId.current);
      }
    };
  }, [isPlaying, barCount, accentColor]);

  return (
    <canvas
      ref={canvasRef}
      width={barCount * 8}
      height={height}
      className="w-full h-full object-contain pointer-events-none"
    />
  );
};
