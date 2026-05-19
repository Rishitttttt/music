import javax.sound.midi.*;
import java.util.Random;

/**
 * ThreadMusic — Two-Phase Generative Music: Improvisation + Motif
 *
 * CONCEPT: Each thread is a jazz musician that knows two modes:
 *   1. RANDOM PHASE  — improvise freely within C pentatonic (8 beats)
 *   2. MOTIF PHASE   — all musicians lock in and play the same rising theme (4 beats)
 *
 * BeatKeeperThread owns the 12-beat cycle and flips isMotif on/off.
 * Thread scheduling non-determinism affects the random phase only —
 * the motif phase always sounds the same, giving the music recognizable structure.
 *
 * Motif: E(64) → G(67) → A(69) → C(72) — a rising 4-note game-theme pattern.
 *
 * Shared volatile state (no locks needed):
 *   globalBeat — monotonically increasing beat counter
 *   isMotif    — true = all threads play motif, false = improvise
 *   motifStep  — current motif note index 0-3, set by BeatKeeperThread
 */
public class ThreadMusic {

    static volatile int     globalBeat = 0;
    static volatile boolean isMotif    = false;
    static volatile int     motifStep  = 0;      // 0-3, valid only when isMotif=true
    static volatile boolean playing    = true;

    static final int[] MOTIF_NOTES = {64, 67, 69, 72};       // E G A C
    static final int[] PENTATONIC  = {60, 62, 64, 67, 69, 72};
    static final int[] BASS_NOTES  = {48, 50, 52};

    static Synthesizer   synth;
    static MidiChannel[] channels;

    public static void main(String[] args) throws Exception {
        synth = MidiSystem.getSynthesizer();
        synth.open();
        channels = synth.getChannels();

        channels[0].programChange(32);    // Acoustic Bass
        channels[1].programChange(0);     // Grand Piano
        channels[2].programChange(48);    // String Ensemble
        // Channel 9: GM drum channel, always percussion

        Thread beatKeeper = new Thread(new BeatKeeperThread(), "BeatKeeper");
        Thread drums      = new Thread(new DrumThread(),       "Drums");
        Thread bass       = new Thread(new BassThread(),       "Bass");
        Thread melody     = new Thread(new MelodyThread(),     "Melody");
        Thread harmony    = new Thread(new HarmonyThread(),    "Harmony");

        beatKeeper.start();
        drums.start();
        bass.start();
        melody.start();
        harmony.start();

        System.out.println("ThreadMusic — 8 beats random, 4 beats motif, repeat. 30 seconds.\n");

        Thread.sleep(30_000);

        playing = false;

        beatKeeper.join();
        drums.join();
        bass.join();
        melody.join();
        harmony.join();

        synth.close();
        System.out.println("\nThreadMusic stopped.");
    }

