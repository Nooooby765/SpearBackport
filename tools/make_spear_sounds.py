#!/usr/bin/env python3
"""Generate the spear sound set.

1.21.11 ships dedicated ``item.spear.{attack,hit,use,lunge}`` samples plus a
``wood_`` family for the wooden tier. Those assets do not exist in 1.12.2, so this
script synthesises an equivalent set from scratch and mirrors the official
layout: three randomised variants per thrust/impact voice, one quiet stance
sound, one wind sound for Lunge, and a darker wooden family.

The targets were measured from the official files, so the result lands in the
same loudness and spectral range without using any game asset:

    voice    loud RMS   spectral centroid   character
    attack   0.12-0.24  ~3.5 kHz metal      short granular thrust
    hit      0.11-0.20  ~5.5 kHz metal      bright crack plus low body
    use      0.03-0.04  ~2.8 kHz            two soft swells, no transient
    lunge    0.05-0.18  ~4.0 kHz            long airy rush

``bright`` scales the whole spectrum per family: 1.0 is metal, ~0.55 is wood.

    python tools/make_spear_sounds.py src/main/resources/assets/spearbackport/sounds/item/spear

Requires numpy and soundfile (which bundles libsndfile with Vorbis support).
"""

import os
import sys
import wave
import zlib

import numpy as np

SAMPLE_RATE = 48000


# ----------------------------------------------------------------------
# Building blocks
# ----------------------------------------------------------------------
def noise(rng, length):
    return rng.standard_normal(length)


def bandpass(signal, centre, q=1.0):
    """Chamberlin state variable band pass with a per-sample centre frequency."""
    out = np.empty_like(signal)
    low = 0.0
    band = 0.0
    damp = 1.0 / max(q, 0.05)
    for i in range(signal.shape[0]):
        f = 2.0 * np.sin(np.pi * min(centre[i], SAMPLE_RATE / 6.0) / SAMPLE_RATE)
        low += f * band
        high = signal[i] - low - damp * band
        band += f * high
        out[i] = band
    return out


def bandpass2(signal, centre, q=1.0):
    """Fourth order: two cascaded passes, so the skirt actually rolls off."""
    return bandpass(bandpass(signal, centre, q), centre, q)


def tone(signal, cutoff):
    """One pole low pass, as a short FIR so it stays vectorised."""
    span = max(2, int(3.0 * SAMPLE_RATE / max(cutoff, 40.0)))
    kernel = np.exp(-np.arange(span) * (2.0 * np.pi * cutoff / SAMPLE_RATE))
    kernel /= kernel.sum()
    if span >= len(signal):
        return signal
    return np.convolve(signal, kernel, mode="same")


def fixed(frequency, length):
    return np.full(length, float(frequency))


def sweep(length, start, peak, end, peak_at=0.3):
    t = np.linspace(0.0, 1.0, length)
    rise = start + (peak - start) * np.clip(t / peak_at, 0.0, 1.0)
    fall = peak + (end - peak) * np.clip((t - peak_at) / (1.0 - peak_at), 0.0, 1.0)
    return np.where(t < peak_at, rise, fall)


def envelope(length, attack, decay, power=1.0):
    t = np.arange(length) / SAMPLE_RATE
    return (1.0 - np.exp(-t / max(attack, 1e-5))) * np.exp(-np.power(t / decay, power))


def grains(rng, length, rate, decay, depth):
    """Amplitude modulation made of short random bursts.

    The official samples are not a smooth whoosh: their spectrograms are full of
    fine vertical striations, i.e. a rapid flutter of tiny air bursts. This
    reproduces that texture.
    """
    impulses = np.zeros(length)
    count = max(1, int(length / SAMPLE_RATE * rate))
    impulses[rng.integers(0, length, count)] = rng.uniform(0.25, 1.0, count)
    span = max(2, int(decay * SAMPLE_RATE))
    kernel = np.exp(-np.arange(span) / (span / 4.0))
    grain = np.convolve(impulses, kernel, mode="same")
    peak = grain.max()
    if peak > 1e-9:
        grain /= peak
    return (1.0 - depth) + depth * grain


def glide(length, start_hz, end_hz, curve=2.6):
    t = np.linspace(0.0, 1.0, length)
    hz = start_hz + (end_hz - start_hz) * np.power(t, 1.0 / curve)
    return np.sin(2.0 * np.pi * np.cumsum(hz) / SAMPLE_RATE)


