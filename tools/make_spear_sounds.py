#!/usr/bin/env python3
"""Generate the spear sound set.

1.21.11 ships six dedicated samples (``item.spear.{attack,hit,use}`` and their
``wood_`` twins) that do not exist in 1.12.2, so this script synthesises an
equivalent set from scratch: a short airy thrust, a piercing impact, and the
low sweep heard while the spear is raised.

Everything is generated procedurally, which keeps the mod free of third-party
audio and makes the assets reproducible:

    python tools/make_spear_sounds.py src/main/resources/assets/spearbackport/sounds/item

Requires numpy and soundfile (which bundles libsndfile with Vorbis support).
"""

import os
import sys
import wave

import numpy as np

SAMPLE_RATE = 44100


def _log(message):
    print(message)


# ----------------------------------------------------------------------
# Building blocks
# ----------------------------------------------------------------------
def noise(rng, length):
    return rng.standard_normal(length)


def bandpass(signal, centre, q=1.0):
    """Chamberlin state-variable band pass with a per-sample centre frequency."""
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


def sweep(length, start, mid, end):
    """A centre frequency that rises to *mid* then settles on *end*."""
    t = np.linspace(0.0, 1.0, length)
    rise = start + (mid - start) * np.clip(t / 0.35, 0.0, 1.0)
    fall = mid + (end - mid) * np.clip((t - 0.35) / 0.65, 0.0, 1.0)
    return np.where(t < 0.35, rise, fall)


def envelope(length, attack, decay, power=1.0):
    t = np.arange(length) / SAMPLE_RATE
    rise = 1.0 - np.exp(-t / max(attack, 1e-5))
    fall = np.exp(-np.power(t / decay, power))
    return rise * fall


def glide(length, start_hz, end_hz, curve=2.6):
    """Exponential pitch glide, as used for the impact body."""
    t = np.linspace(0.0, 1.0, length)
    hz = start_hz + (end_hz - start_hz) * np.power(t, 1.0 / curve)
    phase = 2.0 * np.pi * np.cumsum(hz) / SAMPLE_RATE
    return np.sin(phase)


def normalise(signal, peak):
    highest = float(np.max(np.abs(signal)))
    if highest < 1e-9:
        return signal
    return signal / highest * peak


def fade_out(signal, seconds=0.01):
    length = min(int(seconds * SAMPLE_RATE), signal.shape[0])
    if length > 1:
        signal[-length:] *= np.linspace(1.0, 0.0, length)
    return signal


def finish(signal, peak):
    signal = normalise(signal, peak)
    signal = fade_out(signal)
    return np.tanh(signal * 1.1) / np.tanh(1.1)


# ----------------------------------------------------------------------
# Voices
# ----------------------------------------------------------------------
def thrust_whoosh(seed, duration, centre, q, level):
    rng = np.random.default_rng(seed)
    length = int(duration * SAMPLE_RATE)
    air = bandpass(noise(rng, length), centre, q)
    air *= envelope(length, 0.004, duration * 0.34, power=1.4)
    # A very short high "rip" right at the start makes the thrust read as a
    # point going through air rather than as a generic swing.
    rip = bandpass(noise(rng, length), sweep(length, 5200.0, 6400.0, 3600.0), 2.6)
    rip *= envelope(length, 0.0015, 0.014, power=1.0)
    return air * level + rip * level * 0.5


def low_sweep(seed, duration, centre, level):
    rng = np.random.default_rng(seed)
    length = int(duration * SAMPLE_RATE)
    air = bandpass(noise(rng, length), centre, 1.6)
    air *= envelope(length, 0.09, duration * 0.55, power=1.2)
    body = glide(length, 96.0, 132.0, 3.0) * envelope(length, 0.12, duration * 0.6)
    return air * level + body * level * 0.35


def impact(seed, duration, body_hz, crack_hz, ring_hz, level):
    rng = np.random.default_rng(seed)
    length = int(duration * SAMPLE_RATE)
    thump = glide(length, body_hz, body_hz * 0.32, 2.4)
    thump *= envelope(length, 0.0015, duration * 0.20, power=1.0)
    crack = bandpass(noise(rng, length), crack_hz, 1.1)
    crack *= envelope(length, 0.002, duration * 0.16, power=1.0)
    signal = thump * 0.95 + crack * 0.6
    if ring_hz is not None:
        t = np.arange(length) / SAMPLE_RATE
        ring = (np.sin(2.0 * np.pi * ring_hz * t)
                + 0.6 * np.sin(2.0 * np.pi * ring_hz * 1.33 * t))
        ring *= envelope(length, 0.003, duration * 0.30, power=1.0)
        signal = signal + ring * 0.10
    return signal * level


# ----------------------------------------------------------------------
# The six samples
# ----------------------------------------------------------------------
def build_samples():
    metal_attack = thrust_whoosh(11, 0.22, sweep(int(0.22 * SAMPLE_RATE), 900.0, 4200.0, 1500.0), 1.2, 1.0)
    wood_attack = thrust_whoosh(13, 0.18, sweep(int(0.18 * SAMPLE_RATE), 520.0, 2500.0, 800.0), 1.6, 1.0)

    metal_use = low_sweep(21, 0.50, sweep(int(0.50 * SAMPLE_RATE), 280.0, 1250.0, 900.0), 1.0)
    wood_use = low_sweep(23, 0.44, sweep(int(0.44 * SAMPLE_RATE), 220.0, 880.0, 620.0), 1.0)

    metal_hit = impact(31, 0.30, 190.0, np.full(int(0.30 * SAMPLE_RATE), 3200.0), 2350.0, 1.0)
    wood_hit = impact(33, 0.24, 150.0, np.full(int(0.24 * SAMPLE_RATE), 1500.0), None, 1.0)

    return {
        "spear_attack": (metal_attack, 0.82),
        "spear_attack_wood": (wood_attack, 0.78),
        "spear_use": (metal_use, 0.55),
        "spear_use_wood": (wood_use, 0.52),
        "spear_hit": (metal_hit, 0.92),
        "spear_hit_wood": (wood_hit, 0.88),
    }


def write_ogg(path, signal):
    import soundfile

    soundfile.write(path, signal.astype(np.float32), SAMPLE_RATE,
                    format="OGG", subtype="VORBIS")


def write_wav(path, signal):
    """Fallback used only when soundfile is unavailable."""
    pcm = np.clip(signal, -1.0, 1.0)
    pcm = (pcm * 32767.0).astype("<i2")
    with wave.open(path, "wb") as handle:
        handle.setnchannels(1)
        handle.setsampwidth(2)
        handle.setframerate(SAMPLE_RATE)
        handle.writeframes(pcm.tobytes())


def main(argv):
    if len(argv) != 2:
        _log(__doc__)
        return 2

    out_dir = argv[1]
    os.makedirs(out_dir, exist_ok=True)

    try:
        import soundfile  # noqa: F401
        writer = write_ogg
        suffix = "ogg"
    except ImportError:
        _log("soundfile is unavailable; writing WAV files that must be converted by hand")
        writer = write_wav
        suffix = "wav"

    for name, (signal, peak) in build_samples().items():
        path = os.path.join(out_dir, "%s.%s" % (name, suffix))
        writer(path, finish(signal, peak))
        _log("wrote %s" % path)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
