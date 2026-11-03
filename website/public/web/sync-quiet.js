export const TEXT_QUIET_MS = 8_000;
export const ORDER_QUIET_MS = 5_000;
export const WRITE_QUIET_MS = 2_000;
export const PLAY_SAVE_MS = 10_000;

export function quietMs(kind) {
	if (kind === 'text') return TEXT_QUIET_MS;
	if (kind === 'order') return ORDER_QUIET_MS;
	return WRITE_QUIET_MS;
}

/** Earliest moment an automatic push may start. A later edit extends the wait. */
export function syncReadyAt(now, holds) {
	let until = 0;
	for (const hold of holds || []) {
		until = Math.max(until, (hold.at || 0) + quietMs(hold.kind));
	}
	return Math.max(now, until);
}

/**
 * A push must not replace edits made while the request was in flight.
 * `join` is last-write-wins. `keepFresh` restores rows added during the request.
 */
export function settlePushedDoc(snapshot, live, remote, genChanged, join, keepFresh, startedAt) {
	const base = genChanged ? live : snapshot;
	return {
		doc: keepFresh(join(base, remote), live, startedAt),
		dirty: Boolean(genChanged),
	};
}
