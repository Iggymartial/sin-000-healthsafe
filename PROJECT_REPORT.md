# HealthSafe - Project Report

My own account of this project: what was asked, how I approached it,
what I actually built, and how the whole system works now that all
four stages are done. Written so I can read it back before a demo or
a review and actually explain every part of it, not just remember that
it works.

## 1. What was required

This project comes from the Systems Integration elective. The brief
was to take a starter repo of five mostly-empty Java microservices and
build them out in four stages, each one demonstrating a different way
services can talk to each other:

1. **Ingestion** - clean a deliberately messy CSV file and serve it
   over REST.
2. **REST services** - three services calling each other synchronously
   over plain HTTP.
3. **MQ decoupling** - replace part of that synchronous chain with an
   asynchronous broadcast, using an ActiveMQ topic.
4. **Alerting** - a second, different messaging pattern: a queue, used
   specifically where a message has to be guaranteed delivered, not
   just broadcast.

The brief was explicit that stages 1-2 are the required core, and
stages 3-4 are where I'm meant to show judgement about *when* a queue
or topic actually earns its place over a direct call - not just use
one because it's available. It also said reasonable field names and
status codes are fine, since the endpoint shapes given were
illustrative rather than a spec to match exactly. That mattered a lot,
because several things weren't specified at all and I had to decide
them myself - I'll get into which ones and why below.

## 2. How I approached it

I built this one stage at a time, in the order the brief laid out, and
didn't move to the next stage until the current one actually ran
against real, live services - not just compiled. For each stage I:

- read the actual README and rubric text for that stage first, rather
  than assuming I remembered it correctly
- wrote the code
- tested whatever I could verify without a full Java build (I
  reimplemented tricky logic in a second language where possible, just
  to catch reasoning mistakes early)
- ran the real thing, on real services, and fixed whatever broke
- wrote down *why* I made the decisions I made, while I still
  remembered my own reasoning, not after the fact

That last point turned into DECISIONS.md, which I kept adding to
throughout - it's the single most useful thing I did for being able to
actually defend this project afterwards, because by stage 4 I could
no longer remember every small reason I'd made a choice in stage 1
without it.

## 3. Stage 1 - Ingestion

**What it does:** reads a real, messy `wards-outdated.csv` file and
turns it into clean, trustworthy JSON, served at `GET /wards`.

**The mess I had to deal with:** inconsistent casing (`w-05` vs
`W-05`), padding and double spaces, a genuine duplicate row for the
same ward with conflicting data, missing values written as `N/A`,
`TBD`, or just blank, non-numeric bed counts like `"five"` and
`"full"`, negative bed counts, and one row with `2023` beds - clearly
a stray year, not a real number.

**How I handled it:**
- Every field gets trimmed and had its internal double-spaces
  collapsed before anything else happens to it.
- Ward IDs get uppercased, which is also what makes duplicate
  detection possible - `W-05` and `w-05` need to become the *same* key
  before I can even notice they're duplicates.
- For the one genuine duplicate in the data, I decided: whichever row
  has a *usable* number of beds wins. If neither does, the first one
  in the file wins. Either way, the surviving record's notes say
  exactly what got discarded and why - nothing just silently
  disappears.
- Bed counts get checked three separate ways: can it even be parsed as
  a number, is it negative, and is it a realistic size for a hospital
  ward (nothing over 200). Each failure gets its own specific note, not
  one generic "invalid" flag.

**A bug I actually caught before it became a bug:** I couldn't compile
or run the Java code in the environment I was working in, so before
writing the real version, I reimplemented the same cleaning logic in a
language I *could* run and tested it against the real CSV. That caught
a genuine mistake immediately: naive title-casing turned `"ICU"` into
`"Icu"`. I fixed the acronym handling before it ever reached the real
Java file.

## 4. Stage 2 - REST services

**What it does:** three separate services now talk to each other over
plain HTTP - `ward-service`, `alert-level-service`, and
`staffing-service`.

- `ward-service` calls `ingestion-service` fresh on every request. I
  decided not to cache anything here - if ingestion-service restarts
  with corrected data, I didn't want ward-service quietly serving
  stale results with no way to notice.
- `alert-level-service` tracks a single Emergency Status number, 0
  through 8. The brief only asked for a way to *read* it, but nothing
  can track a status that can never change - so I added a way to
  *set* it too, with range validation.
- `staffing-service` calls both of the above and computes a staffing
  number. Since nothing in the brief said how many doctors should be
  on call, I invented a formula myself: a baseline of one doctor, plus
  more as the alert level rises, plus extra for high-acuity wards like
  ICU or Cardiology. Before trusting it, I checked it across every
  alert level from 0 to 8 to make sure it never went down and never
  hit zero.

**The part I'm most pleased with:** how `staffing-service` handles
`ward-service` failing or not finding a ward. Instead of a `null` or a
generic exception, I modelled the three possible outcomes - found, not
found, or unreachable - as their own distinct type, so there's no way
for me to have accidentally forgotten to handle one of them somewhere.

**Two real bugs I hit while testing this, live:**

1. Posting JSON from PowerShell using `curl.exe` kept failing with a
   broken-JSON error - it turned out PowerShell itself was mangling
   the quotes before the request even left my machine. Not a code bug
   at all; switching to PowerShell's own request tool fixed it
   instantly.
2. The first time I actually hit the staffing endpoint, it crashed
   with a real error: I'd used a Java timestamp type that the JSON
   library couldn't serialise without extra setup. Rather than add
   that setup, I just switched the field to plain text instead - same
   information, no extra configuration needed.

