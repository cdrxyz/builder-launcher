// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

// GitHub Pages serves this repo at /builder-launcher/.
export default defineConfig({
	site: 'https://cdrxyz.github.io',
	base: '/builder-launcher/',
	integrations: [
		starlight({
			title: 'Builder Launcher',
			description:
				'A simple Android home for builders who want to be deliberate with the phone.',
			logo: {
				src: './src/assets/logo.svg',
				alt: 'Builder Launcher',
			},
			favicon: 'favicon.svg',
			editLink: {
				baseUrl: 'https://github.com/cdrxyz/builder-launcher/edit/master/website/',
			},
			social: [
				{
					icon: 'github',
					label: 'GitHub',
					href: 'https://github.com/cdrxyz/builder-launcher',
				},
			],
			customCss: ['./src/styles/custom.css'],
			lastUpdated: true,
			sidebar: [
				{
					label: 'Start',
					items: [
						{ label: 'Install', slug: 'start/install' },
						{ label: 'First run', slug: 'start/first-run' },
					],
				},
				{
					label: 'Use',
					items: [
						{ label: 'Home', slug: 'use/home' },
						{ label: 'Command bar', slug: 'use/command-bar' },
						{ label: 'Commands', slug: 'use/commands' },
						{ label: 'Apps and pins', slug: 'use/apps' },
						{ label: 'Messages', slug: 'use/messages' },
						{ label: 'Calls', slug: 'use/calls' },
						{ label: 'Calendar', slug: 'use/calendar' },
						{ label: 'Todos', slug: 'use/todos' },
						{ label: 'Clock', slug: 'use/clock' },
						{ label: 'Usage', slug: 'use/usage' },
						{ label: 'Weather', slug: 'use/weather' },
						{ label: 'Notes', slug: 'use/notes' },
						{ label: 'Stocks', slug: 'use/stocks' },
						{ label: 'Podcasts', slug: 'use/podcasts' },
						{ label: 'Hub', slug: 'use/hub' },
						{ label: 'Ask AI', slug: 'use/ai' },
					],
				},
				{
					label: 'Configure',
					items: [
						{ label: 'Settings', slug: 'configure/settings' },
						{ label: 'AI providers', slug: 'configure/ai-providers' },
						{ label: 'Weather', slug: 'configure/weather' },
						{ label: 'Keyboard', slug: 'configure/keyboard' },
						{ label: 'Default home app', slug: 'configure/default-home' },
						{ label: 'Privacy', slug: 'configure/privacy' },
					],
				},
				{
					label: 'Develop',
					items: [
						{ label: 'Build from source', slug: 'develop/build' },
						{ label: 'Screenshots', slug: 'develop/screenshots' },
						{ label: 'Keep docs in sync', slug: 'develop/docs' },
					],
				},
			],
		}),
	],
});
