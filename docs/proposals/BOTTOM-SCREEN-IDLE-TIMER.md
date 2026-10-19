# Idea: bottom-screen idle timer

Status: **backlog idea only**. No implementation, release commitment or change
to the JTF-QUALITY-2026-10-17 programme is proposed here.

While the Thor is in BOTH mode, optionally turn off the bottom screen after
X minutes **without interaction on that screen**. This is an inactivity timer
for the bottom screen, not a limit on time spent in BOTH. The timeout has not
been chosen.

Before any implementation, decide:

- How to observe interaction on the bottom screen reliably, and how to handle
  content that is being viewed without touch input.
- The timeout, user control, cancellation and normal restoration behaviour.
- How manual mode changes, sleep/wake, Guard and external displays affect the
  timer, without overriding explicit user actions or existing safety gates.

A later proposal needs characterization of the actual platform signals,
negative tests for stale timers and uncertain state, and separately authorized
physical validation before release. This note adds no timer, background
monitoring, permissions or display commands.