    // ─── BeatKeeperThread ────────────────────────────────────────────────────
    // Controls the 12-beat cycle: beats 0-7 = random, beats 8-11 = motif.
    // Only writer of isMotif and motifStep — all other threads are readers.
    // motifStep is written before isMotif=true so readers always see a valid step.
    static class BeatKeeperThread implements Runnable {
        @Override
        public void run() {
            int beatInCycle = 0;   // 0-11
            while (playing) {
                try {
                    Thread.sleep(500);
                    globalBeat++;

                    if (beatInCycle == 8) {
                        motifStep = 0;
                        isMotif   = true;          // publish after motifStep is set
                        System.out.println("\n=== MOTIF PHASE  (beats 9-12) ===");
                    } else if (beatInCycle >= 9 && beatInCycle <= 11) {
                        motifStep = beatInCycle - 8;   // 1, 2, 3
                    } else if (beatInCycle == 0 && isMotif) {
                        isMotif = false;           // cycle wrapped — back to random
                        System.out.println("=== RANDOM PHASE (8 beats) ===\n");
                    }

                    beatInCycle = (beatInCycle + 1) % 12;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    // ─── DrumThread ──────────────────────────────────────────────────────────
    // Random: kick 1&3 / snare 2&4 / hihat every beat. Fires once per globalBeat.
    // Motif:  kick only, velocity 120 (harder hit), fires once per motifStep.
    static class DrumThread implements Runnable {
        private static final int KICK  = 36;
        private static final int SNARE = 38;
        private static final int HIHAT = 42;

        @Override
        public void run() {
            int lastBeat      = -1;
            int lastMotifStep = -1;
            while (playing) {
                try {
                    int beat = globalBeat;
                    if (isMotif) {
                        int step = motifStep;
                        if (step != lastMotifStep) {
                            lastMotifStep = step;
                            System.out.printf("[DRUMS   | Beat %-2d] MOTIF kick   (step %d)%n", beat, step);
                            channels[9].noteOn(KICK, 120);
                            Thread.sleep(60);
                            channels[9].noteOff(KICK, 0);
                        }
                    } else {
                        lastMotifStep = -1;
                        if (beat != lastBeat) {
                            lastBeat  = beat;
                            int pos   = beat % 4;   // 1=beat1 2=beat2 3=beat3 0=beat4
                            if (pos == 1 || pos == 3) {
                                System.out.printf("[DRUMS   | Beat %-2d] Kick + Hi-hat%n", beat);
                                channels[9].noteOn(KICK,  100);
                                channels[9].noteOn(HIHAT, 70);
                                Thread.sleep(60);
                                channels[9].noteOff(KICK,  0);
                                channels[9].noteOff(HIHAT, 0);
                            } else {
                                System.out.printf("[DRUMS   | Beat %-2d] Snare + Hi-hat%n", beat);
                                channels[9].noteOn(SNARE, 100);
                                channels[9].noteOn(HIHAT, 70);
                                Thread.sleep(60);
                                channels[9].noteOff(SNARE, 0);
                                channels[9].noteOff(HIHAT, 0);
                            }
                        }
                    }
                    Thread.sleep(40);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    // ─── BassThread ──────────────────────────────────────────────────────────
    // Random: picks from {48,50,52} on beats 1&3, 800ms sustain.
    // Motif:  holds note 48 (low C) for the entire 4-beat motif window.
    //         motifNoteOn flag ensures one noteOn and one noteOff per motif visit.
    static class BassThread implements Runnable {
        private final Random rand         = new Random();
        private       boolean motifNoteOn = false;

        @Override
        public void run() {
            int lastBeat = -1;
            while (playing) {
                try {
                    int beat = globalBeat;
                    if (isMotif) {
                        if (!motifNoteOn) {
                            motifNoteOn = true;
                            System.out.printf("[BASS    | Beat %-2d] MOTIF hold C  (48, all 4 beats)%n", beat);
                            channels[0].noteOn(48, 90);
                        }
                    } else {
                        if (motifNoteOn) {
                            motifNoteOn = false;
                            channels[0].noteOff(48, 0);
                        }
                        int pos = beat % 4;
                        if (beat != lastBeat && (pos == 1 || pos == 3)) {
                            lastBeat  = beat;
                            int note  = BASS_NOTES[rand.nextInt(BASS_NOTES.length)];
                            System.out.printf("[BASS    | Beat %-2d] Note %d%n", beat, note);
                            channels[0].noteOn(note, 80);
                            Thread.sleep(800);
                            channels[0].noteOff(note, 0);
                        }
                    }
                    Thread.sleep(40);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    // ─── MelodyThread ────────────────────────────────────────────────────────
    // Random: fast free improvisation, 200ms cycle, pentatonic only.
    // Motif:  plays MOTIF_NOTES[motifStep] exactly once per step change.
    //         Fastest thread, so it detects motifStep changes first.
    static class MelodyThread implements Runnable {
        private final Random rand = new Random();

        @Override
        public void run() {
            int lastMotifStep = -1;
            while (playing) {
                try {
                    int beat = globalBeat;
                    if (isMotif) {
                        int step = motifStep;
                        if (step != lastMotifStep) {
                            lastMotifStep = step;
                            int note = MOTIF_NOTES[step];
                            System.out.printf("[MELODY  | Beat %-2d] MOTIF note %d  (step %d)%n", beat, note, step);
                            channels[1].noteOn(note, 90);
                            Thread.sleep(380);
                            channels[1].noteOff(note, 0);
                        }
                        Thread.sleep(40);
                    } else {
                        lastMotifStep = -1;
                        int note     = PENTATONIC[rand.nextInt(PENTATONIC.length)];
                        int velocity = 60 + rand.nextInt(31);
                        System.out.printf("[MELODY  | Beat %-2d] Note %d%n", beat, note);
                        channels[1].noteOn(note, velocity);
                        Thread.sleep(120);
                        channels[1].noteOff(note, 0);
                        Thread.sleep(80);   // total ~200ms cycle
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    // ─── HarmonyThread ───────────────────────────────────────────────────────
    // Random: slow held note from pentatonic every 4 beats, 800ms sustain.
    // Motif:  echo effect — plays MOTIF_NOTES[step - 1] so it is always one
    //         step behind Melody: Melody plays [64,67,69,72],
    //                             Harmony plays [  -,64,67,69].
    //         prevNote carries the previous step's note forward.
    static class HarmonyThread implements Runnable {
        private final Random rand = new Random();

        @Override
        public void run() {
            int lastMotifStep = -1;
            int prevNote      = -1;     // note played by melody on the previous step
            int lastBeat      = -1;
            while (playing) {
                try {
                    int beat = globalBeat;
                    if (isMotif) {
                        int step = motifStep;
                        if (step != lastMotifStep) {
                            if (prevNote != -1) {   // no echo on the very first step
                                System.out.printf("[HARMONY | Beat %-2d] MOTIF echo %d   (step %d)%n", beat, prevNote, step);
                                channels[2].noteOn(prevNote, 65);
                                Thread.sleep(380);
                                channels[2].noteOff(prevNote, 0);
                            }
                            prevNote      = MOTIF_NOTES[step];   // remember for next step
                            lastMotifStep = step;
                        }
                        Thread.sleep(40);
                    } else {
                        lastMotifStep = -1;
                        prevNote      = -1;
                        if (beat != lastBeat && beat % 4 == 1) {
                            lastBeat = beat;
                            int note = PENTATONIC[rand.nextInt(PENTATONIC.length)];
                            System.out.printf("[HARMONY | Beat %-2d] Note %d (held)%n", beat, note);
                            channels[2].noteOn(note, 55);
                            Thread.sleep(800);
                            channels[2].noteOff(note, 0);
                        }
                        Thread.sleep(40);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
}