# ----------------------------------------------------------------------
# Analysis helpers, also used to normalise the rendered result
# ----------------------------------------------------------------------
def loud_rms(signal, hop=256, window=512):
    frames = []
    for start in range(0, max(1, len(signal) - window), hop):
        chunk = signal[start:start + window]
        chunk = np.pad(chunk, (0, window - len(chunk)))
        frames.append(float(np.sqrt(np.mean(chunk ** 2))))
    frames = np.array(frames)
    above = np.where(frames >= frames.max() * 0.1)[0]
    if not len(above):
        return float(np.sqrt(np.mean(signal ** 2)))
    start = above[0] * hop
    end = min(len(signal), (above[-1] + 1) * hop + window)
    return float(np.sqrt(np.mean(signal[start:end] ** 2)))


def centroid(signal, rate=SAMPLE_RATE):
    spectrum = np.abs(np.fft.rfft(signal * np.hanning(len(signal))))
    freqs = np.fft.rfftfreq(len(signal), 1.0 / rate)
    return float(np.sum(freqs * spectrum) / max(np.sum(spectrum), 1e-9))


def finish(signal, loud, peak_cap, tail=0.08):
    """Normalise to a loudness target, then to a peak ceiling, then add a tail."""
    current = loud_rms(signal)
    if current > 1e-9:
        signal = signal / current * loud
    highest = float(np.max(np.abs(signal)))
    if highest > peak_cap:
        signal = signal / highest * peak_cap
    fade = min(int(0.004 * SAMPLE_RATE), len(signal))
    if fade > 1:
        signal[-fade:] *= np.linspace(1.0, 0.0, fade)
    return np.concatenate([signal, np.zeros(int(tail * SAMPLE_RATE))])


# ----------------------------------------------------------------------
# Voices.  `bright` scales the whole spectrum: metal is bright, wood is dark.
# ----------------------------------------------------------------------
def thrust(seed, duration, bright, loud=0.16):
    rng = np.random.default_rng(seed)
    length = int(duration * SAMPLE_RATE)

    centre = sweep(length, 1320.0, 2360.0, 1035.0, 0.2) * bright
    air = bandpass2(noise(rng, length), centre, 1.5)
    air *= envelope(length, 0.003, duration * 0.14, power=1.4)
    air *= grains(rng, length, 55.0, 0.008, 0.68)

    # a brief upper "snap" gives the thrust its point without brightening the
    # whole voice: 10 ms at 3.6 kHz, far quieter than the air layer
    snap = bandpass(noise(rng, length), fixed(3800.0 * bright, length), 1.8)
    snap *= envelope(length, 0.0012, 0.010) * 0.22

    body = glide(length, 240.0, 110.0, 3.2) * envelope(length, 0.002, 0.028) * 0.30

    return finish(tone(air + snap + body, 6000.0 * bright), loud, 0.95)


def impact(seed, duration, bright, loud=0.18):
    rng = np.random.default_rng(seed)
    length = int(duration * SAMPLE_RATE)

    crack = bandpass2(noise(rng, length), fixed(3900.0 * bright, length), 1.2)
    crack *= envelope(length, 0.0018, duration * 0.10)
    crack *= grains(rng, length, 75.0, 0.007, 0.72)

    body = glide(length, 190.0, 65.0, 2.4) * envelope(length, 0.0015, duration * 0.10) * 1.0

    # a second, quieter crack a few milliseconds later reads as a real impact
    offset = int(0.030 * SAMPLE_RATE)
    late = np.zeros(length)
    tail = bandpass(noise(rng, length - offset), fixed(2400.0 * bright, length - offset), 1.3)
    late[offset:] = tail * envelope(length - offset, 0.002, duration * 0.08) * 0.5

    return finish(tone(crack + body + late, 7200.0 * bright), loud, 1.0)