Both times, the fix came from reading the actual error in the
service's own terminal, not the generic message the client saw.

## 5. Stage 3 - MQ decoupling (staffing-events-topic)

**What it does:** every time `staffing-service` computes a schedule,
it now also broadcasts that update to a topic. `ward-service`
subscribes to that topic and keeps the most recent update per ward in
memory, exposed at a new endpoint - so it can answer "what's the
latest staffing status for this ward" without ever having to ask
staffing-service directly.

**Something I noticed while reading the brief closely:** its own task
list said to replace a synchronous call "from ward-service to
staffing-service" - but no such call exists anywhere in this project.
Every other part of the brief (the integration table, the individual
service READMEs) consistently says the opposite direction:
staffing-service publishes, ward-service subscribes. I went with the
direction four separate sources agreed on, and wrote down the
discrepancy rather than just quietly picking one.

**A design choice I made deliberately:** if the message broker isn't
reachable when either service starts up, neither one crashes. Both
just log a clear warning and keep their REST APIs working normally -
because those were already fully built and tested in the previous
stage, and a missing *optional* messaging layer shouldn't take down
something that has nothing to do with it.

**The real incident here wasn't a code bug at all.** The first time I
tested this, no event ever reached ward-service, even though the
schedule endpoint kept responding successfully. It turned out the
message broker was never actually running - I'd started it from the
wrong folder, and the command had silently done nothing. Because of
the graceful-degradation design above, neither service complained
loudly about it; they just quietly had nothing to talk about. Once I
started the broker from the right place, the whole flow worked
immediately.

## 6. Stage 4 - Alerting (equipment-failure-queue)

**What it does:** `ward-service` can now report an equipment failure
on a specific ward, which gets published to a queue -
`equipment-alert-service` picks it up and keeps a running list of
every alert it's ever received.

**Why a queue instead of a topic, mechanically, not just by name:** a
topic is a broadcast - if nobody's listening, the message is just
gone. A queue is meant to guarantee delivery to whoever's meant to
receive it, even if that service was briefly down. I made that
guarantee real in the code, not just implied by using a different
class name: the consumer only tells the broker "I'm done with this
message" *after* it's successfully stored the alert. If something had
gone wrong partway through, the message would stay on the broker,
unacknowledged, and get redelivered later.

**Another decision worth explaining:** unlike the topic in Stage 3,
where I only kept the *latest* update per ward, here I keep *every*
alert ever received. A live status update genuinely only needs the
most recent value. Two separate equipment failures are two separate
facts - overwriting the first with the second would mean a real alert
just quietly vanished.

**Since nothing in the project could actually trigger an equipment
failure**, I added a way to report one - the same reasoning as adding
the ability to change the alert level back in Stage 2: without some
way to trigger it, there'd be no way to actually prove the queue
worked at all.

**How it tested, for real:** the alerts list started genuinely empty,
reporting a failure on a real ward correctly published it and it
showed up on the other service moments later, and reporting one
against a ward that doesn't exist correctly failed before anything was
ever published. Both pieces of this stage's JMS code worked correctly
on the very first real attempt.

## 7. How the whole system works now, end to end

```
                    ingestion-service (7030)
                    cleans wards-outdated.csv
                             |
                        GET /wards
                             |
                    ward-service (7031)
              -------------------------------
              |                             |
        GET /wards/{id}              subscribes to
              |                    staffing-events-topic
              v                             ^
      staffing-service (7033)               |
              |                    publishes on every
        GET /alert-level           schedule computation
              |
              v
      alert-level-service (7032)


      ward-service also PUBLISHES to
      equipment-failure-queue when an
      equipment failure is reported
              |
              v
      equipment-alert-service (7034)
      keeps every alert ever received
```

Five independent services, each its own Maven project with no shared
code between them - anything they need in common (like the broker
connection details) is duplicated deliberately into each one's own
source tree, since there's no shared parent project to put it in
instead.

Three genuinely different integration patterns are demonstrated here,
each for a real reason rather than just because it was available:
plain synchronous REST calls where an immediate answer is needed and
the caller should know right away if something's wrong; an
asynchronous broadcast (topic) for status updates where losing one
update occasionally doesn't matter much, because a fresher one is
coming soon anyway; and a guaranteed-delivery queue for the one thing
in this system where losing a message actually matters - a genuine
equipment failure alert.

## 8. What I'd add if I kept going

Being honest about the actual limits of what's here, in case anyone
asks:

- No automated tests exist yet - everything was verified by actually
  running the real services and checking real responses, which is
  solid evidence it works, but it isn't the same as a test suite that
  runs itself.
- If the message broker goes down mid-run, both consumers keep
  whatever they'd already received but won't backfill anything they
  missed while disconnected - a durable subscription or persisted
  local store would close that gap, but felt like more complexity
  than this exercise needed.
- Every service currently assumes the others are running on
  `localhost` at fixed ports - fine for this exercise, but a real
  deployment would need that to be configurable, the same way I made
  MySQL connection details configurable in my other data engineering
  project.

## 9. What I actually learned from this

The single biggest thing: a generic error on the client side (a 500, a
"Server Error") tells you almost nothing. Every real bug I hit in this
project - the acronym bug, the timestamp serialisation bug, even the
broker-not-running incident - only became obvious once I went and read
the actual error sitting in that specific service's own terminal. That
became my default move any time something didn't work: stop guessing,
go find the real message, and only then decide what to fix.
