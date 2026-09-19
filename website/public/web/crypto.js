/** BLB1 AES-GCM backup, matching BackupCrypto.kt */

export const MAGIC = 'BLB1';
export const ITERATIONS = 120_000;
const SALT_LEN = 16;
const IV_LEN = 12;
const TAG_BITS = 128;

const text = new TextEncoder();
const utf8 = new TextDecoder();

export function magicBytes() {
	return text.encode(MAGIC);
}

export async function encrypt(plain, passphrase, salt, iv) {
	if (!passphrase) throw new Error('Encryption key is required');
	const saltBytes = salt ?? crypto.getRandomValues(new Uint8Array(SALT_LEN));
	const ivBytes = iv ?? crypto.getRandomValues(new Uint8Array(IV_LEN));
	const key = await derive(passphrase, saltBytes);
	const ct = new Uint8Array(
		await crypto.subtle.encrypt({ name: 'AES-GCM', iv: ivBytes, tagLength: TAG_BITS }, key, plain),
	);
	const magic = magicBytes();
	const out = new Uint8Array(magic.length + saltBytes.length + ivBytes.length + ct.length);
	out.set(magic, 0);
	out.set(saltBytes, magic.length);
	out.set(ivBytes, magic.length + saltBytes.length);
	out.set(ct, magic.length + saltBytes.length + ivBytes.length);
	return out;
}

export async function decrypt(blob, passphrase) {
	if (!passphrase) throw new Error('Encryption key is required');
	const bytes = blob instanceof Uint8Array ? blob : new Uint8Array(blob);
	const magic = magicBytes();
	if (bytes.length < magic.length + SALT_LEN + IV_LEN + 16 || !startsWith(bytes, magic)) {
		throw new Error('Not an encrypted Builder Launcher backup');
	}
	let i = magic.length;
	const salt = bytes.subarray(i, i + SALT_LEN);
	i += SALT_LEN;
	const iv = bytes.subarray(i, i + IV_LEN);
	i += IV_LEN;
	const ct = bytes.subarray(i);
	const key = await derive(passphrase, salt);
	try {
		return new Uint8Array(
			await crypto.subtle.decrypt({ name: 'AES-GCM', iv, tagLength: TAG_BITS }, key, ct),
		);
	} catch {
		throw new Error('Wrong encryption key');
	}
}

export function decodeUtf8(bytes) {
	return utf8.decode(bytes);
}

export function encodeUtf8(value) {
	return text.encode(value);
}

async function derive(passphrase, salt) {
	const material = await crypto.subtle.importKey('raw', text.encode(passphrase), 'PBKDF2', false, [
		'deriveKey',
	]);
	return crypto.subtle.deriveKey(
		{ name: 'PBKDF2', salt, iterations: ITERATIONS, hash: 'SHA-256' },
		material,
		{ name: 'AES-GCM', length: 256 },
		false,
		['encrypt', 'decrypt'],
	);
}

function startsWith(bytes, prefix) {
	for (let i = 0; i < prefix.length; i++) {
		if (bytes[i] !== prefix[i]) return false;
	}
	return true;
}