def stance(seed, duration, bright, loud=0.055):
    """The quiet 'spear raised' sound: two soft swells, no transient."""
    rng = np.random.default_rng(seed)
    length = int(duration * SAMPLE_RATE)
    t = np.linspace(0.0, 1.0, length)

    swell = (np.exp(-np.power((t - 0.10) / 0.11, 2.0))
             + 0.75 * np.exp(-np.power((t - 0.62) / 0.16, 2.0)))
    air = bandpass2(noise(rng, length), fixed(1500.0 * bright, length), 1.6)
    air *= swell * grains(rng, length, 16.0, 0.035, 0.60) * 0.75

    body = (0.7 * np.sin(2.0 * np.pi * 96.0 * t) + 0.3 * np.sin(2.0 * np.pi * 143.0 * t))
    body *= np.exp(-np.power((t - 0.42) / 0.30, 2.0)) * 1.0

    return finish(tone(air + body, 3800.0 * bright), loud, 0.70, tail=0.05)


def wind(seed, duration, bright, loud=0.105):
    """Lunge: a long rush of air that opens up and settles."""
    rng = np.random.default_rng(seed)
    length = int(duration * SAMPLE_RATE)
    centre = sweep(length, 736.0, 1900.0, 830.0, 0.42) * bright
    air = bandpass2(noise(rng, length), centre, 0.9)
    air *= envelope(length, 0.09, duration * 0.42, power=1.5)
    air *= grains(rng, length, 34.0, 0.012, 0.35)
    return finish(tone(air, 4800.0 * bright), loud, 0.85)


# ----------------------------------------------------------------------
# The set
# ----------------------------------------------------------------------
FAMILIES = {"metal": 1.0, "wood": 0.72}

DURATIONS = {"attack": 0.46, "hit": 0.55, "use": 1.12, "lunge": 1.50}


def build():
    """Yields (relative path, signal)."""
    for family, bright in FAMILIES.items():
        base = "" if family == "metal" else "wood/"
        # Only the metal family has a Lunge voice, exactly like 1.21.11.
        names = ["attack", "hit", "use"] + (["lunge"] if family == "metal" else [])
        for name in names:
            duration = DURATIONS[name]
            if name == "use":
                yield base + "use", stance(707, duration, bright)
                continue
            voice = {"attack": thrust, "hit": impact, "lunge": wind}[name]
            for index in range(1, 4):
                # crc32, not hash(): the assets must be identical across runs
                seed = zlib.crc32(("%s/%s/%d" % (family, name, index)).encode()) & 0xFFFF
                # small per-variant spread so repeated strikes do not sound cloned
                shift = 1.0 + (index - 2) * 0.10
                yield "%s%d" % (base + name, index), voice(seed, duration, bright * shift)


def write_ogg(path, signal):
    import soundfile

    soundfile.write(path, signal.astype(np.float32), SAMPLE_RATE,
                    format="OGG", subtype="VORBIS")


def write_wav(path, signal):
    pcm = (np.clip(signal, -1.0, 1.0) * 32767.0).astype("<i2")
    with wave.open(path, "wb") as handle:
        handle.setnchannels(1)
        handle.setsampwidth(2)
        handle.setframerate(SAMPLE_RATE)
        handle.writeframes(pcm.tobytes())


def remove_stale(out_dir):
    """Drop samples from a previous run so the jar never ships orphans."""
    for root, _, files in os.walk(out_dir):
        for name in files:
            if name.endswith(".ogg") or name.endswith(".wav"):
                path = os.path.join(root, name)
                os.remove(path)
                print("removed stale %s" % path)


def main(argv):
    if len(argv) != 2:
        print(__doc__)
        return 2

    out_dir = argv[1]
    os.makedirs(os.path.join(out_dir, "wood"), exist_ok=True)

    try:
        import soundfile  # noqa: F401
        writer, suffix = write_ogg, "ogg"
    except ImportError:
        print("soundfile is unavailable; writing WAV files that must be converted by hand")
        writer, suffix = write_wav, "wav"

    remove_stale(out_dir)
    print("%-16s %6s %6s %6s %8s %7s" % ("sample", "secs", "peak", "loud", "centroid", "lufs-ish"))
    for name, signal in build():
        path = os.path.join(out_dir, "%s.%s" % (name, suffix))
        writer(path, signal)
        print("%-16s %6.2f %6.2f %6.3f %8.0f %7.1f"
              % (name, len(signal) / SAMPLE_RATE, float(np.max(np.abs(signal))),
                 loud_rms(signal), centroid(signal),
                 -0.691 + 10.0 * np.log10(max(np.mean(signal ** 2), 1e-12))))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
