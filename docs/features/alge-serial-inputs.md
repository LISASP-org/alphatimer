# ALGE serial inputs (TM-SWIM, Timy3)

AlphaTiming reads only ARES/Quantum. ALGE timing devices have four outputs with a documented format; the manuals are
kept locally in `docs/alge/` (ignored by Git). All four are RS-232, 8 data bits, no parity, 1 stop bit, ASCII, lines
ending in CR, and can be read passively like ARES (serial port or a serial-to-TCP adapter).

| Device | Output | Speed | Source | Content |
| --- | --- | --- | --- | --- |
| TM-SWIM | Video-Out | 19200 by default (4800–38400) | Swimming manual p. 32 | Running time with event and heat; split, finish and deleted times per lane with touch counter; reaction, event and swimmer lines added by SwimTime |
| TM-SWIM | Display board | 2400 by default (up to 38400) | Swimming manual p. 32 | Running and stopped time per display address only |
| Timy3 | RS-232 | 9600 by default (2400–38400), RTS/CTS optional | Timy3 manual ch. 12.1 | One line per impulse: status flag, start number, channel, time, group/lap, rank |
| Timy3 | Display board | 2400 by default (up to 38400) | Timy3 manual ch. 12.3 | Start number, channel letter, time, rank |

Formats:

- **TM-SWIM Video-Out:** `R eee hhh HH:MM:SS.zhtc` running time with event and heat; `I LL TTT RR …` split, `F LL TTT RR …`
  finish, `C LL TTT RR …` deleted time (lane, touch counter, rank; rank is not sent from firmware 14xx on);
  `M LL TTT …` reaction or relay take-off time; `ID EV/HT/Discipline/Class`, `B : Name/Nation/Club`; `START` from
  firmware 14xx on. A time line carries no event and heat; they come from the preceding `R`/`ID` line. No DNS, DSQ,
  backup times or official end.
- **Timy3 RS-232:** `yNNNN CCC HH:MM:SS.zhtq GGRRRR`; `y` blank = valid, `?` no start number, `m` from memory,
  `c`/`C` deleted, `d` deleted by disqualification, `i` entered by hand, `n` new start number; `CCC` channel `c0`
  start, `c1` finish, `c2`–`c8`, suffix `M` from the keypad. No lane and no heat: lanes come from a channel mapping,
  event and heat from a race number the operator types (e.g. `0102`).
- **Display boards:** too little for a heat model (no lane on TM-SWIM, start number instead of lane on Timy3).

- **Goal:** read the TM-SWIM Video-Out, and possibly the Timy3 RS-232, and refine them into the refined messages.
- **Where:** a protocol module per device and a refiner into `alphatimer-refinedmessages-api`; the line settings next
  to `SerialMode`.
- **Depends on:** [Reaction times](reaction-times.md) for the `M` lines. LENEX is the model for an input source beside
  ARES (recording, rebuilding a project with the right refiner).
- **Open questions:** captures of real devices (firmware version matters); whether the Video-Out port is free at a pool
  (it is often used for TV graphics); whether the Timy3 is needed at all.
