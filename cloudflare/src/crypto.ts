import { argon2id } from '@noble/hashes/argon2.js';
import { bytesToHex, hexToBytes } from '@noble/hashes/utils.js';

const encoder = new TextEncoder();

export function randomToken(bytes = 32): string {
	const arr = new Uint8Array(bytes);
	crypto.getRandomValues(arr);
	return [...arr].map((b) => b.toString(16).padStart(2, '0')).join('');
}

export async function sha256Hex(value: string): Promise<string> {
	const digest = await crypto.subtle.digest('SHA-256', encoder.encode(value));
	return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

const ARGON_M = 4096;
const ARGON_T = 3;
const ARGON_P = 1;
const ARGON_DKLEN = 32;

export async function hashPassword(password: string): Promise<string> {
	const salt = crypto.getRandomValues(new Uint8Array(16));
	const hash = argon2id(encoder.encode(password), salt, {
		t: ARGON_T,
		m: ARGON_M,
		p: ARGON_P,
		dkLen: ARGON_DKLEN,
	});
	return `argon2id$m=${ARGON_M},t=${ARGON_T},p=${ARGON_P}$${bytesToHex(salt)}$${bytesToHex(hash)}`;
}

function constantTimeEqualHex(a: string, b: string): boolean {
	if (a.length !== b.length) return false;
	let diff = 0;
	for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
	return diff === 0;
}

function verifyArgon2id(password: string, stored: string): boolean {
	const parts = stored.split('$');
	if (parts.length !== 4 || parts[0] !== 'argon2id') return false;
	const params = parts[1];
	const saltHex = parts[2];
	const expectedHex = parts[3];
	const m = parseInt(params.match(/m=(\d+)/)?.[1] || '', 10);
	const t = parseInt(params.match(/t=(\d+)/)?.[1] || '', 10);
	const p = parseInt(params.match(/p=(\d+)/)?.[1] || '', 10);
	if (!m || !t || !p || !saltHex || !expectedHex) return false;
	if (m > 65536 || t > 10 || p > 4) return false;
	try {
		const salt = hexToBytes(saltHex);
		const hash = argon2id(encoder.encode(password), salt, {
			t,
			m,
			p,
			dkLen: expectedHex.length / 2,
		});
		return constantTimeEqualHex(bytesToHex(hash), expectedHex);
	} catch {
		return false;
	}
}

export async function verifyPassword(password: string, stored: string): Promise<boolean> {
	if (!stored) return false;
	if (stored.startsWith('argon2id$')) return verifyArgon2id(password, stored);
	return false;
}

export const PASSWORD_MIN = 8;
export const PASSWORD_MAX = 128;
export const DUMMY_HASH =
	'argon2id$m=4096,t=3,p=1$00000000000000000000000000000000$0000000000000000000000000000000000000000000000000000000000000000';

export function isValidPassword(password: string): boolean {
	return password.length >= PASSWORD_MIN && password.length <= PASSWORD_MAX;
}

export function isValidEmail(email: string): boolean {
	if (email.length > 254) return false;
	return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

export function normalizeEmail(email: string): string {
	return email.trim().toLowerCase();
}
