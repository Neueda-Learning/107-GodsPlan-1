/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        canvas: '#F7F9FC',
        primary: { DEFAULT: '#6FAEDB', hover: '#5A9DCC', light: '#EEF6FC' },
        ink: { DEFAULT: '#334155', muted: '#64748B' },
        line: '#DDE7F1',
        success: '#67B68A',
        warning: '#D8B24C',
        danger: '#D77A7A',
      },
      boxShadow: { card: '0 4px 18px rgba(51, 65, 85, 0.06)' },
      borderRadius: { card: '12px' },
    },
  },
  plugins: [],
}

