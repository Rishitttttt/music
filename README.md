# 🎵 ThreadMusic — Music Made by Threads

## 📺 Demo
▶️ [Watch it live — threads making music in real time](https://www.loom.com/share/4c1be64b7dbf409facfdcc801f1e4fa6)

---

## 💡 The Idea

Threads in Java have no guaranteed execution order — the OS decides who runs when.

**ThreadMusic uses that randomness as the music.**

Each thread is an instrument. They all run in parallel with no fixed order. When a thread plays a note it writes to a shared memory variable — a "whiteboard" — that other threads can read. This keeps them loosely in sync. But because threads run randomly, the combination of notes is always different.

```
Thread runs → plays a note → writes to shared memory
Other threads read shared memory → sync up → play their notes
OS schedules threads randomly → different order every run
= a brand new piece of music every single time
```

Same code. Different music. Every run.

---

## 🎼 How It Works

| Thread | Instrument | Role |
|---|---|---|
| BeatKeeperThread | — | Increments global beat every 500ms — the heartbeat |
| DrumThread | GM Drums | Kick on beat 1 & 3, snare on 2 & 4, random hihat |
| BassThread | Acoustic Bass | Holds the foundation — root and fifth notes |
| MelodyThread | Grand Piano | Fast random improvisation within C major pentatonic |
| HarmonyThread | Strings | Slow chords that follow the melody |

All 5 threads run **simultaneously** — nobody waits for anyone.

---

## 🔗 Thread Concepts Used

- **Parallel execution** — all 5 threads run at the same time
- **Shared memory** — `volatile int globalBeat` is the whiteboard all threads read
- **No guaranteed order** — thread scheduling randomness creates the music
- **sleep()** — each thread controls its own tempo independently and keeps its lock
- **Structured randomness** — notes are random but only from C major pentatonic scale so every combination sounds musical

---

## 🚀 Run It Yourself

```bash
javac ThreadMusic.java
java ThreadMusic
```

Make sure your volume is up. Music plays for 30 seconds then stops cleanly.

You will also see this in your console — every line is a different thread playing in real time:

```
[DRUMS   | Beat 1] Playing kick
[MELODY  | Beat 1] Note: 67
[BASS    | Beat 2] Note: 48
[HARMONY | Beat 3] Note: 64
⚡ MOTIF PHASE — all threads aligning!
```

---

## 📋 Requirements

- Java JDK 8 or above
- No external libraries — only `javax.sound.midi` which is built into Java
- Run on your local machine (not an online compiler — needs speaker access)

---

## 🧠 Why This Is Interesting

Most thread projects just print "Hello from Thread 1".

This project makes the **unpredictability of threads** the feature — not the bug. The same property that makes threads hard to control (random scheduling) is exactly what generates new music every run.

It is the same concept as jazz — fixed rules, random improvisation within those rules, different every time.
